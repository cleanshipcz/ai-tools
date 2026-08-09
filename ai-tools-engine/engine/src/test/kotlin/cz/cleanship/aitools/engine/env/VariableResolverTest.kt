package cz.cleanship.aitools.engine.env

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class VariableResolverTest {

    private lateinit var environmentVariables: MutableMap<String, String>
    private lateinit var environment: EnvironmentSource

    @BeforeEach
    fun setUp() {
        // A faked environment keeps the tests from depending on - or mutating - the environment of the JVM they run in.
        environmentVariables = mutableMapOf()
        environment = EnvironmentSource { name -> environmentVariables[name] }
    }

    @Nested
    inner class Substitution {

        @Test
        fun `should expand a reference when a declared variable carries the name`() {
            // given
            // - the variable a config file declares under 'env_vars'
            val resolver = VariableResolver(mapOf("PROJECTS_FOLDER" to "/home/user/Projects"), environment)

            // when
            val substituted = resolver.substitute("\${PROJECTS_FOLDER}/custom-ai-tools")

            // then
            assertThat(substituted).isEqualTo("/home/user/Projects/custom-ai-tools")
        }

        @ParameterizedTest
        @CsvSource(
            "\${FIRST}, alpha",
            "\${FIRST}/\${SECOND}, alpha/beta",
            "\${FIRST}\${SECOND}, alphabeta",
            "/base/\${FIRST}/leaf, /base/alpha/leaf",
            "\${FIRST}-\${FIRST}, alpha-alpha",
        )
        fun `should expand every reference a value carries`(declared: String, expected: String) {
            // given
            val resolver = VariableResolver(mapOf("FIRST" to "alpha", "SECOND" to "beta"), environment)

            // when
            val substituted = resolver.substitute(declared)

            // then
            assertThat(substituted).isEqualTo(expected)
        }

        @ParameterizedTest
        @ValueSource(strings = ["/absolute/path", "relative/path", ".", "..", "a path with spaces"])
        fun `should return the value unchanged when it carries no reference`(declared: String) {
            // given
            // - a variable is declared, but the value never asks for it
            val resolver = VariableResolver(mapOf("FIRST" to "alpha"), environment)

            // when
            val substituted = resolver.substitute(declared)

            // then
            assertThat(substituted).isEqualTo(declared)
        }

        @Test
        fun `should expand a reference to an empty value`() {
            // given
            val resolver = VariableResolver(mapOf("EMPTY" to ""), environment)

            // when
            val substituted = resolver.substitute("\${EMPTY}projects")

            // then
            assertThat(substituted).isEqualTo("projects")
        }

        @Test
        fun `should expand a value carrying characters a replacement pattern would read as syntax`() {
            // given
            // - a dollar and a backslash mean something to a regex replacement, but nothing to a path
            val resolver = VariableResolver(mapOf("ODD" to "/opt/\$1\\share"), environment)

            // when
            val substituted = resolver.substitute("\${ODD}/agents")

            // then
            assertThat(substituted).isEqualTo("/opt/\$1\\share/agents")
        }

        @ParameterizedTest
        @ValueSource(strings = ["\${1DIGIT}/path", "\${WITH-DASH}/path", "\${}/path", "\$NO_BRACES/path", "\${UNCLOSED/path"])
        fun `should leave a dollar-brace group as written when it is not a valid reference`(declared: String) {
            // given
            // - nothing declares any of these names, yet none of them is a reference this resolver recognises
            val resolver = VariableResolver(emptyMap(), environment)

            // when
            val substituted = resolver.substitute(declared)

            // then
            assertThat(substituted).isEqualTo(declared)
        }
    }

    @Nested
    inner class Construction {

        @Test
        fun `should keep resolving what it was built with when the map it was given changes afterwards`() {
            // given
            val declared = mutableMapOf("ROOT" to "/first")
            val resolver = VariableResolver(declared, environment)

            // when
            declared["ROOT"] = "/second"

            // then
            assertThat(resolver.substitute("\${ROOT}/agents")).isEqualTo("/first/agents")
        }

        @Test
        fun `should not equal a resolver built from the same map after that map changed`() {
            // given
            // - one caller's map, built into a resolver and only then changed
            val declared = mutableMapOf("ROOT" to "/first")
            val before = VariableResolver(declared, environment)
            val hashCodeWhenBuilt = before.hashCode()

            // when
            declared["ROOT"] = "/second"
            val after = VariableResolver(declared, environment)

            // then
            // - the two resolve differently, so comparing them equal would be a lie
            assertThat(before.substitute("\${ROOT}")).isEqualTo("/first")
            assertThat(after.substitute("\${ROOT}")).isEqualTo("/second")
            assertThat(before).isNotEqualTo(after)
            // - and a resolver keeps the hash it was built with, so a map it no longer shares cannot move it
            assertThat(before.hashCode()).isEqualTo(hashCodeWhenBuilt)
        }

        @Test
        fun `should equal a resolver declaring the same variables and reading the same environment`() {
            // given
            // - the configuration of a run carries a resolver, so two configurations declaring the same must compare equal
            val resolver = VariableResolver(mapOf("ROOT" to "/root"), environment)
            val same = VariableResolver(mapOf("ROOT" to "/root"), environment)
            val different = VariableResolver(mapOf("ROOT" to "/elsewhere"), environment)

            // then
            assertThat(resolver).isEqualTo(same).hasSameHashCodeAs(same)
            assertThat(resolver).isNotEqualTo(different)
        }
    }

    @Nested
    inner class Precedence {

        @Test
        fun `should read the variable from the environment when no declared variable carries the name`() {
            // given
            environmentVariables["PROJECTS_FOLDER"] = "/from/environment"
            val resolver = VariableResolver(emptyMap(), environment)

            // when
            val substituted = resolver.substitute("\${PROJECTS_FOLDER}/agents")

            // then
            assertThat(substituted).isEqualTo("/from/environment/agents")
        }

        @Test
        fun `should prefer the declared variable when the environment carries the same name`() {
            // given
            // - the config files of the run and the environment disagree about the same name
            environmentVariables["PROJECTS_FOLDER"] = "/from/environment"
            val resolver = VariableResolver(mapOf("PROJECTS_FOLDER" to "/from/config"), environment)

            // when
            val substituted = resolver.substitute("\${PROJECTS_FOLDER}/agents")

            // then
            assertThat(substituted).isEqualTo("/from/config/agents")
        }

        @Test
        fun `should read the environment of the process when no source is given`() {
            // when
            val fromDefaultSource = EnvironmentSource.PROCESS.read("PATH")

            // then
            assertThat(fromDefaultSource).isEqualTo(System.getenv("PATH"))
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `should fail naming the variable when neither the config nor the environment carries it`() {
            // given
            val resolver = VariableResolver(mapOf("OTHER" to "/other"), environment)

            // when
            val error = runCatching { resolver.substitute("\${PROJECTS_FOLDER}/custom-ai-tools") }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(UnresolvedVariableException::class.java)
                .hasMessageContaining("PROJECTS_FOLDER")
            assertThat((error as UnresolvedVariableException).variable).isEqualTo("PROJECTS_FOLDER")
        }

        @Test
        fun `should name the origin of the value in the failure when the caller knows it`() {
            // given
            val resolver = VariableResolver(emptyMap(), environment)

            // when
            val error = runCatching {
                resolver.substitute("\${PROJECTS_FOLDER}/custom", origin = "project 'custom-ai-tools' deploy.directory")
            }.exceptionOrNull()

            // then
            assertThat(error)
                .hasMessageContaining("PROJECTS_FOLDER")
                .hasMessageContaining("project 'custom-ai-tools' deploy.directory")
        }

        @Test
        fun `should fail naming the reference left over when a value carries one of its own`() {
            // given
            // - nested expansion is out of scope, and expanding one level would leave a literal reference in a path
            val resolver = VariableResolver(
                mapOf("OUTER" to "\${INNER}/projects", "INNER" to "/home/user"),
                environment,
            )

            // when
            val error = runCatching { resolver.substitute("\${OUTER}/custom") }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(UnexpandedReferenceException::class.java)
                // - the value the author wrote is quoted back, so the variable they need to fix is in the message
                .hasMessageContaining("OUTER")
            assertThat((error as UnexpandedReferenceException).variable).isEqualTo("INNER")
        }

        @Test
        fun `should fail rather than loop when a variable refers to itself`() {
            // given
            val resolver = VariableResolver(mapOf("SELF" to "\${SELF}/projects"), environment)

            // when
            val error = runCatching { resolver.substitute("\${SELF}") }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(UnexpandedReferenceException::class.java)
        }

        @Test
        fun `should fail when an expansion assembles a reference with the text around it`() {
            // given
            // - a value expanding to a bare dollar turns the '{HOME}' written after it into a reference no pass will
            //   expand, which is the one way a single pass could still hand a literal '${...}' to a deploy
            environmentVariables["HOME"] = "/home/user"
            val resolver = VariableResolver(mapOf("DOLLAR" to "$"), environment)

            // when
            val error = runCatching { resolver.substitute("\${DOLLAR}{HOME}/projects") }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(UnexpandedReferenceException::class.java)
                .hasMessageContaining("HOME")
            assertThat((error as UnexpandedReferenceException).variable).isEqualTo("HOME")
        }
    }
}
