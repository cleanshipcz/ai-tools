package cz.cleanship.aitools.engine.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.UnresolvedVariableException
import cz.cleanship.aitools.engine.models.SecretsManagerKind
import cz.cleanship.aitools.engine.models.ToolType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

class ConfigServiceTest {

    @Test
    fun `should load default config when local config is missing`(
        @TempDir tempDir: File,
    ) {
        val configFile = File(tempDir, "config.yml")
        configFile.writeText(
            """
            locations:
              agents:
                - "agents_default"
              deployments:
                - "deployments_default"
            tools:
                - windsurf
            """.trimIndent(),
        )

        val service = ConfigService()
        val (locations, tools) = service.loadConfig(tempDir)

        assertThat(locations.agents).extracting("name").containsExactly("agents_default")
        assertThat(locations.deployments).extracting("name").containsExactly("deployments_default")
        assertThat(locations.prompts).isEmpty()
        assertThat(locations.rulesets).isEmpty()
        assertThat(tools).containsExactly(ToolType.WINDSURF)
    }

    @Test
    fun `should read the mcps location from config yml when config local yml does not repeat it`(
        @TempDir tempDir: File,
    ) {
        // given
        File(tempDir, "config.yml").writeText(
            """
            locations:
              mcps:
                - "07_mcp"
              agents:
                - "agents_default"
            """.trimIndent(),
        )
        File(tempDir, "config.local.yml").writeText(
            """
            locations:
              agents:
                - "agents_local"
            """.trimIndent(),
        )

        // when
        val config = ConfigService().loadConfig(tempDir)

        // then
        assertThat(config.locations.mcps).containsExactly(File(tempDir, "07_mcp"))
        assertThat(config.locations.agents).containsExactly(File(tempDir, "agents_local"))
    }

    @Test
    fun `should replace the mcps location with the list config local yml declares`(
        @TempDir tempDir: File,
    ) {
        // given
        File(tempDir, "config.yml").writeText("locations:\n  mcps:\n    - \"07_mcp\"\n")
        File(tempDir, "config.local.yml").writeText("locations:\n  mcps:\n    - \"private_mcp\"\n")

        // when
        val config = ConfigService().loadConfig(tempDir)

        // then
        assertThat(config.locations.mcps).containsExactly(File(tempDir, "private_mcp"))
    }

    @Test
    fun `should override default config with local config`(
        @TempDir tempDir: File,
    ) {
        val configFile = File(tempDir, "config.yml")
        configFile.writeText(
            """
            locations:
              agents:
                - "agents_default"
              deployments:
                - "deployments_default"
            tools:
                - windsurf
            """.trimIndent(),
        )

        val localConfigFile = File(tempDir, "config.local.yml")
        localConfigFile.writeText(
            """
            locations:
              deployments:
                - "deployments_local"
              rulesets:
                - "rulesets_local"
            tools:
                - antigravity
                - github_copilot
            """.trimIndent(),
        )

        val service = ConfigService()
        val (locations, tools) = service.loadConfig(tempDir)

        // Agents should settle on default (not in local)
        assertThat(locations.agents).extracting("name").containsExactly("agents_default")

        // Projects should be overridden by local
        assertThat(locations.deployments).extracting("name").containsExactly("deployments_local")

        // Rulesets should take local value (default was null/empty)
        assertThat(locations.rulesets).extracting("name").containsExactly("rulesets_local")

        // Prompts should stay empty
        assertThat(locations.prompts).isEmpty()

        // Tools should be overridden by local
        assertThat(tools).containsExactly(ToolType.ANTIGRAVITY, ToolType.GITHUB_COPILOT)
    }

    @Test
    fun `should fail naming the rename when a config still declares the retired projects key`(
        @TempDir tempDir: File,
    ) {
        // given
        // - config.local.yml is gitignored, so no rename in this repository can reach the one on another machine;
        //   dropping the key silently would leave that machine deploying nothing and reporting success
        File(tempDir, "config.yml").writeText(
            """
            locations:
              projects:
                - "09_projects"
            """.trimIndent(),
        )

        // when
        val error = runCatching { ConfigService().loadConfig(tempDir) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(RetiredConfigKeyException::class.java)
            .hasMessageContaining("locations.projects")
            .hasMessageContaining("locations.deployments")
            .hasMessageContaining("config.yml")
    }

    @Test
    fun `should name the local config when it is the one declaring the retired projects key`(
        @TempDir tempDir: File,
    ) {
        // given
        File(tempDir, "config.yml").writeText(
            """
            locations:
              deployments:
                - "09_deployments"
            """.trimIndent(),
        )
        File(tempDir, "config.local.yml").writeText(
            """
            locations:
              projects:
                - "09_projects"
            """.trimIndent(),
        )

        // when
        val error = runCatching { ConfigService().loadConfig(tempDir) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(RetiredConfigKeyException::class.java)
            .hasMessageContaining("config.local.yml")
    }

    @Test
    fun `should throw exception when missing config file`(
        @TempDir tempDir: File,
    ) {
        // given
        val service = ConfigService()

        // when
        val exception = assertThatThrownBy {
            service.loadConfig(tempDir)
        }

        // then
        exception.isInstanceOf(FileNotFoundException::class.java)
    }

    /**
     * `secrets_manager` names the secrets manager of the machine, which the secrets of stdio MCP servers are read from when a tool starts them.
     */
    @Nested
    inner class SecretsManagerSwitch {

        @Test
        fun `should use libsecret when no config file names a secrets manager`(
            @TempDir tempDir: File,
        ) {
            // given
            File(tempDir, "config.yml").writeText("tools:\n  - claude\n")

            // when
            val config = ConfigService().loadConfig(tempDir)

            // then
            assertThat(config.secretsManager).isEqualTo(SecretsManagerKind.LIBSECRET)
        }

        @ParameterizedTest
        @CsvSource(
            // - config.yml alone decides when config.local.yml names none
            "environment, , ENVIRONMENT",
            "libsecret, , LIBSECRET",
            // - config.local.yml wins, as it does for every other setting
            "environment, libsecret, LIBSECRET",
            "libsecret, environment, ENVIRONMENT",
            ", environment, ENVIRONMENT",
        )
        fun `should take the secrets manager from config local yml over config yml`(
            base: String?,
            local: String?,
            expected: SecretsManagerKind,
            @TempDir tempDir: File,
        ) {
            // given
            File(tempDir, "config.yml").writeText("tools:\n  - claude\n" + (base?.let { "secrets_manager: $it\n" } ?: ""))
            local?.let { File(tempDir, "config.local.yml").writeText("secrets_manager: $it\n") }

            // when
            val config = ConfigService().loadConfig(tempDir)

            // then
            assertThat(config.secretsManager).isEqualTo(expected)
        }

        @ParameterizedTest
        @CsvSource(
            "config.yml, keychain",
            "config.local.yml, Libsecret",
            "config.local.yml, ''",
        )
        fun `should fail naming the file and the accepted values when a config names a secrets manager this engine does not know`(
            file: String,
            value: String,
            @TempDir tempDir: File,
        ) {
            // given
            File(tempDir, "config.yml").writeText("tools:\n  - claude\n")
            File(tempDir, file).appendText("secrets_manager: '$value'\n")

            // when
            val error = runCatching { ConfigService().loadConfig(tempDir) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(InvalidConfigValueException::class.java)
                .hasMessageContaining("'secrets_manager'")
                .hasMessageContaining(file)
                .hasMessageContaining("'libsecret'")
                .hasMessageContaining("'environment'")
        }
    }

    /**
     * A key this engine does not know is ignored, as a key of a newer engine must be, but never silently: a misspelled `secrets_manager` would otherwise leave the default in force.
     */
    @Nested
    inner class UnknownKeys {

        private val logAppender = ListAppender<ILoggingEvent>()
        private val serviceLogger = LoggerFactory.getLogger(ConfigService::class.java) as Logger

        @BeforeEach
        fun setUpLog() {
            logAppender.start()
            serviceLogger.addAppender(logAppender)
        }

        @AfterEach
        fun tearDownLog() {
            serviceLogger.detachAppender(logAppender)
            logAppender.stop()
            logAppender.list.clear()
        }

        @Test
        fun `should warn once naming the file and the key of every key this engine does not know, and go on with the rest`(
            @TempDir tempDir: File,
        ) {
            // given
            // - a misspelled switch, a misspelled location, and an unknown key of config.local.yml
            File(tempDir, "config.yml").writeText("tools:\n  - claude\nsecrets_managr: environment\nlocations:\n  agents:\n    - agents\n  mcp:\n    - 07_mcp\n")
            File(tempDir, "config.local.yml").writeText("future_setting:\n  nested: 1\nsecrets_manager: libsecret\n")

            // when
            val config = ConfigService().loadConfig(tempDir)

            // then
            assertThat(warnings()).containsExactly(
                "config.yml declares the key 'secrets_managr', which this engine does not know, so it is ignored. Check its spelling; a key of a newer engine is ignored the same way.",
                "config.yml declares the key 'locations.mcp', which this engine does not know, so it is ignored. Check its spelling; a key of a newer engine is ignored the same way.",
                "config.local.yml declares the key 'future_setting', which this engine does not know, so it is ignored. Check its spelling; a key of a newer engine is ignored the same way.",
            )
            assertThat(config.tools).containsExactly(ToolType.CLAUDE)
            assertThat(config.locations.agents).containsExactly(File(tempDir, "agents"))
            assertThat(config.locations.mcps).isEmpty()
            assertThat(config.secretsManager).isEqualTo(SecretsManagerKind.LIBSECRET)
        }

        @Test
        fun `should not warn about any key the engine knows, the variables of env_vars included`(
            @TempDir tempDir: File,
        ) {
            // given
            File(tempDir, "config.yml").writeText(
                "tools:\n  - claude\nsecrets_manager: environment\nenv_vars:\n  ANY_NAME: value\n  other_name: value\nlocations:\n" +
                    listOf("agents", "deployments", "prompts", "rulesets", "fragments", "skills", "mcps").joinToString("") { "  $it:\n    - $it\n" },
            )
            File(tempDir, "config.local.yml").writeText("tools: []\n")

            // when
            ConfigService().loadConfig(tempDir)

            // then
            assertThat(warnings()).isEmpty()
        }

        private fun warnings() = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }

    @Nested
    inner class EnvironmentVariables {

        private lateinit var processEnvironment: MutableMap<String, String>
        private lateinit var service: ConfigService

        @BeforeEach
        fun setUp() {
            // A faked environment keeps the tests from depending on - or mutating - the environment of the JVM they run in.
            processEnvironment = mutableMapOf()
            service = ConfigService(EnvironmentSource { name -> processEnvironment[name] })
        }

        @Test
        fun `should expand a declared variable in every location referencing it`(
            @TempDir tempDir: File,
        ) {
            // given
            File(tempDir, "config.yml").writeText(
                """
                env_vars:
                  ROOT: "${tempDir.absolutePath}"
                  TEAM: "platform"
                locations:
                  agents:
                    - "${variableReference("ROOT")}/agents"
                  deployments:
                    - "${variableReference("ROOT")}/projects/${variableReference("TEAM")}"
                  prompts:
                    - "prompts_without_reference"
                """.trimIndent(),
            )

            // when
            val (locations) = service.loadConfig(tempDir)

            // then
            assertThat(locations.agents).containsExactly(File(tempDir, "agents"))
            // - a value may carry several references
            assertThat(locations.deployments).containsExactly(File(tempDir, "projects/platform"))
            // - and one that carries none is resolved exactly as before
            assertThat(locations.prompts).containsExactly(File(tempDir, "prompts_without_reference"))
        }

        @Test
        fun `should override a single variable from the local config while keeping the others`(
            @TempDir tempDir: File,
        ) {
            // given
            File(tempDir, "config.yml").writeText(
                """
                env_vars:
                  ROOT: "${tempDir.absolutePath}/default"
                  TEAM: "platform"
                locations:
                  agents:
                    - "${variableReference("ROOT")}/agents"
                  deployments:
                    - "${variableReference("ROOT")}/${variableReference("TEAM")}"
                """.trimIndent(),
            )
            // - the local config redeclares one of the two variables
            File(tempDir, "config.local.yml").writeText(
                """
                env_vars:
                  ROOT: "${tempDir.absolutePath}/local"
                """.trimIndent(),
            )

            // when
            val (locations) = service.loadConfig(tempDir)

            // then
            assertThat(locations.agents).containsExactly(File(tempDir, "local/agents"))
            // - the variable the local config says nothing about survives the merge
            assertThat(locations.deployments).containsExactly(File(tempDir, "local/platform"))
        }

        @Test
        fun `should expand a variable that only the local config declares`(
            @TempDir tempDir: File,
        ) {
            // given
            // - the shared config references a variable it leaves to each machine to declare
            File(tempDir, "config.yml").writeText(
                """
                env_vars:
                  TEAM: "platform"
                locations:
                  agents:
                    - "${variableReference("ROOT")}/agents/${variableReference("TEAM")}"
                """.trimIndent(),
            )
            File(tempDir, "config.local.yml").writeText(
                """
                env_vars:
                  ROOT: "${tempDir.absolutePath}/local-only"
                """.trimIndent(),
            )

            // when
            val (locations) = service.loadConfig(tempDir)

            // then
            assertThat(locations.agents).containsExactly(File(tempDir, "local-only/agents/platform"))
        }

        @Test
        fun `should expand a local variable when the default config declares none at all`(
            @TempDir tempDir: File,
        ) {
            // given
            // - a config written before this feature existed, beside a local config that adds the variables
            File(tempDir, "config.yml").writeText(
                """
                locations:
                  agents:
                    - "${variableReference("ROOT")}/agents"
                """.trimIndent(),
            )
            File(tempDir, "config.local.yml").writeText(
                """
                env_vars:
                  ROOT: "${tempDir.absolutePath}/from-local"
                """.trimIndent(),
            )

            // when
            val (locations) = service.loadConfig(tempDir)

            // then
            assertThat(locations.agents).containsExactly(File(tempDir, "from-local/agents"))
        }

        @Test
        fun `should read a variable from the process environment when no config declares it`(
            @TempDir tempDir: File,
        ) {
            // given
            processEnvironment["PROJECTS_FOLDER"] = "${tempDir.absolutePath}/from-environment"
            File(tempDir, "config.yml").writeText(
                """
                locations:
                  agents:
                    - "${variableReference("PROJECTS_FOLDER")}/agents"
                """.trimIndent(),
            )

            // when
            val (locations) = service.loadConfig(tempDir)

            // then
            assertThat(locations.agents).containsExactly(File(tempDir, "from-environment/agents"))
        }

        @Test
        fun `should prefer the declared variable when the process environment carries the same name`(
            @TempDir tempDir: File,
        ) {
            // given
            processEnvironment["PROJECTS_FOLDER"] = "${tempDir.absolutePath}/from-environment"
            File(tempDir, "config.yml").writeText(
                """
                env_vars:
                  PROJECTS_FOLDER: "${tempDir.absolutePath}/from-config"
                locations:
                  agents:
                    - "${variableReference("PROJECTS_FOLDER")}/agents"
                """.trimIndent(),
            )

            // when
            val (locations) = service.loadConfig(tempDir)

            // then
            assertThat(locations.agents).containsExactly(File(tempDir, "from-config/agents"))
        }

        @Test
        fun `should fail naming the variable when a location references one nothing declares`(
            @TempDir tempDir: File,
        ) {
            // given
            File(tempDir, "config.yml").writeText(
                """
                locations:
                  agents:
                    - "${variableReference("MISSING_ROOT")}/agents"
                """.trimIndent(),
            )

            // when
            val error = runCatching { service.loadConfig(tempDir) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(UnresolvedVariableException::class.java)
                .hasMessageContaining("MISSING_ROOT")
                // - the field is named together with the one file that declared it, not both candidates
                .hasMessageContaining("locations.agents of config.yml")
        }

        @Test
        fun `should name the local config in the failure when it declared the failing location`(
            @TempDir tempDir: File,
        ) {
            // given
            // - a location list is taken whole from one file, and here the local config replaced the default one
            File(tempDir, "config.yml").writeText(
                """
                locations:
                  agents:
                    - "agents_default"
                """.trimIndent(),
            )
            File(tempDir, "config.local.yml").writeText(
                """
                locations:
                  agents:
                    - "${variableReference("MISSING_ROOT")}/agents"
                """.trimIndent(),
            )

            // when
            val error = runCatching { service.loadConfig(tempDir) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(UnresolvedVariableException::class.java)
                .hasMessageContaining("MISSING_ROOT")
                .hasMessageContaining("locations.agents of config.local.yml")
        }

        @Test
        fun `should expose the merged variables so a path declared outside the config can use them`(
            @TempDir tempDir: File,
        ) {
            // given
            // - 'deploy.directory' of a project manifest is substituted with the variables of the run, not of the manifest
            File(tempDir, "config.yml").writeText(
                """
                env_vars:
                  PROJECTS_FOLDER: "${tempDir.absolutePath}"
                """.trimIndent(),
            )

            // when
            val config = service.loadConfig(tempDir)

            // then
            assertThat(config.variables.substitute("${variableReference("PROJECTS_FOLDER")}/custom-ai-tools"))
                .isEqualTo("${tempDir.absolutePath}/custom-ai-tools")
            assertThat(config.variables.substitute("plain/path")).isEqualTo("plain/path")
        }

        @Test
        fun `should load a config declaring no variables`(
            @TempDir tempDir: File,
        ) {
            // given
            // - every config written before this feature existed declares none, and must keep loading
            File(tempDir, "config.yml").writeText(
                """
                locations:
                  agents:
                    - "agents_default"
                """.trimIndent(),
            )

            // when
            val config = service.loadConfig(tempDir)

            // then
            assertThat(config.locations.agents).containsExactly(File(tempDir, "agents_default"))
        }
    }
}

/**
 * Renders a `${NAME}` reference into a YAML fixture. Written through a function because a Kotlin raw string cannot
 * escape the dollar of the reference itself.
 */
private fun variableReference(name: String) = "\${$name}"
