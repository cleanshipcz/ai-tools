package cz.cleanship.aitools.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.testing.test
import cz.cleanship.aitools.engine.ExportFailedException
import cz.cleanship.aitools.engine.ExportFailure
import cz.cleanship.aitools.engine.io.ArtifactPathException
import cz.cleanship.aitools.engine.models.DuplicateManifestId
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.DryRunArtifactSink
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
import cz.cleanship.aitools.engine.services.ManifestLoadingException
import cz.cleanship.aitools.engine.tools.RulesetResolvingException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
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
              deployments:
                - "deployments"
            tools:
              - claude
            """.trimIndent(),
        )
        // - a project deploying to a relative directory, which must land on the same base the locations use
        val projectFile = File(tempDir, "deployments/test-project/project.yml")
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
              PROJECTS_FOLDER: "${tempDir.absolutePath}/exported"
            locations:
              deployments:
                - "deployments"
            tools:
              - claude
            """.trimIndent(),
        )
        val projectFile = File(tempDir, "deployments/test-project/project.yml")
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
        assertThat(File(tempDir, "exported/custom-ai-tools/CLAUDE.md")).exists()
        // - the reference was expanded rather than taken for a directory name
        assertThat(File(tempDir, "exported").list()).containsExactly("custom-ai-tools")
    }

    @Test
    fun `should fail with the variable name when a deploy directory references an undeclared variable`() {
        // given
        File(tempDir, "config.yml").writeText(
            """
            locations:
              deployments:
                - "deployments"
            tools:
              - claude
            """.trimIndent(),
        )
        val projectFile = File(tempDir, "deployments/test-project/project.yml")
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
              deployments:
                - "${variableReference("AI_TOOLS_UNDECLARED_TEST_FOLDER")}/deployments"
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
    fun `should deploy a user deployment into the home the option names`() {
        // given
        // - the home base is always the one the run was given; the real home of this machine is never involved
        val userHome = File(tempDir, "home")
        File(tempDir, "config.yml").writeText(
            """
            locations:
              deployments:
                - "deployments"
              rulesets:
                - "rulesets"
            tools:
              - claude
            """.trimIndent(),
        )
        val rulesetFile = File(tempDir, "rulesets/base.yml")
        rulesetFile.parentFile.mkdirs()
        rulesetFile.writeText(
            """
            id: base
            description: A ruleset
            rules:
              - A rule from base.
            metadata:
              version: 1.0.0
            """.trimIndent(),
        )
        val deploymentFile = File(tempDir, "deployments/globals/user.yml")
        deploymentFile.parentFile.mkdirs()
        deploymentFile.writeText(
            """
            id: globals
            description: A user deployment
            tools:
              - claude
            metadata:
              version: 1.0.0
            """.trimIndent(),
        )
        val cli = AiToolsCli()

        // when
        cli.parse(arrayOf("--working-dir", tempDir.absolutePath, "--user-home", userHome.absolutePath))

        // then
        assertThat(File(userHome, ".claude/CLAUDE.md").readText())
            .contains("# globals")
            .contains("A rule from base.")
    }

    @Test
    fun `should fail when the user home is empty`() {
        // given
        // - an empty value resolves to the directory the shell happened to be in, which is never what was meant
        val cli = AiToolsCli(runner = { _, _, _ -> })

        // when
        val error = runCatching {
            cli.parse(arrayOf("--working-dir", tempDir.absolutePath, "--user-home", ""))
        }.exceptionOrNull()

        // then
        assertThat(error).isInstanceOf(CliktError::class.java)
    }

    @Test
    fun `should resolve a relative user home against the working directory`() {
        // given
        // - the same base every other declared path of the run uses, rather than the working directory of the JVM
        var recordedUserHome: File? = null
        val cli = AiToolsCli(runner = { _, userHome, _ -> recordedUserHome = userHome })

        // when
        cli.parse(arrayOf("--working-dir", tempDir.absolutePath, "--user-home", "scratch-home"))

        // then
        assertThat(recordedUserHome).isEqualTo(File(tempDir, "scratch-home"))
    }

    @Test
    fun `should fail with the guard message when a manifest id would escape its directory`() {
        // given
        val cli = AiToolsCli(
            runner = { _, _, _ ->
                throw ArtifactPathException("Refusing to replace '/home/user/evil': it is not inside '/home/user/.claude/skills'.")
            },
        )

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        // - the remediation sentence reaches the operator instead of being buried in a stack trace
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("Refusing to replace")
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should run under the home of this user when the option is not given`() {
        // given
        // - a runner that records what it was given, so the default is proven without a single file being written
        var recordedUserHome: File? = null
        val cli = AiToolsCli(runner = { _, userHome, _ -> recordedUserHome = userHome })

        // when
        cli.parse(arrayOf("--working-dir", tempDir.absolutePath))

        // then
        assertThat(recordedUserHome).isEqualTo(File(System.getProperty("user.home")))
    }

    @Test
    fun `should run a deploy when the dry run flag is not given`() {
        // given
        var recordedDryRun: Boolean? = null
        val cli = AiToolsCli(runner = { _, _, dryRun -> recordedDryRun = dryRun })

        // when
        cli.parse(arrayOf("--working-dir", tempDir.absolutePath))

        // then
        assertThat(recordedDryRun).isFalse()
    }

    @Test
    fun `should run a dry run when the flag is given`() {
        // given
        var recordedDryRun: Boolean? = null
        val cli = AiToolsCli(runner = { _, _, dryRun -> recordedDryRun = dryRun })

        // when
        cli.parse(arrayOf("--working-dir", tempDir.absolutePath, "--dry-run"))

        // then
        assertThat(recordedDryRun).isTrue()
    }

    /**
     * A dry run over a real manifest set: everything a deploy loads, filters and renders is loaded, filtered and
     * rendered, and the failures are the same, while the deploy directory and the home stay exactly as they were.
     */
    @Nested
    inner class DryRun {

        private val sinkAppender = ListAppender<ILoggingEvent>()
        private lateinit var sinkLogger: Logger
        private lateinit var generated: File
        private lateinit var userHome: File

        @BeforeEach
        fun setUp() {
            sinkLogger = LoggerFactory.getLogger(DryRunArtifactSink::class.java) as Logger
            sinkAppender.start()
            sinkLogger.addAppender(sinkAppender)
            generated = File(tempDir, "generated")
            userHome = File(tempDir, "home")
            writeManifestSet(agentRuleset = "base")
        }

        @AfterEach
        fun tearDown() {
            sinkLogger.detachAppender(sinkAppender)
            sinkAppender.stop()
            sinkAppender.list.clear()
        }

        @Test
        fun `should validate the manifests without writing the deploy directory or the home`() {
            // when
            val result = AiToolsCli().test(dryRunArguments())

            // then
            assertThat(result.statusCode).isZero()
            assertThat(generated).doesNotExist()
            assertThat(userHome).doesNotExist()
            // - the only things under the working directory are the manifests the test wrote itself
            assertThat(
                tempDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .map { it.name }
                    .toList(),
            ).containsExactlyInAnyOrder("config.yml", "project.yml", "user.yml", "base.yml", "good-agent.yml", "skill.yml", "helper.md")
        }

        @Test
        fun `should say it was a dry run in its output`() {
            // when
            val result = AiToolsCli().test(dryRunArguments())

            // then
            assertThat(result.stdout).contains("Dry run")
        }

        @Test
        fun `should report the absolute path of every artifact it would write`() {
            // when
            AiToolsCli().test(dryRunArguments())

            // then
            val skillDir = generated.resolve(".claude/skills/directory-skill")
            assertThat(sinkInfos()).anyMatch { it.contains(generated.resolve("CLAUDE.md").absolutePath) }
            assertThat(sinkInfos()).anyMatch { it.contains(generated.resolve(".claude/agents/good-agent.md").absolutePath) }
            assertThat(sinkInfos()).anyMatch { it.contains(skillDir.resolve("SKILL.md").absolutePath) }
            assertThat(sinkInfos()).anyMatch { it.contains(skillDir.resolve("helper.md").absolutePath) }
            assertThat(sinkInfos()).anyMatch { it.contains(userHome.resolve(".claude/CLAUDE.md").absolutePath) }
        }

        @Test
        fun `should leave what an earlier deploy wrote untouched even though the project replaces it`() {
            // given
            val instructions = generated.resolve("CLAUDE.md")
            instructions.parentFile.mkdirs()
            instructions.writeText("From an earlier deploy.\n")
            val stale = generated.resolve(".claude/agents/stale-agent.md")
            stale.parentFile.mkdirs()
            stale.writeText("Left behind by an earlier deploy.\n")
            val homeInstructions = userHome.resolve(".claude/CLAUDE.md")
            homeInstructions.parentFile.mkdirs()
            homeInstructions.writeText("From an earlier deploy of the home.\n")

            // when
            val result = AiToolsCli().test(dryRunArguments())

            // then
            assertThat(result.statusCode).isZero()
            assertThat(instructions).hasContent("From an earlier deploy.\n")
            assertThat(stale).hasContent("Left behind by an earlier deploy.\n")
            assertThat(homeInstructions).hasContent("From an earlier deploy of the home.\n")
            assertThat(generated.walkTopDown().filter { it.isFile }.toList()).containsExactlyInAnyOrder(instructions, stale)
            assertThat(userHome.walkTopDown().filter { it.isFile }.toList()).containsExactly(homeInstructions)
        }

        @Test
        fun `should fail with the resolver message when an agent references an unknown ruleset`() {
            // given
            writeManifestSet(agentRuleset = "missing-ruleset")

            // when
            val result = AiToolsCli().test(dryRunArguments())

            // then
            assertThat(result.statusCode).isNotZero()
            assertThat(result.stderr)
                .contains("No rulesets match pattern 'missing-ruleset'")
                .contains("agent 'good-agent'")
                .contains("Dry run")
            assertThat(generated).doesNotExist()
            assertThat(userHome).doesNotExist()
        }

        private fun dryRunArguments() = listOf(
            "--working-dir",
            tempDir.absolutePath,
            "--user-home",
            userHome.absolutePath,
            "--dry-run",
        )

        private fun sinkInfos() = sinkAppender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }

        /**
         * Writes a manifest set exercising every kind of write a deploy makes: a replacing project with an agent and
         * a skill carrying a companion file, and a user deployment into the home.
         */
        private fun writeManifestSet(agentRuleset: String) {
            writeConfig()
            writeSharedManifests(agentRuleset)
            writeDeployments()
        }

        private fun writeConfig() {
            File(tempDir, "config.yml").writeText(
                """
                locations:
                  deployments:
                    - "deployments"
                  rulesets:
                    - "rulesets"
                  agents:
                    - "agents"
                  skills:
                    - "skills"
                tools:
                  - claude
                """.trimIndent(),
            )
        }

        private fun writeSharedManifests(agentRuleset: String) {
            writeFile(
                "rulesets/base.yml",
                """
                id: base
                description: A ruleset
                rules:
                  - A rule from base.
                metadata:
                  version: 1.0.0
                """.trimIndent(),
            )
            writeFile(
                "agents/good-agent.yml",
                """
                id: good-agent
                description: An agent
                rulesets:
                  - $agentRuleset
                persona: A persona
                prompt: An agent prompt
                metadata:
                  version: 1.0.0
                """.trimIndent(),
            )
            writeFile(
                "skills/directory-skill/skill.yml",
                """
                id: directory-skill
                description: A skill
                sections:
                  - text: Some skill content
                files:
                  - path: helper.md
                metadata:
                  version: 1.0.0
                """.trimIndent(),
            )
            writeFile("skills/directory-skill/helper.md", "Companion content.\n")
        }

        private fun writeDeployments() {
            writeFile(
                "deployments/test-project/project.yml",
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
                  replace: true
                """.trimIndent(),
            )
            writeFile(
                "deployments/globals/user.yml",
                """
                id: globals
                description: A user deployment
                replace: true
                tools:
                  - claude
                metadata:
                  version: 1.0.0
                """.trimIndent(),
            )
        }

        private fun writeFile(relativePath: String, content: String) {
            val file = File(tempDir, relativePath)
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }

    @Test
    fun `should fail with the resolver message when an export fails`() {
        // given
        // - a CliktError makes the command report on stderr and exit with a non-zero status code
        val failure = ExportFailure(
            deploymentId = "test-project",
            toolType = ToolType.CLAUDE,
            manifest = "agent 'broken-agent'",
            cause = RulesetResolvingException(
                pattern = "missing-ruleset",
                requestedBy = "agent 'broken-agent'",
                availableIds = listOf("base"),
            ),
        )
        val cli = AiToolsCli(runner = { _, _, _ -> throw ExportFailedException(listOf(failure)) })

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
            runner = { _, _, _ ->
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
            runner = { _, _, _ ->
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
            runner = { _, _, _ ->
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
