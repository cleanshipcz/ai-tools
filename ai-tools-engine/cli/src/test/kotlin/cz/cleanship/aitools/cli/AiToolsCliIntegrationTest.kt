package cz.cleanship.aitools.cli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.parse
import cz.cleanship.aitools.engine.ExportFailedException
import cz.cleanship.aitools.engine.ExportFailure
import cz.cleanship.aitools.engine.models.DuplicateManifestId
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
import cz.cleanship.aitools.engine.services.ManifestLoadingException
import cz.cleanship.aitools.engine.tools.RulesetResolvingException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AiToolsCliIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should run tools application when config exists`() {
        // given
        File(tempDir, "config.yml").writeText("{}")
        val cli = AiToolsCli()

        // when
        val error = runCatching {
            cli.main(arrayOf("--working-dir", tempDir.absolutePath))
        }.exceptionOrNull()

        // then
        assertThat(error).isNull()
    }

    @Test
    fun `should resolve relative manifest locations and a relative deploy directory against the working dir`() {
        // given
        // - a config declaring its manifest locations relative to --working-dir, as the repository's own does
        File(tempDir, "config.yml").writeText(
            """
            locations:
              projects:
                - "projects"
            tools:
              - claude
            """.trimIndent(),
        )
        // - a project deploying to a relative directory, which must land on the same base the locations use
        val projectFile = File(tempDir, "projects/test-project/project.yml")
        projectFile.parentFile.mkdirs()
        projectFile.writeText(
            """
            id: test-project
            description: A project
            metadata:
              version: 1.0.0
            context:
              documentation:
                readme: README.md
            deploy:
              directory: "generated"
            """.trimIndent(),
        )
        val cli = AiToolsCli()

        // when
        cli.parse(arrayOf("--working-dir", tempDir.absolutePath))

        // then
        assertThat(File(tempDir, "generated/CLAUDE.md")).exists()
        // - the working directory of this JVM is a different directory entirely and was left untouched
        assertThat(File("generated")).doesNotExist()
    }

    @Test
    fun `should deploy to the expanded directory when the deploy directory references a declared variable`() {
        // given
        // - the config declares the variable, the way a user keeps a machine-specific base out of the manifests
        File(tempDir, "config.yml").writeText(
            """
            env_vars:
              PROJECTS_FOLDER: "${tempDir.absolutePath}/deployments"
            locations:
              projects:
                - "projects"
            tools:
              - claude
            """.trimIndent(),
        )
        val projectFile = File(tempDir, "projects/test-project/project.yml")
        projectFile.parentFile.mkdirs()
        projectFile.writeText(
            """
            id: test-project
            description: A project
            metadata:
              version: 1.0.0
            context:
              documentation:
                readme: README.md
            deploy:
              directory: "${variableReference("PROJECTS_FOLDER")}/custom-ai-tools"
            """.trimIndent(),
        )
        val cli = AiToolsCli()

        // when
        cli.parse(arrayOf("--working-dir", tempDir.absolutePath))

        // then
        assertThat(File(tempDir, "deployments/custom-ai-tools/CLAUDE.md")).exists()
        // - the reference was expanded rather than taken for a directory name
        assertThat(File(tempDir, "deployments").list()).containsExactly("custom-ai-tools")
    }

    @Test
    fun `should fail with the variable name when a deploy directory references an undeclared variable`() {
        // given
        File(tempDir, "config.yml").writeText(
            """
            locations:
              projects:
                - "projects"
            tools:
              - claude
            """.trimIndent(),
        )
        val projectFile = File(tempDir, "projects/test-project/project.yml")
        projectFile.parentFile.mkdirs()
        projectFile.writeText(
            """
            id: test-project
            description: A project
            metadata:
              version: 1.0.0
            context:
              documentation:
                readme: README.md
            deploy:
              directory: "${variableReference("AI_TOOLS_UNDECLARED_TEST_FOLDER")}/custom-ai-tools"
            """.trimIndent(),
        )
        val cli = AiToolsCli()

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        // - the run reports on stderr and exits non-zero rather than failing with a stack trace
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("AI_TOOLS_UNDECLARED_TEST_FOLDER")
            .hasMessageContaining("test-project")
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should fail with the variable name when a declared path references an undeclared variable`() {
        // given
        // - a name no config file and no environment of a test machine declares
        File(tempDir, "config.yml").writeText(
            """
            locations:
              projects:
                - "${variableReference("AI_TOOLS_UNDECLARED_TEST_FOLDER")}/projects"
            """.trimIndent(),
        )
        val cli = AiToolsCli()

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("AI_TOOLS_UNDECLARED_TEST_FOLDER")
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should fail with the resolver message when an export fails`() {
        // given
        // - a CliktError makes the command report on stderr and exit with a non-zero status code
        val failure = ExportFailure(
            projectId = "test-project",
            toolType = ToolType.CLAUDE,
            manifest = "agent 'broken-agent'",
            cause = RulesetResolvingException(
                pattern = "missing-ruleset",
                requestedBy = "agent 'broken-agent'",
                availableIds = listOf("base"),
            ),
        )
        val cli = AiToolsCli(runner = { throw ExportFailedException(listOf(failure)) })

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("No rulesets match pattern 'missing-ruleset'")
            .hasMessageContaining("agent 'broken-agent'")
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should fail with the file path when a manifest declares a malformed version`() {
        // given
        val cli = AiToolsCli(
            runner = {
                throw ManifestLoadingException(
                    file = File("/manifests/broken.yml"),
                    cause = IllegalArgumentException("Invalid version format: 1.0"),
                )
            },
        )

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("broken.yml")
            .hasMessageContaining("Invalid version format: 1.0")
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should fail with both file paths when a project was skipped for a duplicate id`() {
        // given
        // - the other projects exported fine, so only the duplicate keeps the run from succeeding
        val firstFile = File("/projects/alpha/project.yml")
        val secondFile = File("/projects/beta/project.yml")
        val cli = AiToolsCli(
            runner = {
                throw ExportFailedException(
                    failures = emptyList(),
                    duplicates = listOf(
                        DuplicateManifestId(id = "duplicated-id", firstFile = firstFile, secondFile = secondFile),
                    ),
                )
            },
        )

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("duplicated-id")
            .hasMessageContaining(firstFile.absolutePath)
            .hasMessageContaining(secondFile.absolutePath)
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should fail with both file paths when a duplicate manifest id is loaded`() {
        // given
        val cli = AiToolsCli(
            runner = {
                throw DuplicateManifestIdException(
                    listOf(
                        DuplicateManifestId(
                            id = "duplicated-id",
                            firstFile = File("/manifests/first.yml"),
                            secondFile = File("/manifests/second.yml"),
                        ),
                    ),
                )
            },
        )

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("duplicated-id")
            .hasMessageContaining("first.yml")
            .hasMessageContaining("second.yml")
        assertThat((error as CliktError).statusCode).isNotZero()
    }
}

/**
 * Renders a `${NAME}` reference into a YAML fixture. Written through a function because a Kotlin raw string cannot
 * escape the dollar of the reference itself.
 */
private fun variableReference(name: String) = "\${$name}"
