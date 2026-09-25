package cz.cleanship.aitools.engine

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.UnresolvedVariableException
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.io.ArtifactDeleteException
import cz.cleanship.aitools.engine.io.ArtifactPathException
import cz.cleanship.aitools.engine.io.ToolDirectoryException
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.McpToolRestriction
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.models.serialName
import cz.cleanship.aitools.engine.services.DryRunArtifactSink
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
import cz.cleanship.aitools.engine.services.FileSystemArtifactSink
import cz.cleanship.aitools.engine.services.ManifestLoadingException
import cz.cleanship.aitools.engine.services.SkillFileResolvingException
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.ToolFactory
import cz.cleanship.aitools.engine.tools.adapters.claude.ClaudeAdapter
import cz.cleanship.aitools.engine.tools.adapters.codex.CodexAdapter
import cz.cleanship.aitools.engine.tools.adapters.cursor.CursorAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter
import cz.cleanship.aitools.engine.tools.mcp.McpLedger
import cz.cleanship.aitools.engine.utils.contentSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class ToolsEngineTest {

    @TempDir
    lateinit var tempDir: Path

    private val logAppender = ListAppender<ILoggingEvent>()

    // An environment carrying nothing, so that no variable of the JVM running the tests can reach a deploy directory.
    private val emptyEnvironment = EnvironmentSource { null }

    private lateinit var workspace: File
    private lateinit var destination: File

    /**
     * The home base every engine of this class deploys the user scope under. It is a directory of the test's own temporary tree, so no test can reach the real home of whoever runs the suite.
     */
    private lateinit var userHome: File
    private lateinit var engine: ToolsEngine
    private lateinit var engineLogger: Logger

    @BeforeEach
    fun setUp() {
        workspace = tempDir.resolve("workspace").toFile()
        destination = tempDir.resolve("destination").toFile()
        userHome = tempDir.resolve("home").toFile()
        engine = ToolsEngine(workspace, userHome = userHome, tools = listOf(ClaudeAdapter()))
        engineLogger = LoggerFactory.getLogger(ToolsEngine::class.java) as Logger
        logAppender.start()
        engineLogger.addAppender(logAppender)
        // - a resolvable ruleset and the project that deploys into the destination are always present
        writeRuleset("base")
        writeProject()
    }

    @AfterEach
    fun tearDown() {
        engineLogger.detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
    }

    @Nested
    inner class SuccessfulExport {

        @Test
        fun `should export every manifest when all references resolve`() {
            // given
            writeAgent("good-agent", "base")
            writePrompt("good-prompt", "base")

            // when
            engine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(agentFile("good-agent")).exists()
            assertThat(agentFile("good-agent").readText()).contains("A rule from base.")
            assertThat(promptFile("good-prompt")).exists()
            assertThat(promptFile("good-prompt").readText()).contains("A rule from base.")
        }

        @Test
        fun `should export the project memory file when the project has no agents or prompts`() {
            // when
            engine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
        }
    }

    @Nested
    inner class DeployDirectoryResolution {

        @Test
        fun `should resolve a relative deploy directory against the working directory`() {
            // given
            // - a project deploying to a path relative to the working directory of the run
            val relativeDestination = "relative-destination"
            writeProject(deployDirectory = relativeDestination)

            // when
            engine.process(locations())

            // then
            assertThat(workspace.resolve(relativeDestination).resolve("CLAUDE.md")).exists()
            // - and nothing landed relative to the JVM working directory, which is a different base entirely
            assertThat(File(relativeDestination)).doesNotExist()
        }

        @Test
        fun `should resolve a relative deploy directory that climbs out of the working directory`() {
            // given
            // - the base of a `..` segment is the working directory, so the project lands next to it
            writeProject(deployDirectory = "../sibling-destination")

            // when
            engine.process(locations())

            // then
            assertThat(tempDir.resolve("sibling-destination").resolve("CLAUDE.md").toFile()).exists()
        }

        @Test
        fun `should use an absolute deploy directory as given`() {
            // given
            val absoluteDestination = tempDir.resolve("absolute-destination").toFile()
            writeProject(deployDirectory = absoluteDestination.absolutePath)

            // when
            engine.process(locations())

            // then
            assertThat(absoluteDestination.resolve("CLAUDE.md")).exists()
            // - an absolute destination is never re-based under the working directory
            assertThat(workspace.walkTopDown().filter { it.name == "CLAUDE.md" }.toList()).isEmpty()
        }

        @Test
        fun `should expand a variable in the deploy directory before resolving it`() {
            // given
            // - the variable carries an absolute base, so the expanded value is absolute rather than relative
            val variableDestination = tempDir.resolve("variable-destination").toFile()
            val variableEngine = ToolsEngine(
                workspace,
                variables = VariableResolver(mapOf("PROJECTS_FOLDER" to variableDestination.absolutePath), emptyEnvironment),
                tools = listOf(ClaudeAdapter()),
            )
            writeProject(deployDirectory = "\${PROJECTS_FOLDER}/custom-ai-tools")

            // when
            variableEngine.process(locations())

            // then
            assertThat(variableDestination.resolve("custom-ai-tools/CLAUDE.md")).exists()
        }

        @Test
        fun `should resolve an expanded relative deploy directory against the working directory`() {
            // given
            // - the variable expands to a relative value, which resolves against the working directory like any other
            val variableEngine = ToolsEngine(
                workspace,
                variables = VariableResolver(mapOf("SUBDIR" to "generated"), emptyEnvironment),
                tools = listOf(ClaudeAdapter()),
            )
            writeProject(deployDirectory = "\${SUBDIR}/custom-ai-tools")

            // when
            variableEngine.process(locations())

            // then
            assertThat(workspace.resolve("generated/custom-ai-tools/CLAUDE.md")).exists()
            // - and nothing landed relative to the JVM working directory, which is a different base entirely
            assertThat(File("generated")).doesNotExist()
        }

        @Test
        fun `should fail naming the variable when the deploy directory references an undeclared one`() {
            // given
            val variableEngine = ToolsEngine(
                workspace,
                variables = VariableResolver(emptyMap(), emptyEnvironment),
                tools = listOf(ClaudeAdapter()),
            )
            writeProject(deployDirectory = "\${MISSING_FOLDER}/custom-ai-tools")

            // when
            val error = runCatching { variableEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(DeployDirectoryResolvingException::class.java)
                .hasMessageContaining("MISSING_FOLDER")
                // - the project naming the reference is named too, so the author knows which manifest to fix
                .hasMessageContaining("test-project")
            // - the failure that stopped the project is carried, not only its wording
            assertThat((error as DeployDirectoryResolvingException).failures)
                .hasOnlyElementsOfType(UnresolvedVariableException::class.java)
            // - and nothing was deployed to a directory literally called '${MISSING_FOLDER}'
            val created = tempDir.toFile().walkTopDown().toList()
            assertThat(created).noneMatch { it.name.contains("MISSING_FOLDER") }
        }

        @Test
        fun `should deploy no project at all when a project read later references an undeclared variable`() {
            // given
            // - a deploy may delete before it writes, so a project must not be replaced for a run that cannot finish
            val variableEngine = ToolsEngine(
                workspace,
                variables = VariableResolver(emptyMap(), emptyEnvironment),
                tools = listOf(ClaudeAdapter()),
            )
            // - the project that resolves and the one that does not live under separate roots, because manifests within one directory are found in whatever order the filesystem lists them, while the roots themselves are read in the order they are configured - so this one is provably read first
            writeProject(deployDirectory = destination.absolutePath)
            writeProject("broken-project", "broken-project", "\${MISSING_FOLDER}/custom-ai-tools", root = "late-deployments")
            val twoProjectRoots = locations().copy(
                deployments = listOf(workspace.resolve("deployments"), workspace.resolve("late-deployments")),
            )

            // when
            val error = runCatching { variableEngine.process(twoProjectRoots) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(DeployDirectoryResolvingException::class.java)
            // - the project read before the broken one was left untouched, rather than deployed by a run that then failed
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should report every project whose deploy directory cannot be resolved in one failure`() {
            // given
            // - an author fixing their variables should see all of them at once rather than one per run
            val variableEngine = ToolsEngine(
                workspace,
                variables = VariableResolver(emptyMap(), emptyEnvironment),
                tools = listOf(ClaudeAdapter()),
            )
            writeProject(deployDirectory = "\${FIRST_MISSING}/custom-ai-tools")
            writeProject("second-project", "second-project", "\${SECOND_MISSING}/custom-ai-tools")

            // when
            val error = runCatching { variableEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .hasMessageContaining("2 project(s)")
                .hasMessageContaining("FIRST_MISSING")
                .hasMessageContaining("SECOND_MISSING")
        }

        @Test
        fun `should fail when a project exporting through no tool references an undeclared variable`() {
            // given
            // - the project is narrowed to no adapter, so it is never exported, but its directory is still declared wrong
            val variableEngine = ToolsEngine(
                workspace,
                variables = VariableResolver(emptyMap(), emptyEnvironment),
                tools = listOf(ClaudeAdapter()),
            )
            writeProject(deployDirectory = "\${MISSING_FOLDER}/custom-ai-tools", tools = emptyList())

            // when
            val error = runCatching { variableEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(DeployDirectoryResolvingException::class.java)
                .hasMessageContaining("MISSING_FOLDER")
        }
    }

    @Nested
    inner class ToolSelection {

        @Test
        fun `should export a project only through the tools it declares`() {
            // given
            // - the run configures two tools
            val multiAdapterEngine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter(), CursorAdapter()))
            // - the project narrows itself down to one of them
            writeProject(tools = listOf("claude"))
            writeAgent("good-agent", "base")

            // when
            multiAdapterEngine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(agentFile("good-agent")).exists()
            assertThat(cursorProjectFile()).doesNotExist()
            assertThat(cursorAgentFile("good-agent")).doesNotExist()
        }

        @Test
        fun `should export a project through every configured tool when it declares none`() {
            // given
            // - the project omits deploy.tools entirely, leaving the run-wide tool list in charge
            val multiAdapterEngine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter(), CursorAdapter()))
            writeAgent("good-agent", "base")

            // when
            multiAdapterEngine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(agentFile("good-agent")).exists()
            assertThat(cursorProjectFile()).exists()
            assertThat(cursorAgentFile("good-agent")).exists()
        }

        @Test
        fun `should export through the configured tools and warn when a declared tool is not configured`() {
            // given
            // - the run configures Claude alone, so the Cursor the project declares cannot be honoured
            writeProject(tools = listOf("claude", "cursor"))

            // when
            engine.process(locations())

            // then
            // - the run does not fail over a tool it was simply not asked to build
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(cursorProjectFile()).doesNotExist()
            // - the warning quotes the tool back in the spelling the manifest uses, so the author can grep for it
            assertThat(warnings()).anyMatch { it.contains("test-project") && it.contains("cursor") }
            assertThat(warnings()).noneMatch { it.contains("CURSOR") }
        }

        @Test
        fun `should name a repeated unavailable tool only once`() {
            // given
            // - the schema asks for unique items, but a manifest that repeats one should not stutter in the warning
            writeProject(tools = listOf("cursor", "cursor"))

            // when
            engine.process(locations())

            // then
            assertThat(warnings()).anyMatch { it.contains("[cursor]") }
        }

        @Test
        fun `should warn once when the run itself configures no tools`() {
            // given
            // - an engine with no adapters at all, which is what an emptied `tools:` block of the config produces
            val toollessEngine = ToolsEngine(workspace, tools = emptyList())
            // - a second project, so that the warning is proven to name the run once rather than once per project
            writeProject("second-project", "second-project", tempDir.resolve("second-destination").toString())

            // when
            toollessEngine.process(locations())

            // then
            // - the misconfiguration is named instead of every project being skipped in silence, and the run still succeeds
            assertThat(warnings().filter { it.contains("configures no tools") }).hasSize(1)
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should restrict one project without affecting another project of the same run`() {
            // given
            // - two projects deploy in the same run, only the first one restricts itself
            val multiAdapterEngine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter(), CursorAdapter()))
            val unrestrictedDestination = tempDir.resolve("unrestricted-destination").toFile()
            writeProject(tools = listOf("claude"))
            writeProject("unrestricted-project", "unrestricted-project", unrestrictedDestination.absolutePath)

            // when
            multiAdapterEngine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(cursorProjectFile()).doesNotExist()
            // - the restriction of the first project does not leak onto the second one
            assertThat(unrestrictedDestination.resolve("CLAUDE.md")).exists()
            assertThat(unrestrictedDestination.resolve(".cursor/rules/project.mdc")).exists()
        }

        @Test
        fun `should export a project through no tool when it declares an empty tool list`() {
            // given
            // - an explicit empty list restricts to nothing, unlike an omitted one
            writeProject(tools = emptyList())

            // when
            engine.process(locations())

            // then
            assertThat(destination).doesNotExist()
            assertThat(warnings()).anyMatch { it.contains("test-project") && it.contains("not exported") }
        }
    }

    @Nested
    inner class UnresolvableReferences {

        @Test
        fun `should fail with the resolver message when an agent references an unknown ruleset`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("No rulesets match pattern 'missing-ruleset'")
                .hasMessageContaining("required by agent 'broken-agent'")
                .hasMessageContaining("All available: [base]")
        }

        @Test
        fun `should not leave a truncated file when an agent references an unknown ruleset`() {
            // given
            // - the ruleset is resolved half-way through printing, after the frontmatter has been written
            writeAgent("broken-agent", "missing-ruleset")

            // when
            runCatching { engine.process(locations()) }

            // then
            assertThat(agentFile("broken-agent")).doesNotExist()
        }

        @Test
        fun `should replace no previously exported file when a manifest starts failing`() {
            // given
            // - a first run exported the agent successfully
            writeAgent("agent", "base")
            engine.process(locations())
            val exportedContent = agentFile("agent").readText()

            // - the agent is then edited to reference a ruleset that does not exist
            writeAgent("agent", "missing-ruleset")

            // when
            runCatching { engine.process(locations()) }

            // then
            assertThat(agentFile("agent").readText()).isEqualTo(exportedContent)
        }

        @Test
        fun `should export the remaining manifests when one manifest fails`() {
            // given
            // - agents are exported before prompts, so a broken agent used to skip every prompt
            writeAgent("broken-agent", "missing-ruleset")
            writePrompt("good-prompt", "base")

            // when
            runCatching { engine.process(locations()) }

            // then
            assertThat(promptFile("good-prompt")).exists()
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(agentFile("broken-agent")).doesNotExist()
        }

        @Test
        fun `should report every broken reference of a single run together`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")
            writePrompt("broken-prompt", "another-missing-ruleset")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(ExportFailedException::class.java)
            assertThat((error as ExportFailedException).failures.map { it.manifest })
                .containsExactlyInAnyOrder("agent 'broken-agent'", "prompt 'broken-prompt'")
            assertThat(error)
                .hasMessageContaining("missing-ruleset")
                .hasMessageContaining("another-missing-ruleset")
        }
    }

    @Nested
    inner class UnusableSkillFiles {

        @Test
        fun `should report the failure when a standalone skill declares a relative file`() {
            // given
            // - a standalone skill YAML has no directory of its own, so 'helper.md' cannot be resolved
            writeStandaloneSkill("standalone-skill", "helper.md")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("Cannot resolve relative skill file 'helper.md'")
            assertThat((error as ExportFailedException).failures.map { it.manifest })
                .containsExactly("skill 'standalone-skill'")
        }

        @Test
        fun `should report the failure when a skill declares a companion file that does not exist`() {
            // given
            // - the skill directory exists but the declared companion file was never written
            writeDirectorySkill("directory-skill", "missing.md", companionFileExists = false)

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("missing.md")
            assertThat((error as ExportFailedException).failures.map { it.manifest })
                .containsExactly("skill 'directory-skill'")
        }

        @Test
        fun `should export the remaining manifests when a skill file cannot be copied`() {
            // given
            // - the first skill fails on resolution, the second one on the copy itself
            writeStandaloneSkill("standalone-skill", "helper.md")
            writeDirectorySkill("directory-skill", "missing.md", companionFileExists = false)
            writeAgent("good-agent", "base")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat((error as ExportFailedException).failures.map { it.manifest })
                .containsExactlyInAnyOrder("skill 'standalone-skill'", "skill 'directory-skill'")
            assertThat(agentFile("good-agent")).exists()
        }

        /**
         * A companion file that cannot be read fails its skill, naming the source rather than the target, in a dry run as in a deploy; the tool goes on with its other files, and the file an earlier deploy copied is kept.
         */
        @ParameterizedTest
        @CsvSource("false", "true")
        fun `should report a companion file that cannot be read as a failure of its skill naming the source`(
            dryRun: Boolean,
        ) {
            // given
            val manifest = writeDirectorySkill("directory-skill", "helper.md", companionFileExists = true)
            val source = manifest.parentFile.resolve("helper.md")
            writeAgent("good-agent", "base")
            // - what an earlier deploy copied there
            val deployed = destination.resolve(".claude/skills/directory-skill/helper.md")
            deployed.parentFile.mkdirs()
            deployed.writeText("Deployed before.\n")
            Files.setPosixFilePermissions(source.toPath(), PosixFilePermissions.fromString("---------"))
            val runEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ToolFactory.create(ToolType.CLAUDE, dryRun)), dryRun = dryRun)

            // when
            val error = try {
                // - a user who may read anything, such as root, cannot be refused a read
                assumeTrue(!Files.isReadable(source.toPath()))
                runCatching { runEngine.process(locations()) }.exceptionOrNull()
            } finally {
                Files.setPosixFilePermissions(source.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
            }

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("Skill file '${source.absolutePath}' cannot be read.")
                .hasMessageNotContaining("cannot be written")
            assertThat((error as ExportFailedException).failures.map { it.manifest to it.cause::class.java })
                .containsExactly("skill 'directory-skill'" to SkillFileResolvingException::class.java)
            assertThat(deployed).content().isEqualTo("Deployed before.\n")
            assertThat(agentFile("good-agent").exists()).isEqualTo(!dryRun)
        }

        @Test
        fun `should stop the run rather than collect a failed delete raised while a project is exported`() {
            // given
            // - an adapter that deletes inside the export of an agent, as the user-scope adapters do for a skill
            writeAgent("good-agent", "base")
            val deleting = object : ToolAdapter by ClaudeAdapter() {
                override fun export(projectDir: File, agentContext: AgentContext): Unit =
                    throw ArtifactDeleteException("${projectDir.absolutePath}/.claude/agents/locked", IOException("locked"))
            }
            val deletingEngine = ToolsEngine(workspace, userHome = userHome, tools = listOf(deleting))

            // when
            val error = runCatching { deletingEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ReplaceFailedException::class.java)
                .hasMessageContaining("project 'test-project'")
                .hasMessageContaining("/.claude/agents/locked")
        }

        @Test
        fun `should export the skill when its companion file exists`() {
            // given
            writeDirectorySkill("directory-skill", "helper.md", companionFileExists = true)

            // when
            engine.process(locations())

            // then
            assertThat(destination.resolve(".claude/skills/directory-skill/SKILL.md")).exists()
            assertThat(destination.resolve(".claude/skills/directory-skill/helper.md")).exists()
        }
    }

    @Nested
    inner class DuplicateIds {

        @Test
        fun `should export the unaffected projects when two projects share an id`() {
            // given
            // - two project directories declare the same id, next to the healthy project written in setUp
            val firstDestination = tempDir.resolve("first-destination").toFile()
            val secondDestination = tempDir.resolve("second-destination").toFile()
            val firstFile = writeProject("duplicated-a", "duplicated-project", firstDestination.absolutePath)
            val secondFile = writeProject("duplicated-b", "duplicated-project", secondDestination.absolutePath)

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            // - the project that shares nothing with the collision deployed as usual
            assertThat(destination.resolve("CLAUDE.md")).exists()
            // - neither colliding project was exported, because neither may be picked as the winner
            assertThat(firstDestination).doesNotExist()
            assertThat(secondDestination).doesNotExist()
            // - and the run still fails, naming the id and both files
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("duplicated-project")
                .hasMessageContaining(firstFile.absolutePath)
                .hasMessageContaining(secondFile.absolutePath)
        }

        @Test
        fun `should export the unaffected projects when two features of one project share an id`() {
            // given
            val featureDestination = tempDir.resolve("feature-destination").toFile()
            writeProject("feature-project", "feature-project", featureDestination.absolutePath)
            val firstFile = writeFeature("feature-project", "first.yml", "duplicated-feature")
            val secondFile = writeFeature("feature-project", "second.yml", "duplicated-feature")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(featureDestination).doesNotExist()
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("duplicated-feature")
                .hasMessageContaining(firstFile.absolutePath)
                .hasMessageContaining(secondFile.absolutePath)
        }

        @Test
        fun `should report both the duplicate and the broken references of the same run`() {
            // given
            writeAgent("broken-agent", "base")
            writePrompt("broken-prompt", "missing-ruleset")
            writeProject("duplicated-a", "duplicated-project", tempDir.resolve("first-destination").toString())
            writeProject("duplicated-b", "duplicated-project", tempDir.resolve("second-destination").toString())

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("No rulesets match pattern 'missing-ruleset'")
                .hasMessageContaining("duplicated-project")
            assertThat((error as ExportFailedException).duplicates.map { it.id }).containsExactly("duplicated-project")
        }

        @Test
        fun `should export nothing when two rulesets share an id`() {
            // given
            // - rulesets are shared by every project, and a project with no ruleset filter deploys all of them, so exporting anything would silently ship content that lost one of its two sources
            writeRuleset("base", fileName = "duplicate-of-base.yml")
            writeAgent("good-agent", "base")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(DuplicateManifestIdException::class.java)
                .hasMessageContaining("base")
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should export nothing when two agents share an id`() {
            // given
            // - a dropped agent would leave every project silently missing an agent it never filtered out
            writeAgent("good-agent", "base")
            writeYaml(
                "agents/copy-of-good-agent.yml",
                "id: good-agent\ndescription: An agent\nrulesets:\n  - base\n" +
                    "persona: A persona\nprompt: An agent prompt\n",
            )

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(DuplicateManifestIdException::class.java)
            assertThat(destination).doesNotExist()
        }
    }

    @Nested
    inner class FailureReport {

        @Test
        fun `should count a manifest once when it fails for several adapters`() {
            // given
            // - every configured adapter exports the same broken agent, so it fails once per adapter
            val multiAdapterEngine = ToolsEngine(workspace, tools = listOf(ClaudeAdapter(), CursorAdapter()))
            writeAgent("broken-agent", "missing-ruleset")

            // when
            val error = runCatching { multiAdapterEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("Export failed for 1 manifest(s):")
            assertThat((error as ExportFailedException).failures).hasSize(2)
            assertThat(error.message)
                .contains("[test-project | CLAUDE | agent 'broken-agent']")
                .contains("[test-project | CURSOR | agent 'broken-agent']")
        }

        @Test
        fun `should count each broken manifest when several manifests fail`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")
            writePrompt("broken-prompt", "another-missing-ruleset")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).hasMessageContaining("Export failed for 2 manifest(s):")
        }
    }

    /**
     * A run that deploys nothing has to say so. The engine already refuses to be silent about a run that configures no tools, and a run that configures no deployment locations - or finds no manifest under them - is the same misconfiguration seen from the other side, most often a `config.local.yml` left on the retired key.
     */
    @Nested
    inner class NothingToDeploy {

        @Test
        fun `should warn when no deployment location is configured`() {
            // given
            val noDeployments = locations().copy(deployments = emptyList())

            // when
            engine.process(noDeployments)

            // then
            assertThat(warnings()).anyMatch { it.contains("locations.deployments") }
        }

        @Test
        fun `should warn when the configured locations hold no deployment manifest`() {
            // given
            // - the directory is configured and exists, it simply holds nothing the engine recognises
            val emptyDirectory = workspace.resolve("empty-deployments").also { it.mkdirs() }
            val emptyDeployments = locations().copy(deployments = listOf(emptyDirectory))

            // when
            engine.process(emptyDeployments)

            // then
            assertThat(warnings()).anyMatch { it.contains("no deployment manifest") }
        }

        @Test
        fun `should not warn when the run has something to deploy`() {
            // when
            engine.process(locations())

            // then
            assertThat(warnings()).noneMatch { it.contains("no deployment manifest") }
        }

        @Test
        fun `should not explain manifest layout when the manifests were found and dropped for an ambiguous id`() {
            // given
            // - the only manifests of this run collide, so they were found and rejected rather than never written; the error naming the collision already says what happened
            val collidingRoot = workspace.resolve("colliding")
            writeProject("alpha", "duplicated-project", root = "colliding")
            writeProject("beta", "duplicated-project", root = "colliding")
            val collidingOnly = locations().copy(deployments = listOf(collidingRoot))

            // when
            runCatching { engine.process(collidingOnly) }

            // then
            assertThat(warnings()).noneMatch { it.contains("no deployment manifest") }
            assertThat(errors()).anyMatch { it.contains("duplicated-project") }
        }
    }

    @Nested
    inner class UserDeployments {

        @Test
        fun `should deploy every artifact of a user deployment into the home of the run`() {
            // given
            writeAgent("global-agent", "base")
            writePrompt("global-prompt", "base")
            writeDirectorySkill("global-skill", "helper.md", companionFileExists = true)
            writeUserDeployment()

            // when
            engine.process(locations())

            // then
            val claudeDir = userHome.resolve(".claude")
            assertThat(claudeDir.resolve("CLAUDE.md").readText())
                .contains("# globals")
                .contains("A rule from base.")
            assertThat(claudeDir.resolve("agents/global-agent.md")).exists()
            assertThat(claudeDir.resolve("commands/global-prompt.md")).exists()
            assertThat(claudeDir.resolve("skills/global-skill/SKILL.md")).exists()
            assertThat(claudeDir.resolve("skills/global-skill/helper.md")).exists()
        }

        @Test
        fun `should deploy only the manifests the filters of the deployment select`() {
            // given
            writeAgent("selected-agent", "base")
            writeAgent("rejected-agent", "base")
            writeUserDeployment(agentFilter = listOf("selected-agent"))

            // when
            engine.process(locations())

            // then
            val agentsDir = userHome.resolve(".claude/agents")
            assertThat(agentsDir.resolve("selected-agent.md")).exists()
            assertThat(agentsDir.resolve("rejected-agent.md")).doesNotExist()
        }

        @Test
        fun `should deploy the projects and the user deployments of the same run`() {
            // given
            // - the project written in setUp deploys as usual while the user deployment reaches the home
            writeUserDeployment()

            // when
            engine.process(locations())

            // then
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(userHome.resolve(".claude/CLAUDE.md")).exists()
        }

        @Test
        fun `should deploy a user deployment only through the tools it declares`() {
            // given
            val multiAdapterEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ClaudeAdapter(), CodexAdapter()))
            writeUserDeployment(tools = listOf("claude"))

            // when
            multiAdapterEngine.process(locations())

            // then
            assertThat(userHome.resolve(".claude/CLAUDE.md")).exists()
            assertThat(userHome.resolve(".codex/AGENTS.md")).doesNotExist()
        }

        @Test
        fun `should deploy a user deployment through every configured tool when it declares none`() {
            // given
            val multiAdapterEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ClaudeAdapter(), CodexAdapter()))
            writeUserDeployment(tools = null)

            // when
            multiAdapterEngine.process(locations())

            // then
            assertThat(userHome.resolve(".claude/CLAUDE.md")).exists()
            assertThat(userHome.resolve(".codex/AGENTS.md")).exists()
        }

        @Test
        fun `should warn in the spelling of the manifest when a declared tool is not configured`() {
            // given
            // - the run configures Claude alone, so the Codex the manifest declares cannot be honoured
            writeUserDeployment(tools = listOf("claude", "codex"))

            // when
            engine.process(locations())

            // then
            assertThat(userHome.resolve(".claude/CLAUDE.md")).exists()
            assertThat(warnings()).anyMatch { it.contains("globals") && it.contains("codex") }
            assertThat(warnings()).noneMatch { it.contains("CODEX") }
        }

        @Test
        fun `should deploy a user deployment through no tool when it declares an empty tool list`() {
            // given
            writeUserDeployment(tools = emptyList())

            // when
            engine.process(locations())

            // then
            assertThat(userHome).doesNotExist()
            assertThat(warnings()).anyMatch { it.contains("globals") && it.contains("not exported") }
        }

        @Test
        fun `should warn and deploy nothing for a configured tool that has no user scope`() {
            // given
            // - Windsurf builds projects in this run but has no per-user layout, which must not pass in silence
            val windsurfEngine = ToolsEngine(workspace, userHome = userHome, tools = listOf(WindsurfAdapter()))
            writeUserDeployment(tools = listOf("windsurf"))

            // when
            windsurfEngine.process(locations())

            // then
            // - nothing at all is written for a tool the engine has no user-scope layout for
            assertThat(userHome).doesNotExist()
            assertThat(warnings()).anyMatch { it.contains("globals") && it.contains("no user-scope layout") }
        }

        @Test
        fun `should deploy the remaining artifacts when one artifact of a user deployment fails`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")
            writePrompt("good-prompt", "base")
            writeUserDeployment()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(userHome.resolve(".claude/commands/good-prompt.md")).exists()
            assertThat(userHome.resolve(".claude/agents/broken-agent.md")).doesNotExist()
            // - the failure is reported against the deployment that carries it, beside the one of the project
            assertThat((error as ExportFailedException).failures.map { it.deploymentId to it.manifest })
                .contains("globals" to "agent 'broken-agent'")
        }

        @Test
        fun `should deploy the unaffected user deployment when another one is broken`() {
            // given
            // - the two deploy through different tools, so each owns its own instructions file and the assertion below does not depend on which of them the loader happened to walk last
            val multiAdapterEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ClaudeAdapter(), CodexAdapter()))
            writeAgent("broken-agent", "missing-ruleset")
            writePrompt("good-prompt", "base")
            writeUserDeployment(id = "with-agents", tools = listOf("claude"))
            writeUserDeployment(
                id = "with-prompts",
                directoryName = "second",
                tools = listOf("codex"),
                agentFilter = emptyList(),
            )

            // when
            val error = runCatching { multiAdapterEngine.process(locations()) }.exceptionOrNull()

            // then
            // - an artifact only the second deployment produces, so reaching it is what is actually proven
            assertThat(userHome.resolve(".codex/skills/prompt-good-prompt/SKILL.md")).exists()
            assertThat(error).isInstanceOf(ExportFailedException::class.java)
        }

        @Test
        fun `should deploy neither instructions file when two deployments contend for one`() {
            // given
            // - one tool has one instructions file, so two deployments claiming it is an ambiguity with no winner
            writePrompt("good-prompt", "base")
            writeUserDeployment(id = "personal", agentFilter = emptyList())
            writeUserDeployment(id = "work", directoryName = "work", agentFilter = emptyList())

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            // - neither manifest is picked as the winner, so the file the two contend for is not written at all
            assertThat(userHome.resolve(".claude/CLAUDE.md")).doesNotExist()
            // - the artifacts they do not contend for are still deployed
            assertThat(userHome.resolve(".claude/commands/good-prompt.md")).exists()
            // - and the run fails, naming both manifests and the path
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("personal")
                .hasMessageContaining("work")
            assertThat(errors()).anyMatch {
                it.contains("personal") && it.contains("work") && it.contains("CLAUDE.md")
            }
        }

        @Test
        fun `should leave an existing instructions file untouched when two deployments contend for it`() {
            // given
            val instructions = userHome.resolve(".claude/CLAUDE.md")
            instructions.parentFile.mkdirs()
            instructions.writeText("From an earlier deploy.\n")
            writeUserDeployment(id = "personal", agentFilter = emptyList())
            writeUserDeployment(id = "work", directoryName = "work", agentFilter = emptyList())

            // when
            runCatching { engine.process(locations()) }

            // then
            assertThat(instructions).hasContent("From an earlier deploy.\n")
        }

        @Test
        fun `should warn naming the absolute path when an instructions file is overwritten`() {
            // given
            val instructions = userHome.resolve(".claude/CLAUDE.md")
            instructions.parentFile.mkdirs()
            instructions.writeText("From an earlier deploy.\n")
            writeUserDeployment()

            // when
            engine.process(locations())

            // then
            assertThat(warnings()).anyMatch { it.contains(instructions.absolutePath) }
        }

        @Test
        fun `should name a tool without a user scope in the spelling a manifest writes it`() {
            // given
            val windsurfEngine = ToolsEngine(workspace, userHome = userHome, tools = listOf(WindsurfAdapter()))
            writeUserDeployment(tools = listOf("windsurf"))

            // when
            windsurfEngine.process(locations())

            // then
            // - an author greps their own YAML for 'windsurf', never for the Kotlin constant
            assertThat(warnings()).anyMatch { it.contains("globals") && it.contains("windsurf") }
            assertThat(warnings()).noneMatch { it.contains("WINDSURF") }
        }

        @Test
        fun `should announce no home when no configured tool has a user scope`() {
            // given
            // - the manifest selects a tool the engine has no user-scope layout for, so nothing is written at all
            val windsurfEngine = ToolsEngine(workspace, userHome = userHome, tools = listOf(WindsurfAdapter()))
            writeUserDeployment(tools = listOf("windsurf"))

            // when
            windsurfEngine.process(locations())

            // then
            assertThat(userHome).doesNotExist()
            assertThat(infos()).noneMatch { it.contains("Deploying the user scope") }
            assertThat(infos()).noneMatch { it.contains("created by this deploy") }
        }

        @Test
        fun `should announce no home when every deployment only contends for the instructions file`() {
            // given
            // - two deployments claiming one instructions file and carrying nothing else write nothing between them
            writeUserDeployment(id = "personal", agentFilter = emptyList(), promptFilter = emptyList())
            writeUserDeployment(id = "work", directoryName = "work", agentFilter = emptyList(), promptFilter = emptyList())

            // when
            runCatching { engine.process(locations()) }

            // then
            assertThat(userHome).doesNotExist()
            assertThat(infos()).noneMatch { it.contains("Deploying the user scope") }
        }

        @Test
        fun `should log the absolute home before deploying into it`() {
            // given
            writeUserDeployment()

            // when
            engine.process(locations())

            // then
            assertThat(infos()).anyMatch { it.contains(userHome.absolutePath) }
        }

        @Test
        fun `should write nothing outside the home when a skill id climbs out of its directory`() {
            // given
            // - replace is false here, which is the default: the write is what has to be contained, not only the delete
            writeYaml(
                "skills/escaping/skill.yml",
                "id: ../../escaped\ndescription: A skill\nsections:\n  - text: Some skill content\n",
            )
            writeUserDeployment()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            // - the manifest never loads, so no adapter of any scope is ever handed the id
            assertThat(error).isInstanceOf(ManifestLoadingException::class.java)
            assertThat(
                tempDir
                    .toFile()
                    .walkTopDown()
                    .filter { it.name == "SKILL.md" }
                    .toList(),
            ).isEmpty()
            assertThat(userHome).doesNotExist()
        }

        @Test
        fun `should deploy no user deployment at all when two of them share an id`() {
            // given
            writeUserDeployment(id = "duplicated", directoryName = "first")
            writeUserDeployment(id = "duplicated", directoryName = "second")

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(userHome).doesNotExist()
            // - the project of the same run is untouched by the collision, and the run still fails
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("duplicated")
        }
    }

    /**
     * A skill manifest pointing at a plain SKILL.md skill is deployed with the content of that folder, and the folder is found through the same variables of the run a deploy directory is resolved with.
     */
    @Nested
    inner class SkillSources {

        @Test
        fun `should deploy a pointer skill with the body and companion files of its source folder`() {
            // given
            // - the plain skill lives in another repository, reached through a variable of the run
            val projectsFolder = tempDir.resolve("projects").toFile()
            val sourceDir = writePlainSkill(projectsFolder.resolve("mcp/skills/jira-ticket"), "jira-ticket")
            sourceDir.resolve("templates").mkdirs()
            sourceDir.resolve("templates/task.txt").writeText("Task template.\n")
            writePointerSkill("jira-ticket", "\${PROJECTS_FOLDER}/mcp/skills/jira-ticket")
            val variableEngine = ToolsEngine(
                workspace,
                variables = VariableResolver(mapOf("PROJECTS_FOLDER" to projectsFolder.absolutePath), emptyEnvironment),
                userHome = userHome,
                tools = listOf(ClaudeAdapter()),
            )

            // when
            variableEngine.process(locations())

            // then
            val skillDir = destination.resolve(".claude/skills/jira-ticket")
            assertThat(skillDir.resolve("SKILL.md").readText()).isEqualTo(
                "---\nname: jira-ticket\ndescription: \"The jira-ticket skill\"\n---\n\n# jira-ticket\n\nPlain body.\n",
            )
            assertThat(skillDir.resolve("templates/task.txt")).hasContent("Task template.")
        }

        @Test
        fun `should fail before writing anything naming the manifest, the source and the target when the skill directory is a link to the source folder`() {
            // given
            // - a generated skill directory left behind as a link to the very folder the skill is read from
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("projects/mcp/skills/jira-ticket").toFile())
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            writeAgent("good-agent", "base")
            val target = destination.resolve(".claude/skills/jira-ticket")
            target.parentFile.mkdirs()
            Files.createSymbolicLink(target.toPath(), sourceDir.toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining(target.absolutePath)
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
            assertThat(agentFile("good-agent")).doesNotExist()
        }

        @ParameterizedTest
        @CsvSource(
            // - the generated skill directory is the source folder itself
            ".claude/skills/jira-ticket",
            // - the generated skill directory lies inside the source folder
            ".claude/skills",
            // - the generated skill directory contains the source folder
            ".claude/skills/jira-ticket/vendor",
        )
        fun `should leave the source folder untouched when a replacing deploy would write its skill over it`(
            sourceUnderDestination: String,
        ) {
            // given
            // - the project replaces its .claude directory, which would delete the source folder along with it
            writeProject(replace = true)
            val sourceDir = writeSourceFolderWithTemplate(destination.resolve(sourceUnderDestination))
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining(destination.resolve(".claude/skills/jira-ticket").absolutePath)
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should fail before writing anything when the skill directory of the user scope is a link to the source folder`() {
            // given
            // - the home keeps the plain skill installed by hand as a link to its checkout, and a user deployment selects the pointer skill as well
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("projects/mcp/skills/jira-ticket").toFile())
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            writeUserDeployment(replace = true)
            val target = userHome.resolve(".claude/skills/jira-ticket")
            target.parentFile.mkdirs()
            Files.createSymbolicLink(target.toPath(), sourceDir.toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining(target.absolutePath)
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(Files.isSymbolicLink(target.toPath())).isTrue()
            // - not even the project of the same run, which is exported before the home, was written
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
            assertThat(userHome.resolve(".claude/CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should fail before writing anything when a replacing deploy would delete the source folder of a skill it does not select`() {
            // given
            // - the plain skill sits where Claude Code looks for the skills of a project, and the project replaces its .claude directory without selecting it
            writeProject(replace = true, skillFilter = emptyList())
            val sourceDir = writeSourceFolderWithTemplate(destination.resolve(".claude/skills/jira-ticket"))
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining("'${destination.resolve(".claude").absolutePath}'")
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should fail before writing anything when a replacing deploy would delete a source folder beside the skill directory it writes`() {
            // given
            // - the source folder of the selected skill sits in the replaced .claude directory, but not where the skill is written
            writeProject(replace = true)
            val sourceDir = writeSourceFolderWithTemplate(destination.resolve(".claude/vendor/jira-ticket"))
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining("'${destination.resolve(".claude").absolutePath}'")
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should fail before writing anything when the replaced directory holds a link to a folder of source folders`() {
            // given
            // - the skills folder of the project links to the shared checkout of plain skills, which the project does not select
            writeProject(replace = true, skillFilter = emptyList())
            val skillsFolder = tempDir.resolve("projects/mcp/skills").toFile()
            val sourceDir = writeSourceFolderWithTemplate(skillsFolder.resolve("jira-ticket"))
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val link = destination.resolve(".claude/skills")
            link.parentFile.mkdirs()
            Files.createSymbolicLink(link.toPath(), skillsFolder.toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining("'${link.absolutePath}'")
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
        }

        @ParameterizedTest
        @CsvSource("true", "false")
        fun `should fail before writing anything when a folder inside the generated skill directory links into the source folder`(
            replace: Boolean,
        ) {
            // given
            // - the generated skill directory is real, but its templates folder links back to the templates of the source
            writeProject(replace = replace)
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("projects/mcp/skills/jira-ticket").toFile())
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val link = destination.resolve(".claude/skills/jira-ticket/templates")
            link.parentFile.mkdirs()
            Files.createSymbolicLink(link.toPath(), sourceDir.resolve("templates").toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining("'${link.absolutePath}'")
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should fail before writing anything when another skill would be written through a link into the source folder`() {
            // given
            // - a plain skill installed by hand under another name links the directory of the in-repository skill 'other' to the source folder of 'jira-ticket'
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("projects/mcp/skills/jira-ticket").toFile())
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            writeTextSkill("other")
            val link = destination.resolve(".claude/skills/other")
            link.parentFile.mkdirs()
            Files.createSymbolicLink(link.toPath(), sourceDir.toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining("Skill 'jira-ticket' (${manifest.absolutePath})")
                .hasMessageContaining("the skill 'other'")
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining("'${link.absolutePath}'")
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should fail before writing anything when the deploy directory climbs out of a link onto the source folder`() {
            // given
            // - 'lnk' leads to 'deep/inner', so the deploy directory 'lnk/../proj' is 'deep/proj' on disk, which holds the source folder where the skill is written
            val workspaceRoot = tempDir.resolve("j").toFile()
            workspaceRoot.resolve("deep/inner").mkdirs()
            Files.createSymbolicLink(workspaceRoot.resolve("lnk").toPath(), workspaceRoot.resolve("deep/inner").toPath())
            writeProject(deployDirectory = "${workspaceRoot.absolutePath}/lnk/../proj")
            val sourceDir = writeSourceFolderWithTemplate(workspaceRoot.resolve("deep/proj/.claude/skills/jira-ticket"))
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(workspaceRoot.resolve("deep/proj/CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should replace the directory and keep what a link inside it leads to when that holds no source folder`() {
            // given
            writeProject(replace = true)
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("projects/mcp/skills/jira-ticket").toFile())
            writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val elsewhere = tempDir.resolve("elsewhere/notes.md").toFile()
            elsewhere.parentFile.mkdirs()
            elsewhere.writeText("Kept by hand.\n")
            val link = destination.resolve(".claude/vendor")
            link.parentFile.mkdirs()
            Files.createSymbolicLink(link.toPath(), elsewhere.parentFile.toPath())

            // when
            engine.process(locations())

            // then
            assertThat(elsewhere).hasContent("Kept by hand.\n")
            assertThat(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)).isFalse()
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(destination.resolve(".claude/skills/jira-ticket/SKILL.md")).exists()
        }

        @Test
        fun `should fail saying it would only unlink it when the replaced directory itself is a link to a folder holding a source folder`() {
            // given
            // - .claude is a link to a checkout that holds the source folder; the delete of a replacing deploy would only unlink .claude
            // - the project selects no skill, so the replaced directory is the only path that overlaps the source folder
            writeProject(replace = true, skillFilter = emptyList())
            val checkout = tempDir.resolve("checkout").toFile()
            val sourceDir = writeSourceFolderWithTemplate(checkout.resolve("skills/jira-ticket"))
            writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val link = destination.resolve(".claude")
            destination.mkdirs()
            Files.createSymbolicLink(link.toPath(), checkout.toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining("claude would delete '${link.absolutePath}' for project 'test-project' to replace it, and '${link.absolutePath}' is itself a link to '${checkout.toPath().toRealPath()}', which contains that folder.")
                .hasMessageNotContaining("Deploying would overwrite or delete the files of the source.")
            assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
        }

        @ParameterizedTest
        @CsvSource(
            // - Claude replaces its whole tool directory, which is the link here
            "CLAUDE, .claude, .claude/skills/jira-ticket, CLAUDE.md, true",
            "CLAUDE, .claude, .claude/skills/jira-ticket, CLAUDE.md, false",
            // - Codex replaces the folders it generates below a .codex it keeps for config.toml; the skills folder, which the skill is written into, is the link here
            "CODEX, .codex/skills, .codex/skills/skill-jira-ticket, AGENTS.md, true",
            "CODEX, .codex/skills, .codex/skills/skill-jira-ticket, AGENTS.md, false",
            // - GitHub Copilot replaces three folders below a .github it keeps; the prompts folder, which the skill is written into, is the link here
            "GITHUB_COPILOT, .github/prompts, .github/prompts/skill-jira-ticket, .github/copilot-instructions.md, true",
            "GITHUB_COPILOT, .github/prompts, .github/prompts/skill-jira-ticket, .github/copilot-instructions.md, false",
        )
        fun `should fail before writing anything when the replaced directory is a link leading elsewhere that sits inside the source folder`(
            toolType: ToolType,
            linkPath: String,
            skillPath: String,
            instructionsPath: String,
            dryRun: Boolean,
        ) {
            // given
            // - the project deploys into a folder inside the source folder, and its replaced directory is a link to a folder elsewhere
            // - the deploy would unlink that link, which is an entry of the source, and then write the skill into the source
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("F-src/jira-ticket").toFile())
            writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val project = sourceDir.resolve("proj")
            writeProject(deployDirectory = project.absolutePath, replace = true)
            val elsewhere = tempDir.resolve("F-elsewhere").toFile()
            elsewhere.mkdirs()
            val link = project.resolve(linkPath)
            link.parentFile.mkdirs()
            Files.createSymbolicLink(link.toPath(), elsewhere.toPath())
            val sourceBefore = sourceDir.contentSnapshot()
            val runEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ToolFactory.create(toolType, dryRun)), dryRun = dryRun)
            val tool = toolType.serialName
            val advice = "Deploying would overwrite or delete the files of the source. Move the directory that project 'test-project' deploys to out of the source folder, or turn off 'replace' for that deployment."

            // when
            val error = runCatching { runEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining("$tool would delete '${link.absolutePath}' for project 'test-project' to replace it, which lies inside that folder once the link '${link.absolutePath}' is removed. $advice")
                .hasMessageContaining("$tool would write the skill 'jira-ticket' for project 'test-project' to '${project.resolve(skillPath).absolutePath}', which lies inside that folder once the link '${link.absolutePath}' is removed. $advice")
            assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
            assertThat(elsewhere.list()).isEmpty()
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(project.resolve(instructionsPath)).doesNotExist()
        }

        @Test
        fun `should fail with one line naming the link below the replaced directory when a skill directory installed by hand links to the source folder`() {
            // given
            // - .claude is a real folder the project replaces, and the plain skill is installed by hand in it as a link to its checkout
            // - the delete unlinks that link before the skill is written, so the write lands in a fresh folder and only the link is reported
            writeProject(replace = true)
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("projects/mcp/skills/jira-ticket").toFile())
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val replaced = destination.resolve(".claude")
            val link = replaced.resolve("skills/jira-ticket")
            link.parentFile.mkdirs()
            Files.createSymbolicLink(link.toPath(), sourceDir.toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessage(
                    "Refusing to deploy: 1 path(s) the run would write or delete overlap the source folder of a pointer skill; nothing was written:\n" +
                        "  - Skill 'jira-ticket' (${manifest.absolutePath}) is read from the source folder '${sourceDir.absolutePath}', but claude would delete '${replaced.absolutePath}' for project 'test-project' to replace it, " +
                        "and the link '${link.absolutePath}' below the directory to be replaced leads to '${sourceDir.toPath().toRealPath()}', which is that folder. " +
                        "The deploy would only unlink it, but it refuses while a link below a replaced directory leads into a source folder. " +
                        "Remove the link '${link.absolutePath}', or turn off 'replace' for that deployment.",
                )
            assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should compare the skill of another project through a replaced link it does not own and fail before writing anything`() {
            // given
            // - two projects deploy into the same directory, whose .claude links to a shared folder, and 'skills' in that folder links to the folder holding the source folder
            // - 'test-project' replaces .claude and selects no skill; 'second' keeps it and writes the skill through both links, so its path is compared through them
            writeProject(replace = true, skillFilter = emptyList())
            writeProject(directoryName = "second", id = "second", skillFilter = listOf("jira-ticket"))
            val sourceParent = tempDir.resolve("shared-src").toFile()
            val sourceDir = writeSourceFolderWithTemplate(sourceParent.resolve("jira-ticket"))
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val shared = tempDir.resolve("shared").toFile()
            shared.mkdirs()
            Files.createSymbolicLink(shared.resolve("skills").toPath(), sourceParent.toPath())
            val link = destination.resolve(".claude")
            destination.mkdirs()
            Files.createSymbolicLink(link.toPath(), shared.toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(SkillSourceOverlapException::class.java)
            assertThat((error as SkillSourceOverlapException).overlaps.map { it.message }).containsExactly(
                "Skill 'jira-ticket' (${manifest.absolutePath}) is read from the source folder '${sourceDir.absolutePath}', but claude would write the skill 'jira-ticket' for project 'second' to '${link.resolve("skills/jira-ticket").absolutePath}', " +
                    "which is that folder once links are resolved. Deploying would overwrite or delete the files of the source. " +
                    "Remove the link or folder that leads there, or deselect the skill 'jira-ticket' for that deployment.",
            )
            assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
        }

        @Test
        fun `should name where a replaced link leading to nothing would lead when that lies inside the source folder`() {
            // given
            // - .claude is a link to a folder of the source that does not exist, so it leads to nothing yet
            writeProject(replace = true, skillFilter = emptyList())
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("checkout/skills/jira-ticket").toFile())
            writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val link = destination.resolve(".claude")
            destination.mkdirs()
            Files.createSymbolicLink(link.toPath(), sourceDir.resolve("missing").toPath())
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining("and '${link.absolutePath}' is itself a link to '${sourceDir.toPath().toRealPath().resolve("missing")}', which lies inside that folder.")
            assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
        }

        @Test
        fun `should fail before writing anything when a replacing user deployment would delete a prompt directory holding a source folder`() {
            // given
            // - Codex keeps prompts as skill directories in the home, and a replacing user deploy deletes each one before writing it again
            val codexEngine = ToolsEngine(workspace, userHome = userHome, tools = listOf(CodexAdapter()))
            writePrompt("review", "base")
            writeUserDeployment(tools = listOf("codex"), replace = true, promptFilter = listOf("review"))
            val promptDir = userHome.resolve(".codex/skills/prompt-review")
            val sourceDir = writeSourceFolderWithTemplate(promptDir.resolve("vendor/jira-ticket"))
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val sourceBefore = sourceDir.contentSnapshot()

            // when
            val error = runCatching { codexEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining("'${promptDir.absolutePath}'")
                .hasMessageContaining("user deployment 'globals'")
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(userHome.resolve(".codex/AGENTS.md")).doesNotExist()
        }

        @Test
        fun `should write nothing into the source folder of another pointer skill that a chain of links leads to`() {
            // given
            // - the project selects only 'probe'; its generated templates folder links to 'shared', and 'shared/sub' links to the source folder of 'second'
            writeProject(skillFilter = listOf("probe"))
            val probeSource = writePlainSkill(tempDir.resolve("src/probe").toFile(), "probe")
            probeSource.resolve("templates/sub").mkdirs()
            probeSource.resolve("templates/sub/s.txt").writeText("Probe template.\n")
            writePointerSkill("probe", probeSource.absolutePath)
            val secondSource = writePlainSkill(tempDir.resolve("src/second").toFile(), "second")
            writePointerSkill("second", secondSource.absolutePath)
            val shared = tempDir.resolve("shared").toFile()
            shared.mkdirs()
            Files.createSymbolicLink(shared.resolve("sub").toPath(), secondSource.toPath())
            val templates = destination.resolve(".claude/skills/probe/templates")
            templates.parentFile.mkdirs()
            Files.createSymbolicLink(templates.toPath(), shared.toPath())
            val secondBefore = secondSource.contentSnapshot()

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("skill 'probe'")
                .hasMessageContaining(secondSource.absolutePath)
            assertThat(secondSource.contentSnapshot()).isEqualTo(secondBefore)
            assertThat(secondSource.resolve("s.txt")).doesNotExist()
        }

        @Test
        fun `should fail the run naming the manifest when the source folder does not exist`() {
            // given
            val missing = tempDir.resolve("projects/missing").toFile()
            val manifest = writePointerSkill("jira-ticket", missing.absolutePath)

            // when
            val error = runCatching { engine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ManifestLoadingException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(missing.absolutePath)
            assertThat(destination).doesNotExist()
        }
    }

    @Nested
    inner class ReplacedDirectories {

        @ParameterizedTest
        @CsvSource("true", "false")
        fun `should fail before deleting anything naming the project, the tool and the folder when a replaced directory holds a folder it cannot read`(
            dryRun: Boolean,
        ) {
            // given
            // - deleting the replaced .claude directory would stop at the locked folder after having deleted what came before it
            writeProject(replace = true)
            val earlier = destination.resolve(".claude/old.md")
            earlier.parentFile.mkdirs()
            earlier.writeText("Written by an earlier deploy.\n")
            val locked = destination.resolve(".claude/locked")
            locked.mkdirs()
            val runEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ToolFactory.create(ToolType.CLAUDE, dryRun)), dryRun = dryRun)
            locked.setReadable(false)
            try {
                // - a superuser reads the folder regardless of its permissions, so the failure cannot be provoked there
                assumeFalse(locked.canRead())

                // when
                val error = runCatching { runEngine.process(locations()) }.exceptionOrNull()

                // then
                assertThat(error)
                    .isInstanceOf(UnreadableReplacedFolderException::class.java)
                    .hasMessageContaining("claude would delete '${destination.resolve(".claude").absolutePath}' for project 'test-project' to replace it")
                    .hasMessageContaining("'${locked.absolutePath}'")
                assertThat(earlier).hasContent("Written by an earlier deploy.\n")
                assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
            } finally {
                locked.setReadable(true)
            }
        }

        @Test
        fun `should fail naming the project, the tool and the path when a replaced directory cannot be deleted`() {
            // given
            // - the folder can be read, so the check before the run passes, but the file in it cannot be removed
            writeProject(replace = true)
            val readOnly = destination.resolve(".claude/read-only")
            readOnly.mkdirs()
            val kept = readOnly.resolve("kept.md")
            kept.writeText("Cannot be deleted.\n")
            readOnly.setWritable(false)
            try {
                // - a superuser deletes regardless of permissions, so the failure cannot be provoked there
                assumeFalse(readOnly.canWrite())

                // when
                val error = runCatching { engine.process(locations()) }.exceptionOrNull()

                // then
                assertThat(error)
                    .isInstanceOf(ReplaceFailedException::class.java)
                    .hasMessage(
                        "Cannot replace the claude files of project 'test-project' in '${destination.absolutePath}': deleting '${kept.absolutePath}' failed (AccessDeniedException). " +
                            "The run stopped here; make that path deletable and deploy again.",
                    )
            } finally {
                readOnly.setWritable(true)
            }
        }

        @Test
        fun `should fail naming the user deployment, the tool and the path when a replaced skill directory in the home cannot be deleted`() {
            // given
            // - the user scope replaces the directory of each skill it deploys, and the file below it cannot be removed
            writeTextSkill("plain")
            writeUserDeployment(replace = true)
            val readOnly = userHome.resolve(".claude/skills/plain/ro")
            readOnly.mkdirs()
            val kept = readOnly.resolve("a.md")
            kept.writeText("Cannot be deleted.\n")
            readOnly.setWritable(false)
            try {
                // - a superuser deletes regardless of permissions, so the failure cannot be provoked there
                assumeFalse(readOnly.canWrite())

                // when
                val error = runCatching { engine.process(locations()) }.exceptionOrNull()

                // then
                assertThat(error)
                    .isInstanceOf(ReplaceFailedException::class.java)
                    .hasMessage(
                        "Cannot replace the claude files of user deployment 'globals' under '${userHome.absolutePath}': deleting '${kept.absolutePath}' failed (AccessDeniedException). " +
                            "The run stopped here; make that path deletable and deploy again.",
                    )
                assertThat(kept).hasContent("Cannot be deleted.\n")
            } finally {
                readOnly.setWritable(true)
            }
        }

        @ParameterizedTest
        @CsvSource("true", "false")
        fun `should replace a replaced directory that is itself a link without reading the folder it leads to`(
            dryRun: Boolean,
        ) {
            // given
            // - .claude links into a shared folder holding a folder nobody can read; the delete only unlinks .claude and never looks behind it
            writeProject(replace = true)
            val shared = tempDir.resolve("shared").toFile()
            val locked = shared.resolve("locked")
            locked.mkdirs()
            shared.resolve("notes.md").writeText("Kept by hand.\n")
            val link = destination.resolve(".claude")
            destination.mkdirs()
            Files.createSymbolicLink(link.toPath(), shared.toPath())
            val runEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ToolFactory.create(ToolType.CLAUDE, dryRun)), dryRun = dryRun)
            locked.setReadable(false)
            locked.setExecutable(false)
            locked.setWritable(false)
            try {
                // - a superuser reads the folder regardless of its permissions, so the case the check must not report cannot be set up there
                assumeFalse(locked.canRead())

                // when
                runEngine.process(locations())

                // then
                assertThat(Files.isSymbolicLink(link.toPath())).isEqualTo(dryRun)
                assertThat(shared.list()).containsExactlyInAnyOrder("locked", "notes.md")
                assertThat(shared.resolve("notes.md")).hasContent("Kept by hand.\n")
                assertThat(destination.resolve("CLAUDE.md").exists()).isEqualTo(!dryRun)
            } finally {
                locked.setWritable(true)
                locked.setExecutable(true)
                locked.setReadable(true)
            }
        }

        @ParameterizedTest
        @CsvSource(
            "CLAUDE, .claude, CLAUDE.md, true",
            "CLAUDE, .claude, CLAUDE.md, false",
            // - Codex replaces the folders it generates below a .codex it keeps for config.toml; the skills folder is the link here
            "CODEX, .codex/skills, AGENTS.md, true",
            "CODEX, .codex/skills, AGENTS.md, false",
            // - GitHub Copilot replaces three folders below a .github it keeps; the prompts folder is the link here
            "GITHUB_COPILOT, .github/prompts, .github/copilot-instructions.md, true",
            "GITHUB_COPILOT, .github/prompts, .github/copilot-instructions.md, false",
        )
        fun `should replace a replaced directory that is itself a link without comparing the links in the folder it leads to`(
            toolType: ToolType,
            linkPath: String,
            instructionsPath: String,
            dryRun: Boolean,
        ) {
            // given
            // - the replaced directory links to a shared folder, and 'tools' in that folder links to the folder holding a source folder; the project selects no skill
            // - the deploy only unlinks the replaced directory and never touches 'tools', so only where that link sits and where it leads are compared
            writeProject(replace = true, skillFilter = emptyList())
            val sourceParent = tempDir.resolve("G-src").toFile()
            val sourceDir = writeSourceFolderWithTemplate(sourceParent.resolve("jira-ticket"))
            writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val shared = tempDir.resolve("G-shared").toFile()
            shared.mkdirs()
            val tools = shared.resolve("tools")
            Files.createSymbolicLink(tools.toPath(), sourceParent.toPath())
            val link = destination.resolve(linkPath)
            link.parentFile.mkdirs()
            Files.createSymbolicLink(link.toPath(), shared.toPath())
            val sourceBefore = sourceDir.contentSnapshot()
            val runEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ToolFactory.create(toolType, dryRun)), dryRun = dryRun)

            // when
            runEngine.process(locations())

            // then
            assertThat(Files.isSymbolicLink(link.toPath())).isEqualTo(dryRun)
            assertThat(shared.list()).containsExactly("tools")
            assertThat(Files.readSymbolicLink(tools.toPath())).isEqualTo(sourceParent.toPath())
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
            assertThat(destination.resolve(instructionsPath).exists()).isEqualTo(!dryRun)
        }

        @ParameterizedTest
        @CsvSource("true", "false")
        fun `should unlink a replaced skill directory in the home that is a link leading inside the skills folder and keep what it leads to`(
            dryRun: Boolean,
        ) {
            // given
            // - the skill 'plain' is installed in the home as a link to its development copy 'plain-dev' beside it, whose 'lib' links to the folder holding a source folder
            // - the deploy only unlinks 'plain' and never touches 'plain-dev', so the link in it is not compared
            writeTextSkill("plain")
            writeUserDeployment(replace = true)
            val sourceParent = tempDir.resolve("U-src").toFile()
            val sourceDir = writeSourceFolderWithTemplate(sourceParent.resolve("jira-ticket"))
            writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val skillsFolder = userHome.resolve(".claude/skills")
            val development = skillsFolder.resolve("plain-dev")
            development.mkdirs()
            development.resolve("SKILL.md").writeText("Development copy.\n")
            Files.createSymbolicLink(development.resolve("lib").toPath(), sourceParent.toPath())
            val link = skillsFolder.resolve("plain")
            Files.createSymbolicLink(link.toPath(), development.toPath())
            val sourceBefore = sourceDir.contentSnapshot()
            val runEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ToolFactory.create(ToolType.CLAUDE, dryRun)), dryRun = dryRun)

            // when
            runEngine.process(locations())

            // then
            assertThat(Files.isSymbolicLink(link.toPath())).isEqualTo(dryRun)
            // - a deploy writes the skill into a fresh folder where the link was, a dry run leaves the link leading to the development copy
            assertThat(link.resolve("SKILL.md").readText()).contains(if (dryRun) "Development copy." else "Some skill content")
            assertThat(development.list()).containsExactlyInAnyOrder("SKILL.md", "lib")
            assertThat(development.resolve("SKILL.md")).hasContent("Development copy.\n")
            assertThat(Files.readSymbolicLink(development.resolve("lib").toPath())).isEqualTo(sourceParent.toPath())
            assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
        }

        @ParameterizedTest
        @CsvSource("true", "false")
        fun `should refuse a replaced skill directory in the home that is a link leading outside the skills folder, advising to remove the link`(
            dryRun: Boolean,
        ) {
            // given
            // - the skill 'plain' is installed in the home as a link to a checkout outside the skills folder, and the user deployment replaces it
            writeTextSkill("plain")
            writeUserDeployment(replace = true)
            val checkout = tempDir.resolve("checkout/plain").toFile()
            checkout.mkdirs()
            checkout.resolve("SKILL.md").writeText("Installed by hand.\n")
            val skillsFolder = userHome.resolve(".claude/skills")
            skillsFolder.mkdirs()
            val link = skillsFolder.resolve("plain")
            Files.createSymbolicLink(link.toPath(), checkout.toPath())
            val runEngine =
                ToolsEngine(workspace, userHome = userHome, tools = listOf(ToolFactory.create(ToolType.CLAUDE, dryRun)), dryRun = dryRun)

            // when
            val error = runCatching { runEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ArtifactPathException::class.java)
                .hasMessage(
                    "Refusing to replace '${link.absolutePath}' for 'skill 'plain'': it is a symbolic link that leads to '${checkout.canonicalPath}', which is not inside '${skillsFolder.absolutePath}'. " +
                        "Remove the link, or turn off 'replace' for that deployment.",
                )
            assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
            assertThat(checkout.resolve("SKILL.md")).hasContent("Installed by hand.\n")
            // - refused before any write: neither the project, which a run exports first, nor the instructions file of the home was written
            assertThat(destination.resolve("CLAUDE.md")).doesNotExist()
            assertThat(userHome.resolve(".claude/CLAUDE.md")).doesNotExist()
        }

        @ParameterizedTest
        @CsvSource(
            "prompt, prompt-review, prompt 'review'",
            "agent, agent-review, agent 'review'",
        )
        fun `should refuse before writing anything a replaced prompt or agent directory of Codex in the home that is a link leading outside the skills folder`(
            kind: String,
            directoryName: String,
            describedBy: String,
        ) {
            // given
            // - Codex keeps prompts and agents as skill directories in the home; the one for 'review' is a link to a checkout outside the skills folder
            val codexEngine = ToolsEngine(workspace, userHome = userHome, tools = listOf(CodexAdapter()))
            if (kind == "prompt") writePrompt("review", "base") else writeAgent("review", "base")
            writeUserDeployment(tools = listOf("codex"), replace = true)
            val checkout = tempDir.resolve("checkout/review").toFile()
            checkout.mkdirs()
            val skillsFolder = userHome.resolve(".codex/skills")
            skillsFolder.mkdirs()
            val link = skillsFolder.resolve(directoryName)
            Files.createSymbolicLink(link.toPath(), checkout.toPath())

            // when
            val error = runCatching { codexEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ArtifactPathException::class.java)
                .hasMessage(
                    "Refusing to replace '${link.absolutePath}' for '$describedBy': it is a symbolic link that leads to '${checkout.canonicalPath}', which is not inside '${skillsFolder.absolutePath}'. " +
                        "Remove the link, or turn off 'replace' for that deployment.",
                )
            assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
            assertThat(checkout.list()).isEmpty()
            assertThat(destination.resolve("AGENTS.md")).doesNotExist()
            assertThat(userHome.resolve(".codex/AGENTS.md")).doesNotExist()
        }
    }

    /**
     * A dry run loads, filters and renders exactly what a deploy does and reports the same failures, while nothing on disk is created, deleted or modified. The adapters come from [ToolFactory] here, the way the CLI builds them, so that the engine flag and the adapters' sink are proven to act together.
     */
    @Nested
    inner class DryRun {

        private val sinkAppender = ListAppender<ILoggingEvent>()
        private lateinit var sinkLogger: Logger
        private lateinit var dryRunEngine: ToolsEngine

        @BeforeEach
        fun setUp() {
            dryRunEngine = ToolsEngine(
                workspace,
                userHome = userHome,
                tools = listOf(ToolFactory.create(ToolType.CLAUDE, dryRun = true)),
                dryRun = true,
            )
            sinkLogger = LoggerFactory.getLogger(DryRunArtifactSink::class.java) as Logger
            sinkAppender.start()
            sinkLogger.addAppender(sinkAppender)
        }

        @AfterEach
        fun tearDown() {
            sinkLogger.detachAppender(sinkAppender)
            sinkAppender.stop()
            sinkAppender.list.clear()
        }

        @Test
        fun `should write nothing when every manifest of a project resolves`() {
            // given
            writeAgent("good-agent", "base")
            writePrompt("good-prompt", "base")
            writeDirectorySkill("directory-skill", "helper.md", companionFileExists = true)

            // when
            dryRunEngine.process(locations())

            // then
            assertThat(destination).doesNotExist()
            // - and no artifact landed anywhere else under the temporary tree either
            assertThat(
                tempDir
                    .toFile()
                    .walkTopDown()
                    .filter { it.name == "CLAUDE.md" || it.name == "SKILL.md" }
                    .toList(),
            ).isEmpty()
        }

        @Test
        fun `should name the absolute target path of every artifact it would write`() {
            // given
            writeAgent("good-agent", "base")
            writeDirectorySkill("directory-skill", "helper.md", companionFileExists = true)

            // when
            dryRunEngine.process(locations())

            // then
            val skillDir = destination.resolve(".claude/skills/directory-skill")
            assertThat(sinkInfos()).anyMatch { it.contains(destination.resolve("CLAUDE.md").absolutePath) }
            assertThat(sinkInfos()).anyMatch { it.contains(agentFile("good-agent").absolutePath) }
            assertThat(sinkInfos()).anyMatch { it.contains(skillDir.resolve("SKILL.md").absolutePath) }
            assertThat(sinkInfos()).anyMatch { it.contains(skillDir.resolve("helper.md").absolutePath) }
        }

        @Test
        fun `should leave the artifacts of an earlier deploy untouched when the project replaces them`() {
            // given
            // - replace lets a deploy delete before it writes, which is the one step a dry run must skip outright
            writeProject(replace = true)
            val instructions = destination.resolve("CLAUDE.md")
            instructions.parentFile.mkdirs()
            instructions.writeText("From an earlier deploy.\n")
            val stale = agentFile("stale-agent")
            stale.parentFile.mkdirs()
            stale.writeText("Left behind by an earlier deploy.\n")

            // when
            dryRunEngine.process(locations())

            // then
            assertThat(instructions).hasContent("From an earlier deploy.\n")
            assertThat(stale).hasContent("Left behind by an earlier deploy.\n")
            // - the replacement is announced as what it would do, not as something that happened
            assertThat(warnings()).anyMatch { it.contains("test-project") && it.contains("Would replace") }
            assertThat(warnings()).noneMatch { it.contains("Replacing existing agentic files") }
        }

        @Test
        fun `should fail with the resolver message when an agent references an unknown ruleset`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")

            // when
            val error = runCatching { dryRunEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("No rulesets match pattern 'missing-ruleset'")
                .hasMessageContaining("required by agent 'broken-agent'")
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should fail when a skill declares a companion file that does not exist`() {
            // given
            writeDirectorySkill("directory-skill", "missing.md", companionFileExists = false)

            // when
            val error = runCatching { dryRunEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("missing.md")
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should fail naming the manifest when a pointer skill names a source folder without a SKILL md`() {
            // given
            val sourceDir = tempDir.resolve("projects/jira-ticket").toFile()
            sourceDir.mkdirs()
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)

            // when
            val error = runCatching { dryRunEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ManifestLoadingException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.resolve("SKILL.md").absolutePath)
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should fail naming the manifest, the source and the target when the skill directory is a link to the source folder`() {
            // given
            val sourceDir = writeSourceFolderWithTemplate(tempDir.resolve("projects/mcp/skills/jira-ticket").toFile())
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)
            val target = destination.resolve(".claude/skills/jira-ticket")
            target.parentFile.mkdirs()
            Files.createSymbolicLink(target.toPath(), sourceDir.toPath())

            // when
            val error = runCatching { dryRunEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining(target.absolutePath)
        }

        @Test
        fun `should fail naming the replaced directory when a replacing deploy would delete the source folder of a skill it does not select`() {
            // given
            writeProject(replace = true, skillFilter = emptyList())
            val sourceDir = writeSourceFolderWithTemplate(destination.resolve(".claude/skills/jira-ticket"))
            val manifest = writePointerSkill("jira-ticket", sourceDir.absolutePath)

            // when
            val error = runCatching { dryRunEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillSourceOverlapException::class.java)
                .hasMessageContaining(manifest.absolutePath)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining("'${destination.resolve(".claude").absolutePath}'")
        }

        @Test
        fun `should resolve a deploy directory starting with a tilde against the home of the user running the engine`() {
            // given
            // - a dry run writes nothing, so naming a directory under the real home is safe here
            writeProject(deployDirectory = "~/.ai-tools-engine-test-destination")
            val expected = File(System.getProperty("user.home"), ".ai-tools-engine-test-destination")

            // when
            dryRunEngine.process(locations())

            // then
            assertThat(sinkAppender.list.map { it.formattedMessage }).anyMatch { it.contains(expected.resolve("CLAUDE.md").absolutePath) }
            assertThat(workspace.resolve("~")).doesNotExist()
        }

        @Test
        fun `should fail naming the variable when the deploy directory references an undeclared one`() {
            // given
            val variableEngine = ToolsEngine(
                workspace,
                variables = VariableResolver(emptyMap(), emptyEnvironment),
                tools = listOf(ToolFactory.create(ToolType.CLAUDE, dryRun = true)),
                dryRun = true,
            )
            writeProject(deployDirectory = "\${MISSING_FOLDER}/custom-ai-tools")

            // when
            val error = runCatching { variableEngine.process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(DeployDirectoryResolvingException::class.java)
                .hasMessageContaining("MISSING_FOLDER")
        }

        @Test
        fun `should write nothing into the home when a user deployment is dry run`() {
            // given
            writeAgent("global-agent", "base")
            writeDirectorySkill("global-skill", "helper.md", companionFileExists = true)
            writeUserDeployment()

            // when
            dryRunEngine.process(locations())

            // then
            assertThat(userHome).doesNotExist()
            // - the home is still announced, with what would happen to it
            assertThat(infos()).anyMatch { it.contains(userHome.absolutePath) && it.contains("Would deploy") }
            assertThat(sinkInfos()).anyMatch { it.contains(userHome.resolve(".claude/CLAUDE.md").absolutePath) }
            assertThat(sinkInfos()).anyMatch { it.contains(userHome.resolve(".claude/agents/global-agent.md").absolutePath) }
        }

        @Test
        fun `should leave the home untouched when a replacing user deployment is dry run`() {
            // given
            // - the user scope replaces per artifact inside the adapter, a delete the engine never sees directly
            writeDirectorySkill("global-skill", "helper.md", companionFileExists = true)
            writeUserDeployment(replace = true)
            val instructions = userHome.resolve(".claude/CLAUDE.md")
            instructions.parentFile.mkdirs()
            instructions.writeText("From an earlier deploy.\n")
            val leftover = userHome.resolve(".claude/skills/global-skill/leftover.md")
            leftover.parentFile.mkdirs()
            leftover.writeText("Left behind by an earlier deploy.\n")

            // when
            dryRunEngine.process(locations())

            // then
            assertThat(instructions).hasContent("From an earlier deploy.\n")
            assertThat(leftover).hasContent("Left behind by an earlier deploy.\n")
            assertThat(userHome.resolve(".claude/skills/global-skill/SKILL.md")).doesNotExist()
            assertThat(warnings()).anyMatch { it.contains("globals") && it.contains("Would replace") }
        }

        @Test
        fun `should say it was a dry run in its last line`() {
            // given
            writeUserDeployment()

            // when
            dryRunEngine.process(locations())

            // then
            assertThat(infos().last()).contains("Dry run").contains("nothing was written")
        }

        @Test
        fun `should say it was a dry run before reporting the failures of the run`() {
            // given
            writeAgent("broken-agent", "missing-ruleset")

            // when
            runCatching { dryRunEngine.process(locations()) }

            // then
            assertThat(infos().last()).contains("Dry run")
        }

        @Test
        fun `should not say it was a dry run when it deploys`() {
            // when
            engine.process(locations())

            // then
            assertThat(infos()).noneMatch { it.contains("Dry run") }
            assertThat(destination.resolve("CLAUDE.md")).exists()
        }

        private fun sinkInfos() = sinkAppender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }
    }

    @Nested
    inner class McpServers {

        // - the config of the run declares the base URL, the environment of the run carries the secret; neither is a real value
        private val mcpVariables = VariableResolver(
            variables = mapOf("JIRA_BASE_URL" to "https://jira.example.com"),
            environment = { name -> mapOf("JIRA_PAT" to SECRET_VALUE)[name] },
        )

        private fun engineFor(vararg toolTypes: ToolType, dryRun: Boolean = false, variables: VariableResolver = mcpVariables) = ToolsEngine(
            workspace,
            variables = variables,
            userHome = userHome,
            tools = toolTypes.map { ToolFactory.create(it, dryRun) },
            dryRun = dryRun,
        )

        @BeforeEach
        fun selectAtlassian() {
            // - MCP servers are opt-in, so the project of these tests names the one it deploys
            writeProject(mcpFilter = listOf("atlassian"))
        }

        @Test
        fun `should write the selected servers into the MCP file of every tool that supports them, with secrets only as references`() {
            // given
            writeAtlassianServer()

            // when
            engineFor(*ToolType.entries.toTypedArray()).process(locations())

            // then
            val claude = destination.resolve(".mcp.json").readText()
            val vsCode = destination.resolve(".vscode/mcp.json").readText()
            val cursor = destination.resolve(".cursor/mcp.json").readText()
            val codex = destination.resolve(".codex/config.toml").readText()
            assertThat(claude).contains("\"JIRA_PAT\": \"\${JIRA_PAT}\"")
            assertThat(vsCode).contains("\"JIRA_PAT\": \"\${env:JIRA_PAT}\"")
            assertThat(cursor).contains("\"JIRA_PAT\": \"\${env:JIRA_PAT}\"")
            assertThat(codex).contains("env_vars = [\"JIRA_PAT\"]")
            assertThat(listOf(claude, vsCode, cursor, codex)).allSatisfy {
                assertThat(it).doesNotContain(SECRET_VALUE).contains("https://jira.example.com")
            }
        }

        @ParameterizedTest
        @CsvSource("windsurf", "antigravity")
        fun `should report a tool without MCP support as skipped for the servers a project selects`(tool: String) {
            // given
            writeAtlassianServer()

            // when
            engineFor(ToolType.entries.single { it.serialName == tool }).process(locations())

            // then
            assertThat(warnings()).anyMatch { it.contains("test-project") && it.contains(tool) && it.contains("MCP") && it.contains("atlassian") }
        }

        @ParameterizedTest
        @CsvSource(
            // - no mcps block at all
            "false",
            // - an mcps block selecting nothing
            "true",
        )
        fun `should not touch any MCP config file of a project that selects no server`(emptyWhitelist: Boolean) {
            // given
            writeAtlassianServer()
            writeProject(mcpFilter = if (emptyWhitelist) emptyList() else null)
            // - a hand-written entry named like an MCP manifest of the run, in the compact layout a rewrite would change
            val mcpFile = destination.resolve(".mcp.json")
            mcpFile.parentFile.mkdirs()
            val existing = """{"mcpServers":{"atlassian":{"command":"mine"},"playwright":{"command":"npx"}}}"""
            mcpFile.writeText(existing)

            // when
            engineFor(ToolType.CLAUDE, ToolType.CODEX, ToolType.GITHUB_COPILOT, ToolType.CURSOR).process(locations())

            // then
            assertThat(mcpFile).hasContent(existing)
            assertThat(destination.resolve(".vscode/mcp.json")).doesNotExist()
            assertThat(destination.resolve(".cursor/mcp.json")).doesNotExist()
            assertThat(destination.resolve(".codex/config.toml")).doesNotExist()
            assertThat(warnings()).noneMatch { it.contains("MCP") }
        }

        @Test
        fun `should remove an owned server the project does not select and keep a server added by hand`() {
            // given
            // - inside a project that selects servers, an entry named after any MCP manifest of the run is the engine's
            writeAtlassianServer()
            writeYaml("mcps/other.yml", "id: other\ndescription: Other\ntransport:\n  type: stdio\n  command: other-server\n")
            writeProject(mcpFilter = listOf("other"))
            val mcpFile = destination.resolve(".mcp.json")
            mcpFile.parentFile.mkdirs()
            mcpFile.writeText("""{ "mcpServers": { "atlassian": { "command": "old" }, "playwright": { "command": "npx" } } }""")

            // when
            engineFor(ToolType.CLAUDE).process(locations())

            // then
            assertThat(mcpFile)
                .content()
                .doesNotContain("atlassian")
                .contains("playwright")
                .contains("other-server")
        }

        @Test
        fun `should keep the MCP files and the servers added by hand when a replacing project is deployed`() {
            // given
            writeAtlassianServer()
            writeProject(replace = true, mcpFilter = listOf("atlassian"))
            val codexConfig = destination.resolve(".codex/config.toml")
            codexConfig.parentFile.mkdirs()
            codexConfig.writeText("# mine\n[mcp_servers.playwright]\ncommand = \"npx\"\n")
            val cursorConfig = destination.resolve(".cursor/mcp.json")
            cursorConfig.parentFile.mkdirs()
            cursorConfig.writeText("""{ "mcpServers": { "playwright": { "command": "npx" } } }""")

            // when
            engineFor(ToolType.CODEX, ToolType.CURSOR).process(locations())

            // then
            assertThat(codexConfig).content().startsWith("# mine\n[mcp_servers.playwright]\ncommand = \"npx\"\n").contains("[mcp_servers.atlassian]")
            assertThat(cursorConfig).content().contains("playwright").contains("atlassian")
        }

        @Test
        fun `should fail naming the server and the variable when a required plain variable is declared nowhere, and still export the rest`() {
            // given
            writeAtlassianServer(extraVariable = "  - name: CONFLUENCE_BASE_URL\n    description: Base URL\n    secret: false\n")

            // when
            val error = runCatching { engineFor(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'CONFLUENCE_BASE_URL'")
            // - the failure is labelled with the server that failed, not with the file holding every server of the project
            assertThat((error as ExportFailedException).failures.map { it.manifest }).containsExactly("MCP server 'atlassian'")
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(destination.resolve(".mcp.json")).doesNotExist()
        }

        @Test
        fun `should fail only the project whose MCP config file links outside it, naming the file and the target, and write the rest`() {
            // given
            writeAtlassianServer()
            val outside = tempDir.resolve("home/.claude.json").toFile()
            outside.parentFile.mkdirs()
            outside.writeText("""{"mcpServers":{}}""")
            destination.mkdirs()
            Files.createSymbolicLink(destination.resolve(".mcp.json").toPath(), outside.toPath())

            // when
            val error = runCatching { engineFor(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining(destination.resolve(".mcp.json").absolutePath)
                .hasMessageContaining(outside.canonicalPath)
            assertThat(outside).hasContent("""{"mcpServers":{}}""")
            assertThat(destination.resolve("CLAUDE.md")).exists()
        }

        /**
         * A linked `.codex` that leads nowhere fails the Codex files of that project only, before any of them is written; the dry run reports exactly the failure the deploy reports, and every other tool and project of the run is still deployed.
         */
        @ParameterizedTest
        @CsvSource(
            // - the link leads to a directory that no longer exists
            "dangling",
            // - the link leads to a second link that leads back to it
            "looping",
        )
        fun `should fail only the tool and project whose tool directory is a link that cannot be followed, in a dry run as in a deploy`(
            kind: String,
        ) {
            // given
            writeAtlassianServer()
            // - a second project of the same run, whose tool directories are plain
            val laterDestination = tempDir.resolve("later-destination").toFile()
            writeProject("later-project", "later-project", laterDestination.absolutePath, mcpFilter = listOf("atlassian"))
            destination.mkdirs()
            val codexDir = destination.resolve(".codex")
            val leadsTo = when (kind) {
                "dangling" -> tempDir.resolve("out/missing").toFile()
                else -> destination.resolve("loop").also { Files.createSymbolicLink(it.toPath(), codexDir.toPath()) }
            }
            Files.createSymbolicLink(codexDir.toPath(), leadsTo.toPath())
            val configFile = codexDir.resolve("config.toml")

            // when
            val dryRunError = runCatching { engineFor(ToolType.CODEX, ToolType.CLAUDE, dryRun = true).process(locations()) }.exceptionOrNull()
            val writtenByDryRun = listOf(laterDestination, destination.resolve(".mcp.json")).filter { it.exists() }
            val deployError = runCatching { engineFor(ToolType.CODEX, ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(deployError)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'${codexDir.absolutePath}'")
                .hasMessageContaining("'${leadsTo.absolutePath}'")
            assertThat(dryRunError).isInstanceOf(ExportFailedException::class.java).hasMessage(deployError?.message)
            assertThat(writtenByDryRun).isEmpty()
            listOf(dryRunError, deployError).forEach { error ->
                assertThat((error as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.manifest) })
                    .containsExactly(Triple("test-project", ToolType.CODEX, "tool directory '.codex'"))
            }
            // - nothing of Codex is written for that project, the instructions beside .codex included
            assertThat(configFile.exists()).isFalse()
            assertThat(destination.resolve("AGENTS.md")).doesNotExist()
            // - the tool after Codex in the same project, and the other project, are deployed in full
            assertThat(destination.resolve(".mcp.json")).content().contains("atlassian")
            assertThat(laterDestination.resolve(".codex/config.toml")).content().contains("[mcp_servers.atlassian]")
            assertThat(laterDestination.resolve(".mcp.json")).content().contains("atlassian")
            assertThat(tempDir.resolve("out").toFile()).doesNotExist()
        }

        /**
         * A tool directory a deploy cannot write through fails that tool of that project once, before any file of it is written, and every other tool and project is still written. The dry run finds the directory exactly as the deploy does, whether the project selects an MCP server or not.
         */
        @ParameterizedTest
        @CsvSource(
            // - a dangling .codex in a project that deploys a Codex agent, which is written into .codex/skills before the MCP config file
            "codex, true",
            "codex, false",
            // - a regular file at .cursor, where every deploy writes the rules of the project first
            "cursor, true",
            "cursor, false",
        )
        fun `should fail only the tool whose directory cannot be written and still write every other tool and project`(
            tool: String,
            selectsServers: Boolean,
        ) {
            // given
            writeAtlassianServer()
            writeAgent("basic", "base")
            val mcpFilter = if (selectsServers) listOf("atlassian") else null
            writeProject(mcpFilter = mcpFilter)
            // - a second project of the same run, whose tool directories are plain
            val laterDestination = tempDir.resolve("later-destination").toFile()
            writeProject("later-project", "later-project", laterDestination.absolutePath, mcpFilter = mcpFilter)
            val toolType = if (tool == "codex") ToolType.CODEX else ToolType.CURSOR
            destination.mkdirs()
            val brokenDir = destination.resolve(".$tool")
            if (tool == "codex") Files.createSymbolicLink(brokenDir.toPath(), tempDir.resolve("out/missing")) else brokenDir.writeText("not a directory\n")

            // when
            val dryRunError = runCatching { engineFor(toolType, ToolType.CLAUDE, dryRun = true).process(locations()) }.exceptionOrNull()
            val writtenByDryRun = listOf(laterDestination, destination.resolve("CLAUDE.md")).filter { it.exists() }
            val deployError = runCatching { engineFor(toolType, ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(deployError)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'${brokenDir.absolutePath}'")
                .hasMessageContaining("writes no ${toolType.serialName} files of project 'test-project'")
            assertThat((deployError as ExportFailedException).failures.map { it.deploymentId to it.toolType }).containsExactly("test-project" to toolType)
            // - the failure is named after the directory, and its cause is the check of that directory
            assertThat(deployError.failures.map { it.manifest to it.cause::class.java }).containsExactly("tool directory '.$tool'" to ToolDirectoryException::class.java)
            // - the dry run fails the same pair with the same message, whether the project selects a server or not
            assertThat(dryRunError).isInstanceOf(ExportFailedException::class.java).hasMessage(deployError.message)
            assertThat(writtenByDryRun).isEmpty()
            // - the other tool of the project, and every tool of the later project, are written in full
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(destination.resolve(".claude/agents/basic.md")).exists()
            val laterFiles = if (tool == "codex") listOf("AGENTS.md", ".codex/skills/agent-basic/SKILL.md") else listOf(".cursor/rules/project.mdc", ".cursor/rules/agent-basic.mdc")
            (laterFiles + listOf("CLAUDE.md", ".claude/agents/basic.md")).forEach { assertThat(laterDestination.resolve(it)).exists() }
            val mcpFiles = listOf(".mcp.json", if (tool == "codex") ".codex/config.toml" else ".cursor/mcp.json")
            mcpFiles.forEach { assertThat(laterDestination.resolve(it).exists()).isEqualTo(selectsServers) }
            assertThat(destination.resolve(".mcp.json").exists()).isEqualTo(selectsServers)
            assertThat(tempDir.resolve("out").toFile()).doesNotExist()
        }

        @Test
        fun `should resolve a leading tilde of a stdio command against the real home, whatever the user home of the run`() {
            // given
            writeYaml("mcps/atlassian.yml", "id: atlassian\ndescription: Jira\ntransport:\n  type: stdio\n  command: ~/bin/jira-mcp-server\n")

            // when
            engineFor(ToolType.CLAUDE).process(locations())

            // then
            val content = destination.resolve(".mcp.json").readText()
            assertThat(content).contains(File(System.getProperty("user.home"), "bin/jira-mcp-server").absolutePath).doesNotContain(userHome.absolutePath)
        }

        @Test
        fun `should name the MCP file of every supporting tool and write nothing in a dry run`() {
            // given
            writeAtlassianServer()
            val sinkAppender = ListAppender<ILoggingEvent>()
            val sinkLogger = LoggerFactory.getLogger(DryRunArtifactSink::class.java) as Logger
            sinkAppender.start()
            sinkLogger.addAppender(sinkAppender)

            // when
            try {
                engineFor(*ToolType.entries.toTypedArray(), dryRun = true).process(locations())
            } finally {
                sinkLogger.detachAppender(sinkAppender)
                sinkAppender.stop()
            }

            // then
            val sinkInfos = sinkAppender.list.map { it.formattedMessage }
            listOf(".mcp.json", ".vscode/mcp.json", ".cursor/mcp.json", ".codex/config.toml").forEach { path ->
                assertThat(sinkInfos).anyMatch { it.contains("MCP servers [atlassian]") && it.contains(destination.resolve(path).absolutePath) }
                assertThat(destination.resolve(path)).doesNotExist()
            }
        }

        /**
         * Every way a value of the deploying shell or of the config could reach an MCP config file of a project or of the home, a ledger, a `settings.json`, a log line or an error: each either renders a reference only, or fails, and the value appears nowhere.
         */
        @ParameterizedTest
        @CsvSource(
            // - a header marked secret only on the outside, holding a bearer token variable not marked itself: rendered as a reference
            "outer-secret-bearer, reference, ATLASSIAN_TOKEN",
            // - an environment variable marked secret on the outside whose value puts the secret into other text
            "outer-secret-composed-environment, refused-at-load, ATLASSIAN_KEY",
            // - one variable derived as plain text in the url and as a secret in a header
            "conflicting-secrecy, refused-at-load, ATLASSIAN_TOKEN",
            // - a server json holding a reference in the syntax of the engine
            "literal-reference, refused-at-load, X-Key",
            // - a server json holding a reference in the syntax of VS Code and Cursor
            "tool-reference, refused-at-load, X-Env",
            // - an inline manifest referencing a variable it does not declare
            "undeclared-reference, refused-at-load, GITHUB_TOKEN",
            // - a plain variable only the deploying shell exports: left out
            "environment-plain-value, omitted, EXAMPLE_TOKEN",
            // - a TLS verification flag only the deploying shell exports: left out
            "environment-tls-flag, omitted, JIRA_VERIFY_SSL",
            // - a plain value of the config carrying a reference a tool would expand
            "nested-config-value, refused-at-export, NESTED_PROXY",
        )
        fun `should never write, log or report a value of the environment or the config`(
            scenario: String,
            outcome: String,
            name: String,
        ) {
            // given
            val rootAppender = ListAppender<ILoggingEvent>()
            val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
            rootAppender.start()
            rootLogger.addAppender(rootAppender)
            val variables = VariableResolver(
                variables = mapOf("NESTED_PROXY" to "http://user:$LEAKED_VALUE@proxy/\${X}"),
                environment = { name -> if (name in LEAKING_NAMES) LEAKED_VALUE else null },
            )
            writeLeakScenario(scenario)
            // - the project and the user deployment restrict the tools of the server, so both settings.json, both ledgers and the MCP files of the home are written
            writeProject(mcpFilter = listOf("atlassian"), mcpTools = mapOf("atlassian" to McpToolRestriction(deny = listOf("search"))))
            writeUserDeployment(tools = listOf("claude", "codex"), mcpFilter = listOf("atlassian"), mcpTools = mapOf("atlassian" to McpToolRestriction(deny = listOf("search"))))

            // when
            val error = try {
                runCatching { engineFor(ToolType.CLAUDE, ToolType.CODEX, ToolType.GITHUB_COPILOT, ToolType.CURSOR, variables = variables).process(locations()) }.exceptionOrNull()
            } finally {
                rootLogger.detachAppender(rootAppender)
                rootAppender.stop()
            }

            // then
            val written = (destination.walkTopDown() + userHome.walkTopDown())
                .filter { it.isFile }
                .map { it.readText() }
                .toList()
            val logged = rootAppender.list.flatMap { event ->
                listOfNotNull(event.formattedMessage) + generateSequence(event.throwableProxy) { it.cause }.mapNotNull { it.message }
            }
            val reported = generateSequence(error) { it.cause }.mapNotNull { it.message }.toList()
            assertThat(written + logged + reported).noneMatch { it.contains(LEAKED_VALUE) }
            // - and each scenario ends the way it is meant to, so a refusal for an unrelated reason cannot pass for safety
            val claude = destination.resolve(".mcp.json")
            val home = userHome.resolve(".claude.json")
            when (outcome) {
                "reference" -> {
                    assertThat(error).isNull()
                    assertThat(listOf(claude, home)).allSatisfy { assertThat(it).content().contains("\${$name}") }
                }
                "omitted" -> {
                    assertThat(error).isNull()
                    assertThat(listOf(claude, home)).allSatisfy { assertThat(it).content().contains("\"atlassian\"").doesNotContain(name) }
                }
                "refused-at-load" -> {
                    assertThat(error).isInstanceOf(ManifestLoadingException::class.java).hasMessageContaining(name)
                    assertThat(listOf(claude, home)).allSatisfy { assertThat(it).doesNotExist() }
                }
                else -> {
                    assertThat(error).isInstanceOf(ExportFailedException::class.java).hasMessageContaining("'$name'")
                    assertThat(listOf(claude, home)).allSatisfy { assertThat(it).doesNotExist() }
                }
            }
            // - wherever the servers were written, the permissions, the ledgers and the files of the home were written too, so the absence above covers them
            if (outcome == "reference" || outcome == "omitted") {
                listOf(destination.resolve(".claude/settings.json"), destination.resolve(".ai-tools/mcp-ledger.json"), userHome.resolve(".ai-tools/mcp-ledger.json"), userHome.resolve(".codex/config.toml"), userHome.resolve(".claude/settings.json"))
                    .forEach { assertThat(it).exists() }
            }
        }

        private fun writeLeakScenario(scenario: String) {
            val schema = "\"\$schema\": \"https://static.modelcontextprotocol.io/schemas/2025-12-11/server.schema.json\", \"name\": \"x\", \"description\": \"d\", \"version\": \"1\""

            fun pointer(serverJson: String) {
                val folder = tempDir.resolve("servers/$scenario").toFile()
                folder.mkdirs()
                folder.resolve("server.json").writeText("{$schema, $serverJson}")
                writeYaml("mcps/atlassian.yml", "id: atlassian\nsource: \"${folder.absolutePath}\"\n")
            }

            fun remote(url: String, headers: String) = pointer("\"remotes\": [{\"type\": \"streamable-http\", \"url\": \"$url\", \"variables\": {\"token\": {}}, \"headers\": [$headers]}]")

            fun inline(transport: String, variables: String = "") = writeYaml("mcps/atlassian.yml", "id: atlassian\ndescription: d\ntransport:\n$transport$variables")
            when (scenario) {
                "outer-secret-bearer" -> remote("https://x/mcp", "{\"name\": \"Authorization\", \"isSecret\": true, \"value\": \"Bearer {token}\", \"variables\": {\"token\": {\"isRequired\": true}}}")
                "outer-secret-composed-environment" -> pointer(
                    "\"packages\": [{\"registryType\": \"npm\", \"identifier\": \"x\", \"transport\": {\"type\": \"stdio\"}, " +
                        "\"environmentVariables\": [{\"name\": \"API\", \"isSecret\": true, \"value\": \"key={key}\", \"variables\": {\"key\": {\"isRequired\": true}}}]}]",
                )
                "conflicting-secrecy" -> remote("https://x/{token}/mcp", "{\"name\": \"Authorization\", \"value\": \"Bearer {token}\", \"variables\": {\"token\": {\"isSecret\": true}}}")
                "literal-reference" -> remote("https://x/mcp", "{\"name\": \"X-Key\", \"value\": \"\${DEMO_KEY}\"}")
                "tool-reference" -> remote("https://x/mcp", "{\"name\": \"X-Env\", \"value\": \"\${env:AWS_SECRET_ACCESS_KEY}\"}")
                "undeclared-reference" -> inline("  type: stdio\n  command: server\n  args: ['--token=\${GITHUB_TOKEN}']\n")
                "environment-plain-value" -> inline("  type: stdio\n  command: server\n", "variables:\n  - name: EXAMPLE_TOKEN\n    description: t\n    secret: false\n    required: false\n")
                "environment-tls-flag" -> inline("  type: stdio\n  command: server\n", "variables:\n  - name: JIRA_VERIFY_SSL\n    description: t\n    secret: false\n    required: false\n")
                "nested-config-value" -> inline("  type: stdio\n  command: server\n", "variables:\n  - name: NESTED_PROXY\n    description: t\n    secret: false\n")
                else -> error("Unknown scenario $scenario")
            }
        }

        private fun writeAtlassianServer(extraVariable: String = "") = writeYaml(
            "mcps/atlassian.yml",
            "id: atlassian\ndescription: Jira and Confluence\n" +
                "transport:\n  type: stdio\n  command: jira-mcp-server\n" +
                "variables:\n" +
                "  - name: JIRA_PAT\n    description: Token\n    secret: true\n" +
                "  - name: JIRA_BASE_URL\n    description: Base URL\n    secret: false\n" +
                extraVariable,
        )
    }

    /**
     * The MCP servers of a user deployment, written into `<home>/.claude.json` and `<home>/.codex/config.toml`. Every home here is a directory of the test's own temporary tree.
     */
    @Nested
    inner class UserScopeMcpServers {

        private val claudeJson get() = userHome.resolve(".claude.json")
        private val codexConfig get() = userHome.resolve(".codex/config.toml")

        // - the environment of the run carries the secret; no file may ever hold it
        private val secretVariables =
            VariableResolver(emptyMap(), environment = { name -> mapOf("JIRA_PAT" to SECRET_VALUE)[name] })

        @BeforeEach
        fun writeServers() {
            writeStdioServer("atlassian", secret = "JIRA_PAT")
            writeStdioServer("other")
        }

        @Test
        fun `should fail only the tool whose config file of the home is nested deeper than the limit, naming the file and the limit, and deploy the rest`() {
            // given
            writeUserDeployment(tools = listOf("claude", "codex"), mcpFilter = listOf("atlassian"))
            userHome.mkdirs()
            val deep = "{\"deep\": " + "[".repeat(10_000) + "]".repeat(10_000) + "}"
            claudeJson.writeText(deep)

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(ExportFailedException::class.java).hasMessageContaining(claudeJson.absolutePath).hasMessageContaining("512")
            assertThat((error as ExportFailedException).failures.map { it.deploymentId to it.toolType }).containsExactly("globals" to ToolType.CLAUDE)
            assertThat(claudeJson).hasContent(deep)
            assertThat(codexConfig).content().contains("[mcp_servers.atlassian]")
            assertThat(destination.resolve("CLAUDE.md")).exists()
        }

        @Test
        fun `should create the config files and the ledger of the home readable by their owner only, and those of a project with the mode of every artifact`() {
            // given
            assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            val restriction = mapOf("atlassian" to McpToolRestriction(deny = listOf("delete")))
            writeUserDeployment(tools = listOf("claude", "codex"), mcpFilter = listOf("atlassian"), mcpTools = restriction)
            writeProject(mcpFilter = listOf("atlassian"), mcpTools = restriction)

            // when
            engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations())

            // then
            listOf(claudeJson, codexConfig, userHome.resolve(".claude/settings.json"), userHome.resolve(".ai-tools/mcp-ledger.json")).forEach { file ->
                assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))).describedAs(file.path).isEqualTo("rw-------")
            }
            val artifactMode = Files.getPosixFilePermissions(destination.resolve("CLAUDE.md").toPath())
            listOf(".mcp.json", ".codex/config.toml", ".claude/settings.json", ".ai-tools/mcp-ledger.json").forEach { path ->
                assertThat(Files.getPosixFilePermissions(destination.resolve(path).toPath())).describedAs(path).isEqualTo(artifactMode)
            }
        }

        @Test
        fun `should write no MCP file into the home when the user deployment declares no mcps block, in a dry run as in a deploy`() {
            // given
            writeUserDeployment(tools = listOf("claude", "codex"))
            userHome.mkdirs()
            val existing = "{\"numStartups\":1,\"mcpServers\":{\"atlassian\":{\"command\":\"mine\"}}}"
            claudeJson.writeText(existing)

            // when
            engineWith(ToolType.CLAUDE, ToolType.CODEX, dryRun = true).process(locations())
            engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations())

            // then
            assertThat(claudeJson).hasContent(existing)
            assertThat(codexConfig).doesNotExist()
            assertThat(userHome.resolve(".ai-tools")).doesNotExist()
        }

        @Test
        fun `should name only the MCP files of Claude Code and Codex in the home in a dry run, and write nothing`() {
            // given
            writeUserDeployment(tools = null, mcpFilter = listOf("atlassian"))

            // when
            val infos =
                infosOf(DryRunArtifactSink::class.java) { engineWith(*ToolType.entries.toTypedArray(), dryRun = true).process(locations()) }

            // then
            assertThat(infos.filter { it.contains("MCP servers") }).containsExactlyInAnyOrder(
                "Would write MCP servers [atlassian] to ${claudeJson.absolutePath}",
                "Would write MCP servers [atlassian] to ${codexConfig.absolutePath}",
            )
            assertThat(userHome).doesNotExist()
        }

        @Test
        fun `should write the selected servers into the home with secrets only as references, and record them in the ledger of the home`() {
            // given
            writeUserDeployment(tools = listOf("claude", "codex"), mcpFilter = listOf("atlassian"))

            // when
            engineWith(ToolType.CLAUDE, ToolType.CODEX, variables = secretVariables).process(locations())

            // then
            assertThat(claudeJson).content().contains("\"JIRA_PAT\": \"\${JIRA_PAT}\"").doesNotContain(SECRET_VALUE)
            assertThat(codexConfig)
                .content()
                .contains("[mcp_servers.atlassian]")
                .contains("env_vars = [\"JIRA_PAT\"]")
                .doesNotContain(SECRET_VALUE)
            assertThat(recordedEntries(userHome.resolve(".ai-tools/mcp-ledger.json")))
                .isEqualTo(mapOf(".claude.json" to listOf("atlassian"), ".codex/config.toml" to listOf("atlassian")))
        }

        @Test
        fun `should report every tool without MCP servers in the user scope as skipped once, with its reason`() {
            // given
            writeUserDeployment(tools = null, mcpFilter = listOf("atlassian"))

            // when
            engineWith(*ToolType.entries.toTypedArray()).process(locations())

            // then
            listOf("github_copilot", "cursor", "windsurf", "antigravity").forEach { tool ->
                assertThat(warnings().filter { it.contains("globals: $tool ") && it.contains("MCP") })
                    .describedAs(tool)
                    .singleElement()
                    .satisfies({ assertThat(it).contains("[atlassian]").contains("user scope") })
            }
            assertThat(warnings()).noneMatch { (it.contains("globals: claude ") || it.contains("globals: codex ")) && it.contains("MCP") }
        }

        @Test
        fun `should keep every byte of the file of Claude Code outside the entry it owns, the session state and a server added by hand included`() {
            // given
            writeUserDeployment(tools = listOf("claude"), mcpFilter = listOf("atlassian"))
            userHome.mkdirs()
            val existing = "{\n  \"numStartups\": 12,\n  \"projects\": {\n    \"/work/p\": {\n      \"lastCost\": 0.25,\n      \"allowedTools\": []\n    }\n  },\n" +
                "  \"mcpServers\": {\n    \"playwright\": {\n      \"command\": \"npx\"\n    }\n  },\n  \"userID\": \"caf\\u00e9\"\n}\n"
            claudeJson.writeText(existing)

            // when
            engineWith(ToolType.CLAUDE).process(locations())
            val deployed = claudeJson.readText()
            writeUserDeployment(tools = listOf("claude"), mcpFilter = emptyList())
            engineWith(ToolType.CLAUDE).process(locations())

            // then
            val prefix = existing.commonPrefixWith(deployed).length
            val suffix = existing.substring(prefix).commonSuffixWith(deployed.substring(prefix)).length
            assertThat(prefix + suffix).describedAs("the deploy only inserts the owned entry").isEqualTo(existing.length)
            assertThat(deployed).contains("\"atlassian\"")
            // - the ledger names the entry, so the deployment that deselects it removes it and restores the file byte for byte
            assertThat(claudeJson).hasContent(existing)
            assertThat(userHome.resolve(".ai-tools")).doesNotExist()
        }
    }

    /**
     * The ledger of a project: the entries the engine wrote into each MCP file, so a later deploy removes what its deployment no longer selects.
     */
    @Nested
    inner class McpLedgers {

        private val mcpFiles = listOf(".mcp.json", ".vscode/mcp.json", ".cursor/mcp.json", ".codex/config.toml")
        private val tools = arrayOf(ToolType.CLAUDE, ToolType.CODEX, ToolType.GITHUB_COPILOT, ToolType.CURSOR)
        private val ledger get() = destination.resolve(".ai-tools/mcp-ledger.json")

        @BeforeEach
        fun writeServersAndForeignEntries() {
            writeStdioServer("atlassian")
            writeStdioServer("other")
            // - a server added by hand to every MCP file, which no deploy may remove
            destination.resolve(".vscode").mkdirs()
            destination.resolve(".cursor").mkdirs()
            destination.resolve(".codex").mkdirs()
            destination.resolve(".mcp.json").writeText("{\"mcpServers\": {\"mine\": {\"command\": \"npx\"}}}")
            destination.resolve(".vscode/mcp.json").writeText("{\"servers\": {\"mine\": {\"command\": \"npx\"}}}")
            destination.resolve(".cursor/mcp.json").writeText("{\"mcpServers\": {\"mine\": {\"command\": \"npx\"}}}")
            destination.resolve(".codex/config.toml").writeText("[mcp_servers.mine]\ncommand = \"npx\"\n")
        }

        @Test
        fun `should remove a deselected server from every MCP file, then every recorded one once nothing is selected, reporting each removal in a dry run and keeping foreign entries`() {
            // given
            writeProject(mcpFilter = listOf("atlassian", "other"))
            engineWith(*tools).process(locations())
            val ledgerAfterBoth = recordedEntries(ledger)

            // when
            writeProject(mcpFilter = listOf("atlassian"))
            val narrowedInDryRun =
                infosOf(DryRunArtifactSink::class.java) { engineWith(*tools, dryRun = true).process(locations()) }
            val beforeNarrowing = mcpFiles.map { destination.resolve(it).readText() }
            engineWith(*tools).process(locations())
            val narrowed = mcpFiles.associateWith { destination.resolve(it).readText() }
            val ledgerAfterNarrowing = recordedEntries(ledger)
            writeProject(mcpFilter = null)
            val clearedInDryRun =
                infosOf(DryRunArtifactSink::class.java) { engineWith(*tools, dryRun = true).process(locations()) }
            engineWith(*tools).process(locations())

            // then
            assertThat(ledgerAfterBoth).isEqualTo(mcpFiles.associateWith { listOf("atlassian", "other") })
            mcpFiles.forEach { path ->
                val file = destination.resolve(path)
                assertThat(narrowedInDryRun).describedAs(path).anyMatch { it.contains("MCP servers [atlassian], removing [other]") && it.contains(file.absolutePath) }
                assertThat(clearedInDryRun).describedAs(path).anyMatch { it.contains("MCP servers [], removing [atlassian]") && it.contains(file.absolutePath) }
                assertThat(narrowed.getValue(path))
                    .describedAs(path)
                    .contains("atlassian")
                    .doesNotContain("other")
                    .contains("mine")
                assertThat(file)
                    .describedAs(path)
                    .content()
                    .doesNotContain("atlassian")
                    .doesNotContain("other")
                    .contains("mine")
            }
            // - a dry run changed nothing: the files it reported on were still the ones of the first deploy
            assertThat(beforeNarrowing).allSatisfy { assertThat(it).contains("other") }
            assertThat(ledgerAfterNarrowing).isEqualTo(mcpFiles.associateWith { listOf("atlassian") })
            assertThat(ledger).doesNotExist()
            assertThat(destination.resolve(".ai-tools")).doesNotExist()
        }

        @Test
        fun `should remove an entry the ledger records whose manifest the run no longer has`() {
            // given
            writeProject(mcpFilter = listOf("atlassian", "other"))
            engineWith(ToolType.CLAUDE).process(locations())
            workspace.resolve("mcps/other.yml").delete()
            writeProject(mcpFilter = listOf("atlassian"))

            // when
            engineWith(ToolType.CLAUDE).process(locations())

            // then
            assertThat(destination.resolve(".mcp.json"))
                .content()
                .contains("atlassian")
                .contains("mine")
                .doesNotContain("other")
            assertThat(recordedEntries(ledger)).isEqualTo(mapOf(".mcp.json" to listOf("atlassian")))
        }

        @Test
        fun `should keep the record of a tool the run does not deploy`() {
            // given
            writeProject(mcpFilter = listOf("atlassian"))
            engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations())
            writeProject(mcpFilter = null)

            // when
            engineWith(ToolType.CLAUDE).process(locations())

            // then
            assertThat(destination.resolve(".mcp.json")).content().doesNotContain("atlassian")
            assertThat(destination.resolve(".codex/config.toml")).content().contains("[mcp_servers.atlassian]")
            assertThat(recordedEntries(ledger)).isEqualTo(mapOf(".codex/config.toml" to listOf("atlassian")))
        }

        @Test
        fun `should fail every MCP file of a project whose ledger cannot be read, naming the ledger, and deploy everything else`() {
            // given
            writeProject(mcpFilter = listOf("atlassian"))
            val laterDestination = tempDir.resolve("later-destination").toFile()
            writeProject("later-project", "later-project", laterDestination.absolutePath, mcpFilter = listOf("atlassian"))
            ledger.parentFile.mkdirs()
            ledger.writeText("{\"version\": 7, \"files\": {}}")
            val before = mcpFiles.map { destination.resolve(it).readText() }

            // when
            val error = runCatching { engineWith(*tools).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(ExportFailedException::class.java).hasMessageContaining(ledger.absolutePath)
            assertThat((error as ExportFailedException).failures.map { it.deploymentId to it.toolType })
                .containsExactlyInAnyOrder(*tools.map { "test-project" to it }.toTypedArray())
            assertThat(mcpFiles.map { destination.resolve(it).readText() }).isEqualTo(before)
            assertThat(destination.resolve("CLAUDE.md")).exists()
            assertThat(laterDestination.resolve(".mcp.json")).content().contains("atlassian")
        }

        @Test
        fun `should leave every MCP file of a tool as it is when the ledger cannot be written, and report the ledger once for that tool`() {
            // given
            assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            writeProject(mcpFilter = listOf("atlassian"))
            engineWith(ToolType.CLAUDE).process(locations())
            val deployed = destination.resolve(".mcp.json").readText()
            // - a second server and a restriction, so the tool has two MCP files whose entries the ledger would have to record
            writeProject(mcpFilter = listOf("atlassian", "other"), mcpTools = mapOf("atlassian" to McpToolRestriction(deny = listOf("search"))))
            val ledgerDirectory = ledger.parentFile.toPath()
            Files.setPosixFilePermissions(ledgerDirectory, PosixFilePermissions.fromString("r-x------"))
            assumeFalse(Files.isWritable(ledgerDirectory), "the user running the tests writes every directory")

            // when
            val error = try {
                runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()
            } finally {
                Files.setPosixFilePermissions(ledgerDirectory, PosixFilePermissions.fromString("rwx------"))
            }

            // then
            assertThat(error).isInstanceOf(ExportFailedException::class.java).hasMessageContaining(ledger.absolutePath)
            assertThat((error as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.manifest) })
                .containsExactly(Triple("test-project", ToolType.CLAUDE, "MCP ledger"))
            assertThat(destination.resolve(".mcp.json")).hasContent(deployed)
            assertThat(destination.resolve(".claude/settings.json")).doesNotExist()
            assertThat(recordedEntries(ledger)).isEqualTo(mapOf(".mcp.json" to listOf("atlassian")))
        }

        @Test
        fun `should write the permissions of a replacing project afresh, and drop the settings file from the ledger once the restriction goes away`() {
            // given
            val restricted = mapOf("atlassian" to McpToolRestriction(deny = listOf("search")))
            writeProject(replace = true, mcpFilter = listOf("atlassian"), mcpTools = restricted)
            val settings = destination.resolve(".claude/settings.json")

            // when
            engineWith(ToolType.CLAUDE).process(locations())
            val ledgerAfterFirst = recordedEntries(ledger)
            engineWith(ToolType.CLAUDE).process(locations())
            val settingsAfterSecond = Json.parseToJsonElement(settings.readText())
            writeProject(replace = true, mcpFilter = listOf("atlassian"))
            engineWith(ToolType.CLAUDE).process(locations())

            // then
            assertThat(ledgerAfterFirst).isEqualTo(mapOf(".claude/settings.json" to listOf("deny:mcp__atlassian__search"), ".mcp.json" to listOf("atlassian")))
            assertThat(settingsAfterSecond).isEqualTo(Json.parseToJsonElement("""{"permissions": {"deny": ["mcp__atlassian__search"]}}"""))
            assertThat(settings).doesNotExist()
            assertThat(recordedEntries(ledger)).isEqualTo(mapOf(".mcp.json" to listOf("atlassian")))
        }

        /**
         * A ledger can arrive with a pull: one the engine did not write names entries whose content it does not record, and removes nothing, deny rules of settings.json included.
         */
        @Test
        fun `should remove nothing on the word of a ledger the engine did not write from a project that declares no mcps block, warning for each entry it names`() {
            // given
            val settings = destination.resolve(".claude/settings.json")
            settings.parentFile.mkdirs()
            settings.writeText("{\"permissions\": {\"allow\": [\"Bash(ls:*)\"], \"deny\": [\"mcp__mine__delete_everything\", \"mcp__keep__x\", \"Read(./.env)\"]}}")
            val planted = "\"sha256:" + "0".repeat(64) + "\""
            ledger.parentFile.mkdirs()
            ledger.writeText(
                "{\"version\": 2, \"files\": {" + mcpFiles.joinToString { "\"$it\": {\"mine\": $planted}" } +
                    ", \".claude/settings.json\": {\"deny:mcp__mine__delete_everything\": $planted}}}",
            )
            val before = (mcpFiles + ".claude/settings.json").associateWith { destination.resolve(it).readText() }
            val ledgerWarnings = ListAppender<ILoggingEvent>().also { it.start() }
            val ledgerLogger = LoggerFactory.getLogger(McpLedger::class.java) as Logger
            ledgerLogger.addAppender(ledgerWarnings)

            // when
            val dryRun = try {
                infosOf(DryRunArtifactSink::class.java) { engineWith(*tools, dryRun = true).process(locations()) }.also { engineWith(*tools).process(locations()) }
            } finally {
                ledgerLogger.detachAppender(ledgerWarnings)
            }

            // then
            assertThat((mcpFiles + ".claude/settings.json").associateWith { destination.resolve(it).readText() }).isEqualTo(before)
            assertThat(dryRun).noneMatch { it.contains("MCP") && !it.contains("MCP ledger") }
            val warned = ledgerWarnings.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
            (mcpFiles + ".claude/settings.json").forEach { path ->
                assertThat(warned).describedAs(path).anyMatch { it.contains(destination.resolve(path).absolutePath) && it.contains("changed since the engine wrote it") }
            }
            // - the ledger recorded nothing the files hold, so nothing of it is left
            assertThat(ledger).doesNotExist()
        }

        @Test
        fun `should refuse a ledger of version 1, naming it and its version, and leave every MCP file of its target as it is`() {
            // given
            writeProject(mcpFilter = listOf("atlassian"))
            ledger.parentFile.mkdirs()
            ledger.writeText("{\"version\": 1, \"files\": {\".mcp.json\": [\"mine\"]}}")
            val before = mcpFiles.map { destination.resolve(it).readText() }

            // when
            val error = runCatching { engineWith(*tools).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining(ledger.absolutePath)
                .hasMessageContaining("version 1")
            assertThat(mcpFiles.map { destination.resolve(it).readText() }).isEqualTo(before)
            assertThat(destination.resolve("CLAUDE.md")).exists()
        }

        @Test
        fun `should never write the ledger in a dry run`() {
            // given
            writeProject(mcpFilter = listOf("atlassian"))

            // when
            engineWith(*tools, dryRun = true).process(locations())

            // then
            assertThat(destination.resolve(".ai-tools")).doesNotExist()
        }
    }

    /**
     * One MCP file of a target belongs to at most one deployment of a run: the one whose `mcps` block covers it.
     */
    @Nested
    inner class McpOwnership {

        @BeforeEach
        fun writeServer() {
            writeStdioServer("atlassian")
        }

        @Test
        fun `should fail every deployment whose mcps block covers a file another deployment of the run covers too, naming both, and write everything else`() {
            // given
            writeProject(mcpFilter = listOf("atlassian"))
            // - a second project deploying into the same directory, whose mcps block selects nothing
            writeProject("second-project", "second-project", destination.absolutePath, mcpFilter = emptyList())
            val mcpFile = destination.resolve(".mcp.json")

            // when
            val dryRunError = runCatching { engineWith(ToolType.CLAUDE, dryRun = true).process(locations()) }.exceptionOrNull()
            val error = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'${mcpFile.absolutePath}'")
                .hasMessageContaining("project 'second-project', project 'test-project'")
            assertThat(dryRunError).isInstanceOf(ExportFailedException::class.java).hasMessage(error?.message)
            assertThat((error as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.cause::class.java) }).containsExactlyInAnyOrder(
                Triple("test-project", ToolType.CLAUDE, ContendedMcpFileException::class.java),
                Triple("second-project", ToolType.CLAUDE, ContendedMcpFileException::class.java),
            )
            assertThat(errors().filter { it.contains("Not writing") && it.contains(mcpFile.absolutePath) }).hasSize(2)
            assertThat(mcpFile).doesNotExist()
            assertThat(destination.resolve(".ai-tools")).doesNotExist()
            assertThat(destination.resolve("CLAUDE.md")).exists()
        }

        @Test
        fun `should fail both deployments whose mcps blocks cover one file through a linked project directory, and write none of their MCP files`() {
            // given
            destination.mkdirs()
            val linked = tempDir.resolve("linked-destination")
            Files.createSymbolicLink(linked, destination.toPath())
            writeStdioServer("beta")
            writeProject(mcpFilter = listOf("atlassian"))
            writeProject("second-project", "second-project", linked.toFile().absolutePath, mcpFilter = listOf("beta"))

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(ExportFailedException::class.java).hasMessageContaining("project 'second-project', project 'test-project'")
            assertThat((error as ExportFailedException).failures.map { it.deploymentId to it.cause::class.java }).containsExactlyInAnyOrder(
                "test-project" to ContendedMcpFileException::class.java,
                "second-project" to ContendedMcpFileException::class.java,
            )
            assertThat(destination.resolve(".mcp.json")).doesNotExist()
            assertThat(destination.resolve(".ai-tools")).doesNotExist()
        }

        @Test
        fun `should fail every user deployment whose mcps block covers a file of the home another user deployment covers too`() {
            // given
            writeProject(tools = emptyList())
            writeUserDeployment(tools = listOf("codex"), mcpFilter = listOf("atlassian"))
            writeUserDeployment("second", tools = listOf("claude", "codex"), mcpFilter = emptyList())
            val codexConfig = userHome.resolve(".codex/config.toml")

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'${codexConfig.absolutePath}'")
                .hasMessageContaining("user deployment 'globals', user deployment 'second'")
            assertThat((error as ExportFailedException).failures.filter { it.cause is ContendedMcpFileException }.map { it.deploymentId to it.toolType })
                .containsExactlyInAnyOrder("globals" to ToolType.CODEX, "second" to ToolType.CODEX)
            assertThat(codexConfig).doesNotExist()
        }

        @Test
        fun `should never let a deployment without an mcps block remove the entries another deployment of the run writes into the same file`() {
            // given
            writeProject(mcpFilter = listOf("atlassian"))
            writeProject("second-project", "second-project", destination.absolutePath)

            // when
            val written = infosOf(FileSystemArtifactSink::class.java) {
                engineWith(ToolType.CLAUDE).process(locations())
                engineWith(ToolType.CLAUDE).process(locations())
            }

            // then
            assertThat(destination.resolve(".mcp.json")).content().contains("\"atlassian\"")
            assertThat(recordedEntries(destination.resolve(".ai-tools/mcp-ledger.json"))).isEqualTo(mapOf(".mcp.json" to listOf("atlassian")))
            assertThat(written).noneMatch { it.contains("removing") }
        }
    }

    /**
     * The tools of a selected server a deployment allows and denies: `enabled_tools` and `disabled_tools` for Codex, `permissions` of `settings.json` for Claude Code.
     */
    @Nested
    inner class McpToolRestrictions {

        private val restriction =
            mapOf("github" to McpToolRestriction(allow = listOf("get_me"), deny = listOf("delete_repository")))
        private val settings get() = destination.resolve(".claude/settings.json")

        @BeforeEach
        fun writeServer() {
            writeYaml(
                "mcps/github.yml",
                "id: github\ndescription: GitHub\ntransport:\n  type: http\n  url: https://api.githubcopilot.com/mcp/\n  headers:\n    Authorization: 'Bearer \${GITHUB_TOKEN}'\n" +
                    "variables:\n  - name: GITHUB_TOKEN\n    description: Token\n    secret: true\n",
            )
        }

        @Test
        fun `should restrict the tools for Codex and as permissions of Claude Code, keeping foreign permissions and every other byte, and remove only its own once the restriction goes away`() {
            // given
            writeProject(mcpFilter = listOf("github"), mcpTools = restriction)
            settings.parentFile.mkdirs()
            val existing = "{\n  \"permissions\": {\n    \"allow\": [\n      \"Bash(git status)\"\n    ],\n    \"deny\": [\n      \"mcp__playwright__browser_close\"\n    ]\n  },\n  \"model\": \"opus\"\n}\n"
            settings.writeText(existing)

            // when
            engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations())
            val restricted = settings.readText()
            val codex = destination.resolve(".codex/config.toml").readText()
            writeProject(mcpFilter = listOf("github"))
            engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations())

            // then
            assertThat(codex).contains("enabled_tools = [\"get_me\"]").contains("disabled_tools = [\"delete_repository\"]")
            // - Claude Code gets the denied tools only: an allow entry would approve calls without a prompt, and is never written
            assertThat(restricted).isEqualTo(
                "{\n  \"permissions\": {\n    \"allow\": [\n      \"Bash(git status)\"\n    ],\n    \"deny\": [\n      \"mcp__playwright__browser_close\",\n      \"mcp__github__delete_repository\"\n    ]\n  },\n  \"model\": \"opus\"\n}\n",
            )
            assertThat(settings).hasContent(existing)
            assertThat(destination.resolve(".codex/config.toml")).content().doesNotContain("enabled_tools").doesNotContain("disabled_tools")
            assertThat(recordedEntries(destination.resolve(".ai-tools/mcp-ledger.json"))).isEqualTo(mapOf(".codex/config.toml" to listOf("github"), ".mcp.json" to listOf("github")))
        }

        @Test
        fun `should never write an allow entry for Claude Code, and report the allowed tools as skipped with the reason, in a dry run as in a deploy`() {
            // given
            writeProject(mcpFilter = listOf("github"), mcpTools = mapOf("github" to McpToolRestriction(allow = listOf("get_me"))))
            writeUserDeployment(tools = listOf("claude"), mcpFilter = listOf("github"), mcpTools = mapOf("github" to McpToolRestriction(allow = listOf("get_me"))))

            // when
            engineWith(ToolType.CLAUDE, dryRun = true).process(locations())
            val dryRunWarnings = warnings()
            logAppender.list.clear()
            engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations())

            // then
            val reason = "Claude Code has no list of the tools a server may offer; only 'deny' is rendered."
            assertThat(dryRunWarnings).containsExactly(
                "test-project: claude does not apply the 'allow' list of the MCP server(s) [github]: $reason",
                "globals: claude does not apply the 'allow' list of the MCP server(s) [github]: $reason",
            )
            assertThat(warnings()).contains("test-project: claude does not apply the 'allow' list of the MCP server(s) [github]: $reason")
            assertThat(settings).doesNotExist()
            assertThat(userHome.resolve(".claude/settings.json")).doesNotExist()
            assertThat(destination.resolve(".codex/config.toml")).content().contains("enabled_tools = [\"get_me\"]")
        }

        /**
         * A deny rule of the user blocks a call in every scope, so the engine never takes one over: it denies what it was asked to beside it, and removes only what it wrote.
         */
        @Test
        fun `should keep the deny rules written by hand for a server it restricts, and name every entry it removes by its full text`() {
            // given
            val existing = "{\"permissions\": {\"deny\": [\"mcp__github__drop_database\", \"mcp__github__delete_repository\"]}}"
            settings.parentFile.mkdirs()
            settings.writeText(existing)
            writeProject(mcpFilter = listOf("github"), mcpTools = mapOf("github" to McpToolRestriction(deny = listOf("delete_repository", "push"))))

            // when
            engineWith(ToolType.CLAUDE).process(locations())
            val restricted = settings.readText()
            writeProject(mcpFilter = listOf("github"))
            val removals = infosOf(DryRunArtifactSink::class.java) { engineWith(ToolType.CLAUDE, dryRun = true).process(locations()) } +
                infosOf(FileSystemArtifactSink::class.java) { engineWith(ToolType.CLAUDE).process(locations()) }

            // then
            assertThat(restricted).isEqualTo("{\"permissions\": {\"deny\": [\"mcp__github__drop_database\", \"mcp__github__delete_repository\", \"mcp__github__push\"]}}")
            assertThat(settings).hasContent(existing)
            assertThat(removals.filter { it.contains(settings.absolutePath) }).hasSize(2).allSatisfy { assertThat(it).contains("MCP tool permissions [], removing [mcp__github__push]") }
        }

        @Test
        fun `should create the settings file holding only the permissions when the project has none`() {
            // given
            writeProject(mcpFilter = listOf("github"), mcpTools = restriction)

            // when
            engineWith(ToolType.CLAUDE).process(locations())

            // then
            assertThat(Json.parseToJsonElement(settings.readText())).isEqualTo(
                Json.parseToJsonElement("""{"permissions": {"deny": ["mcp__github__delete_repository"]}}"""),
            )
        }

        @Test
        fun `should write the restrictions of a user deployment into the settings file of the home, and record it in the ledger of the home`() {
            // given
            writeUserDeployment(tools = listOf("claude"), mcpFilter = listOf("github"), mcpTools = restriction)
            val homeSettings = userHome.resolve(".claude/settings.json")

            // when
            engineWith(ToolType.CLAUDE).process(locations())
            writeUserDeployment(tools = listOf("claude"), mcpFilter = listOf("github"))
            val restricted = Json.parseToJsonElement(homeSettings.readText())
            val ledgerAfterRestriction = recordedEntries(userHome.resolve(".ai-tools/mcp-ledger.json"))
            engineWith(ToolType.CLAUDE).process(locations())

            // then
            assertThat(restricted).isEqualTo(
                Json.parseToJsonElement("""{"permissions": {"deny": ["mcp__github__delete_repository"]}}"""),
            )
            assertThat(ledgerAfterRestriction).isEqualTo(mapOf(".claude.json" to listOf("github"), ".claude/settings.json" to listOf("deny:mcp__github__delete_repository")))
            // - the file held nothing but the restriction, so it goes with it
            assertThat(homeSettings).doesNotExist()
            assertThat(recordedEntries(userHome.resolve(".ai-tools/mcp-ledger.json"))).isEqualTo(mapOf(".claude.json" to listOf("github")))
        }

        @ParameterizedTest
        @CsvSource(
            // - a server whose id holds two underscores
            "a__b, get_me",
            // - a tool whose name holds two underscores
            "github, get__me",
        )
        fun `should fail a deployment whose restriction names a server or a tool holding two underscores, which separate the two in a permission entry`(
            server: String,
            tool: String,
        ) {
            // given
            writeStdioServer("a__b")
            writeProject(mcpFilter = listOf(server), mcpTools = mapOf(server to McpToolRestriction(allow = listOf(tool))))

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("Project 'test-project'")
                .hasMessageContaining("MCP server '$server'")
                .hasMessageContaining("'__'")
            assertThat(destination).doesNotExist()
        }

        @Test
        fun `should never touch the settings file of a project that restricts nothing`() {
            // given
            writeProject(mcpFilter = listOf("github"))
            settings.parentFile.mkdirs()
            val existing = "{\"permissions\":{\"allow\":[\"mcp__github__get_me\"]}}"
            settings.writeText(existing)

            // when
            engineWith(ToolType.CLAUDE).process(locations())

            // then
            assertThat(settings).hasContent(existing)
        }

        @Test
        fun `should fail a deployment that restricts a server it does not select, naming the deployment and the server, and write none of it`() {
            // given
            writeStdioServer("atlassian")
            writeProject(mcpFilter = listOf("atlassian"), mcpTools = restriction)
            val laterDestination = tempDir.resolve("later-destination").toFile()
            writeProject("later-project", "later-project", laterDestination.absolutePath)
            writeUserDeployment(tools = listOf("claude"), mcpFilter = emptyList(), mcpTools = restriction)

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("Project 'test-project' restricts the tools of the MCP server 'github'")
                .hasMessageContaining("User deployment 'globals' restricts the tools of the MCP server 'github'")
            assertThat(destination).doesNotExist()
            assertThat(userHome).doesNotExist()
            assertThat(laterDestination.resolve("CLAUDE.md")).exists()
        }

        @ParameterizedTest
        @CsvSource(
            // - a space
            "'get me'",
            // - a character a permission rule would read as syntax
            "'get_me(x)'",
        )
        fun `should fail a deployment that names a tool no MCP server can have`(tool: String) {
            // given
            writeProject(mcpFilter = listOf("github"), mcpTools = mapOf("github" to McpToolRestriction(allow = listOf("\"$tool\""))))

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'test-project'")
                .hasMessageContaining("'github'")
                .hasMessageContaining("A-Z a-z 0-9 _ - .")
            assertThat(destination).doesNotExist()
        }

        @ParameterizedTest
        @CsvSource("github_copilot", "cursor")
        fun `should report a tool that cannot restrict the tools of a server as skipped for the restriction, with its reason`(
            tool: String,
        ) {
            // given
            writeProject(mcpFilter = listOf("github"), mcpTools = restriction)

            // when
            engineWith(ToolType.entries.single { it.serialName == tool }).process(locations())

            // then
            assertThat(warnings()).singleElement().satisfies({ assertThat(it).contains("test-project: $tool ").contains("[github]").contains("restrict") })
        }
    }

    /**
     * The MCP servers an agent uses: listed in the frontmatter of Claude Code and GitHub Copilot, and reported as skipped for every other tool.
     */
    @Nested
    inner class AgentMcpServers {

        @BeforeEach
        fun writeServer() {
            writeStdioServer("github")
            writeAgent("reviewer", "base", mcps = listOf("github"))
        }

        @Test
        fun `should attach the servers an agent uses in Claude Code, and report every other tool as skipped once, naming the agent`() {
            // given
            writeAgent("helper", "base", mcps = listOf("github"))
            writeProject(mcpFilter = listOf("github"))

            // when
            engineWith(*ToolType.entries.toTypedArray()).process(locations())

            // then
            assertThat(destination.resolve(".claude/agents/reviewer.md")).content().contains("\nmcpServers: [github]\n")
            // - a Copilot agent without tools keeps its built-in tools and gets every server of .vscode/mcp.json
            assertThat(destination.resolve(".github/agents/reviewer.agent.md")).content().doesNotContain("tools:")
            assertThat(warnings().filter { it.contains("test-project: github_copilot ") && it.contains("agent") }).singleElement().satisfies({
                assertThat(it).contains("'helper'").contains("'reviewer'").contains("a 'tools' list would remove its built-in tools")
            })
            assertThat(warnings()).noneMatch { it.contains("test-project: claude ") && it.contains("agent") }
            listOf("codex", "cursor", "windsurf", "antigravity").forEach { tool ->
                assertThat(warnings().filter { it.contains("test-project: $tool ") && it.contains("agent") })
                    .describedAs(tool)
                    .singleElement()
                    .satisfies({ assertThat(it).contains("'helper'").contains("'reviewer'") })
            }
        }

        @Test
        fun `should fail a project that deploys an agent using a server it does not select, naming the agent, the server and the project, and deploy the others`() {
            // given
            val laterDestination = tempDir.resolve("later-destination").toFile()
            writeProject("later-project", "later-project", laterDestination.absolutePath, mcpFilter = listOf("github"))

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("Project 'test-project' deploys the agent 'reviewer', which uses the MCP server 'github'")
            assertThat(destination).doesNotExist()
            assertThat(laterDestination.resolve(".claude/agents/reviewer.md")).content().contains("mcpServers: [github]")
        }

        @Test
        fun `should fail a user deployment that deploys an agent using a server it does not select`() {
            // given
            writeProject(tools = emptyList())
            writeUserDeployment(tools = listOf("claude"))

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("User deployment 'globals' deploys the agent 'reviewer', which uses the MCP server 'github'")
            assertThat(userHome).doesNotExist()
        }

        @Test
        fun `should attach the servers an agent uses in the user scope of Claude Code, and report Codex as skipped once, naming the agent`() {
            // given
            writeProject(tools = emptyList())
            writeUserDeployment(tools = listOf("claude", "codex"), mcpFilter = listOf("github"))

            // when
            engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations())

            // then
            assertThat(userHome.resolve(".claude/agents/reviewer.md")).content().contains("\nmcpServers: [github]\n")
            assertThat(warnings().filter { it.contains("globals: codex ") && it.contains("agent") }).singleElement().satisfies({
                assertThat(it).contains("'reviewer'").contains("skills")
            })
            assertThat(warnings()).noneMatch { it.contains("globals: claude ") && it.contains("agent") }
        }

        @Test
        fun `should report none of the MCP skips of a user deployment it refuses because an agent uses a server the deployment does not select`() {
            // given
            writeStdioServer("other")
            writeProject(tools = emptyList())
            // - GitHub Copilot gets no MCP server in the user scope, which a deployment that is exported reports as skipped
            writeUserDeployment(tools = listOf("claude", "github_copilot"), mcpFilter = listOf("other"))

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE, ToolType.GITHUB_COPILOT).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("User deployment 'globals' deploys the agent 'reviewer', which uses the MCP server 'github'")
            assertThat(warnings()).noneMatch { it.contains("globals: ") && it.contains("MCP") }
        }

        @Test
        fun `should refuse a user deployment whose agent uses a server it does not select even when none of its tools has a user scope`() {
            // given
            writeProject(tools = emptyList())
            writeUserDeployment(tools = listOf("cursor"))

            // when
            val error = runCatching { engineWith(ToolType.CURSOR).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("User deployment 'globals' deploys the agent 'reviewer', which uses the MCP server 'github'")
        }
    }

    /**
     * The directory every file of a tool lands in is checked before any of them is written, in a dry run as in a deploy.
     */
    @Nested
    inner class ToolDirectories {

        private val markers = mapOf(
            ToolType.CLAUDE to ".claude/agents/basic.md",
            ToolType.CODEX to ".codex/skills/agent-basic/SKILL.md",
            ToolType.GITHUB_COPILOT to ".github/agents/basic.agent.md",
            ToolType.CURSOR to ".cursor/rules/project.mdc",
            ToolType.WINDSURF to ".windsurf/rules/project.md",
            ToolType.ANTIGRAVITY to ".agent/rules/project.md",
        )

        @BeforeEach
        fun writeAgent() {
            writeAgent("basic", "base")
        }

        /**
         * The check of the tool directories keeps a deploy inside the project only if every file a tool writes lies at the top of the target or in a directory the tool lists: an artifact of each kind, with a companion file, MCP servers and a restriction, is written, and every file lands where the list says.
         */
        @ParameterizedTest
        @EnumSource(ToolType::class)
        fun `should write every file of a tool at the top of the target or in one of the directories it lists, in a project and in the home`(
            toolType: ToolType,
        ) {
            // given
            writePrompt("review", "base")
            writeDirectorySkill("jira-ticket", "task.txt", companionFileExists = true)
            writeFeature("test-project", "login.yml", "login")
            writeStdioServer("atlassian")
            val restriction =
                mapOf("atlassian" to McpToolRestriction(allow = listOf("search"), deny = listOf("delete")))
            writeProject(mcpFilter = listOf("atlassian"), mcpTools = restriction)
            writeUserDeployment(tools = listOf(toolType.serialName), mcpFilter = listOf("atlassian"), mcpTools = restriction)
            val adapter = ToolFactory.create(toolType)
            val userScope = adapter.userScope(userHome, UserDeploymentManifest(id = "globals", description = "d", metadata = ManifestMetadata(version = Version("1.0.0"))))

            // when
            engineWith(toolType).process(locations())

            // then
            val projectDirectories = adapter.toolDirectories(destination) + listOfNotNull(adapter.mcpConfig(destination)?.file?.parentFile, adapter.mcpPermissions(destination)?.file?.parentFile)
            assertWrittenWithin(destination, projectDirectories)
            userScope?.let { exporter ->
                assertWrittenWithin(userHome, exporter.toolDirectories + listOfNotNull(exporter.mcpConfig()?.file?.parentFile, exporter.mcpPermissions()?.file?.parentFile))
            }
        }

        /**
         * Fails unless every file below [root], the MCP ledger aside, lies directly in [root] or in one of [directories], or in the folder of one artifact directly below one of them, named after it, and unless every directory of [directories] was written into.
         */
        private fun assertWrittenWithin(root: File, directories: List<File>) {
            val listed = directories.map { it.absoluteFile.normalize() }.toSet()
            val ledgerDirectory = root.resolve(".ai-tools").absoluteFile
            val written = root
                .walkTopDown()
                .filter { it.isFile && !it.startsWith(ledgerDirectory) }
                .map { it.absoluteFile.normalize() }
                .toList()
            assertThat(written).isNotEmpty()
            written.forEach { file ->
                val parent = file.parentFile
                // - a skill, and an agent or a prompt rendered as one, is written as a folder of its own, named after it, with its companion files
                val artifactFolder = generateSequence(parent) { it.parentFile }.takeWhile { it != root.absoluteFile }.any { folder ->
                    folder.parentFile in listed && listOf("jira-ticket", "basic", "review", "login").any { folder.name.endsWith(it) }
                }
                assertThat(parent == root.absoluteFile.normalize() || parent in listed || artifactFolder).describedAs(file.relativeTo(root).path).isTrue()
            }
            listed.filter { it != root.absoluteFile.normalize() }.forEach { assertThat(it).describedAs(it.relativeTo(root).path).isDirectory() }
        }

        @ParameterizedTest
        @CsvSource(
            ".claude, CLAUDE, dangling",
            ".claude, CLAUDE, looping",
            ".claude, CLAUDE, file",
            ".github, GITHUB_COPILOT, dangling",
            ".github, GITHUB_COPILOT, looping",
            ".github, GITHUB_COPILOT, file",
            ".codex, CODEX, dangling",
            ".codex, CODEX, looping",
            ".codex, CODEX, file",
            ".cursor, CURSOR, dangling",
            ".cursor, CURSOR, looping",
            ".cursor, CURSOR, file",
            ".windsurf, WINDSURF, dangling",
            ".windsurf, WINDSURF, looping",
            ".windsurf, WINDSURF, file",
            ".agent, ANTIGRAVITY, dangling",
            ".agent, ANTIGRAVITY, looping",
            ".agent, ANTIGRAVITY, file",
            // - a link out of the project fails the same way
            ".codex, CODEX, outside",
        )
        fun `should fail only the tool and project whose tool directory cannot hold its files, with the same message in a dry run as in a deploy`(
            directory: String,
            toolType: ToolType,
            kind: String,
        ) {
            // given
            val laterDestination = tempDir.resolve("later-destination").toFile()
            writeProject("later-project", "later-project", laterDestination.absolutePath)
            destination.mkdirs()
            val broken = destination.resolve(directory)
            val outside = tempDir.resolve("out").toFile()
            breakDirectory(broken, kind, outside)

            // when
            val dryRunError = runCatching { engineWith(*ToolType.entries.toTypedArray(), dryRun = true).process(locations()) }.exceptionOrNull()
            val writtenByDryRun = listOf(laterDestination, destination.resolve("CLAUDE.md")).filter { it.exists() }
            val deployError = runCatching { engineWith(*ToolType.entries.toTypedArray()).process(locations()) }.exceptionOrNull()

            // then
            assertThat(deployError)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'${broken.absolutePath}'")
                .hasMessageContaining("writes no ${toolType.serialName} files of project 'test-project'")
            assertThat(dryRunError).isInstanceOf(ExportFailedException::class.java).hasMessage(deployError?.message)
            assertThat(writtenByDryRun).isEmpty()
            assertThat((deployError as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.manifest) })
                .containsExactly(Triple("test-project", toolType, "tool directory '$directory'"))
            // - every other tool of the project, and every tool of the later project, are written in full
            markers.filterKeys { it != toolType }.values.forEach { assertThat(destination.resolve(it)).describedAs(it).exists() }
            markers.values.forEach { assertThat(laterDestination.resolve(it)).describedAs(it).exists() }
            // - nothing was written through a link out of the project
            assertThat(outside.walkTopDown().filter { it.isFile }.toList()).isEmpty()
        }

        @ParameterizedTest
        @CsvSource(
            ".claude/agents, CLAUDE, outside",
            ".claude/agents, CLAUDE, file",
            ".claude/commands, CLAUDE, outside",
            ".claude/skills, CLAUDE, file",
            ".claude/workflows, CLAUDE, outside",
            ".codex/skills, CODEX, outside",
            ".codex/features, CODEX, file",
            ".github/agents, GITHUB_COPILOT, outside",
            ".github/prompts, GITHUB_COPILOT, file",
            ".github/instructions, GITHUB_COPILOT, outside",
            ".cursor/rules, CURSOR, outside",
            ".cursor/commands, CURSOR, file",
            ".cursor/features, CURSOR, outside",
            ".windsurf/rules, WINDSURF, outside",
            ".windsurf/workflows, WINDSURF, file",
            ".agent/rules, ANTIGRAVITY, outside",
            ".agent/workflows, ANTIGRAVITY, file",
        )
        fun `should fail only the tool and project whose directory below the tool directory leads outside the project or is not a directory, with the same message in a dry run as in a deploy`(
            directory: String,
            toolType: ToolType,
            kind: String,
        ) {
            // given
            val laterDestination = tempDir.resolve("later-destination").toFile()
            writeProject("later-project", "later-project", laterDestination.absolutePath)
            val broken = destination.resolve(directory)
            broken.parentFile.mkdirs()
            val outside = tempDir.resolve("out").toFile()
            breakDirectory(broken, kind, outside)

            // when
            val dryRunError = runCatching { engineWith(*ToolType.entries.toTypedArray(), dryRun = true).process(locations()) }.exceptionOrNull()
            val writtenByDryRun = listOf(laterDestination, destination.resolve("CLAUDE.md")).filter { it.exists() }
            val deployError = runCatching { engineWith(*ToolType.entries.toTypedArray()).process(locations()) }.exceptionOrNull()

            // then
            assertThat(deployError)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'${broken.absolutePath}'")
                .hasMessageContaining("writes no ${toolType.serialName} files of project 'test-project'")
            assertThat(dryRunError).isInstanceOf(ExportFailedException::class.java).hasMessage(deployError?.message)
            assertThat(writtenByDryRun).isEmpty()
            assertThat((deployError as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.manifest) })
                .containsExactly(Triple("test-project", toolType, "tool directory '$directory'"))
            markers.filterKeys { it != toolType }.values.forEach { assertThat(destination.resolve(it)).describedAs(it).exists() }
            markers.values.forEach { assertThat(laterDestination.resolve(it)).describedAs(it).exists() }
            // - nothing was written through a link out of the project
            assertThat(outside.walkTopDown().filter { it.isFile }.toList()).isEmpty()
        }

        @Test
        fun `should not check a directory below a tool directory the project replaces, since the replace removes it before anything is written`() {
            // given
            writeProject(replace = true)
            destination.resolve(".claude").mkdirs()
            destination.resolve(".claude/agents").writeText("not a directory\n")

            // when
            val dryRunError = runCatching { engineWith(ToolType.CLAUDE, dryRun = true).process(locations()) }.exceptionOrNull()
            val deployError = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            assertThat(dryRunError).isNull()
            assertThat(deployError).isNull()
            assertThat(destination.resolve(".claude/agents/basic.md")).exists()
        }

        /**
         * A replacing project whose tool directory links outside it fails that tool before the replace deletes anything, so nothing outside the project is ever deleted through the link.
         */
        @ParameterizedTest
        @CsvSource(
            ".codex, CODEX, skills/keep/SKILL.md",
            ".github, GITHUB_COPILOT, agents/keep.agent.md",
            ".cursor, CURSOR, rules/keep.mdc",
        )
        fun `should delete nothing outside the project when a replacing project links a tool directory there`(
            directory: String,
            toolType: ToolType,
            kept: String,
        ) {
            // given
            writeProject(replace = true)
            destination.mkdirs()
            val outside = tempDir.resolve("out").toFile()
            val keptFile = outside.resolve(kept).also { it.parentFile.mkdirs() }
            keptFile.writeText("keep\n")
            Files.createSymbolicLink(destination.resolve(directory).toPath(), outside.toPath())

            // when
            val dryRunError = runCatching { engineWith(toolType, dryRun = true).process(locations()) }.exceptionOrNull()
            val deployError = runCatching { engineWith(toolType).process(locations()) }.exceptionOrNull()

            // then
            assertThat((deployError as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.manifest) })
                .containsExactly(Triple("test-project", toolType, "tool directory '$directory'"))
            assertThat(dryRunError).isInstanceOf(ExportFailedException::class.java).hasMessage(deployError.message)
            assertThat(keptFile).hasContent("keep\n")
            assertThat(outside.walkTopDown().filter { it.isFile }.toList()).containsExactly(keptFile)
        }

        @Test
        fun `should pass a dry run of a replacing project whose claude directory links outside it and holds the settings file it restricts, as the deploy passes`() {
            // given
            writeStdioServer("github")
            writeProject(replace = true, mcpFilter = listOf("github"), mcpTools = mapOf("github" to McpToolRestriction(deny = listOf("get_me"))))
            destination.mkdirs()
            val outside = tempDir.resolve("out").toFile().also { it.mkdirs() }
            val outsideSettings = outside.resolve("settings.json").also { it.writeText("{\"permissions\": {\"allow\": [\"mcp__github__mine\"]}}") }
            Files.createSymbolicLink(destination.resolve(".claude").toPath(), outside.toPath())

            // when
            val dryRunInfos = infosOf(DryRunArtifactSink::class.java) {
                val dryRunError = runCatching { engineWith(ToolType.CLAUDE, dryRun = true).process(locations()) }.exceptionOrNull()
                assertThat(dryRunError).isNull()
            }
            val deployError = runCatching { engineWith(ToolType.CLAUDE).process(locations()) }.exceptionOrNull()

            // then
            val settings = destination.resolve(".claude/settings.json")
            assertThat(dryRunInfos).contains("Would write MCP tool permissions [github] to ${settings.absolutePath}")
            assertThat(deployError).isNull()
            assertThat(outsideSettings).hasContent("{\"permissions\": {\"allow\": [\"mcp__github__mine\"]}}")
            assertThat(Files.isSymbolicLink(destination.resolve(".claude").toPath())).isFalse()
            assertThat(Json.parseToJsonElement(settings.readText())).isEqualTo(Json.parseToJsonElement("""{"permissions": {"deny": ["mcp__github__get_me"]}}"""))
        }

        @ParameterizedTest
        @CsvSource(
            ".claude/agents, CLAUDE",
            ".claude/commands, CLAUDE",
            ".claude/skills, CLAUDE",
            ".codex/skills, CODEX",
        )
        fun `should fail only the tool whose directory below its directory in the home is not a directory, in a dry run as in a deploy`(
            directory: String,
            toolType: ToolType,
        ) {
            // given
            writeUserDeployment(tools = listOf("claude", "codex"))
            val broken = userHome.resolve(directory)
            broken.parentFile.mkdirs()
            broken.writeText("not a directory\n")

            // when
            val dryRunError = runCatching { engineWith(ToolType.CLAUDE, ToolType.CODEX, dryRun = true).process(locations()) }.exceptionOrNull()
            val deployError = runCatching { engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations()) }.exceptionOrNull()

            // then
            assertThat(deployError)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'${broken.absolutePath}'")
                .hasMessageContaining("writes no ${toolType.serialName} files of user deployment 'globals'")
            assertThat(dryRunError).isInstanceOf(ExportFailedException::class.java).hasMessage(deployError?.message)
            assertThat((deployError as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.manifest) })
                .containsExactly(Triple("globals", toolType, "tool directory '$directory'"))
        }

        @Test
        fun `should follow a directory below a tool directory of the home that links outside it, as a dotfile repository does`() {
            // given
            writeUserDeployment(tools = listOf("claude"))
            userHome.resolve(".claude").mkdirs()
            val dotfiles = tempDir.resolve("dotfiles/agents").toFile().also { it.mkdirs() }
            Files.createSymbolicLink(userHome.resolve(".claude/agents").toPath(), dotfiles.toPath())

            // when
            engineWith(ToolType.CLAUDE).process(locations())

            // then
            assertThat(dotfiles.resolve("basic.md")).exists()
        }

        @ParameterizedTest
        @CsvSource("true", "false")
        fun `should check the directory of an MCP config file outside the tool directory only when the project writes that file`(
            selectsServers: Boolean,
        ) {
            // given
            writeStdioServer("atlassian")
            writeProject(mcpFilter = if (selectsServers) listOf("atlassian") else null)
            destination.mkdirs()
            Files.createSymbolicLink(destination.resolve(".vscode").toPath(), tempDir.resolve("out/missing"))

            // when
            val error = runCatching { engineWith(ToolType.GITHUB_COPILOT).process(locations()) }.exceptionOrNull()

            // then
            if (selectsServers) {
                assertThat((error as ExportFailedException).failures.map { it.manifest }).containsExactly("tool directory '.vscode'")
                assertThat(destination.resolve(".github")).doesNotExist()
            } else {
                assertThat(error).isNull()
                assertThat(destination.resolve(".github/agents/basic.agent.md")).exists()
            }
        }

        @ParameterizedTest
        @CsvSource(
            ".claude, CLAUDE, dangling",
            ".claude, CLAUDE, looping",
            ".claude, CLAUDE, file",
            ".codex, CODEX, dangling",
            ".codex, CODEX, looping",
            ".codex, CODEX, file",
        )
        fun `should fail only the tool whose directory in the home cannot hold its files, and deploy every other tool and project`(
            directory: String,
            toolType: ToolType,
            kind: String,
        ) {
            // given
            writeUserDeployment(tools = listOf("claude", "codex"))
            userHome.mkdirs()
            val broken = userHome.resolve(directory)
            breakDirectory(broken, kind, tempDir.resolve("out").toFile())
            val other = if (toolType == ToolType.CLAUDE) userHome.resolve(".codex/skills/agent-basic/SKILL.md") else userHome.resolve(".claude/agents/basic.md")

            // when
            val dryRunError = runCatching { engineWith(ToolType.CLAUDE, ToolType.CODEX, dryRun = true).process(locations()) }.exceptionOrNull()
            val deployError = runCatching { engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations()) }.exceptionOrNull()

            // then
            assertThat(deployError)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("'${broken.absolutePath}'")
                .hasMessageContaining("writes no ${toolType.serialName} files of user deployment 'globals'")
            assertThat(dryRunError).isInstanceOf(ExportFailedException::class.java).hasMessage(deployError?.message)
            assertThat((deployError as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.manifest) })
                .containsExactly(Triple("globals", toolType, "tool directory '$directory'"))
            assertThat(other).exists()
            assertThat(destination.resolve(".claude/agents/basic.md")).exists()
        }

        @Test
        fun `should follow a tool directory of the home that links outside it, as a dotfile repository does`() {
            // given
            writeUserDeployment(tools = listOf("codex"))
            userHome.mkdirs()
            val dotfiles = tempDir.resolve("dotfiles/codex").toFile().also { it.mkdirs() }
            Files.createSymbolicLink(userHome.resolve(".codex").toPath(), dotfiles.toPath())

            // when
            engineWith(ToolType.CODEX).process(locations())

            // then
            assertThat(dotfiles.resolve("skills/agent-basic/SKILL.md")).exists()
            assertThat(dotfiles.resolve("AGENTS.md")).exists()
        }

        private fun breakDirectory(directory: File, kind: String, outside: File) {
            when (kind) {
                "dangling" -> Files.createSymbolicLink(directory.toPath(), outside.resolve("missing").toPath())
                "looping" -> directory.resolveSibling("${directory.name}-loop").also { Files.createSymbolicLink(it.toPath(), directory.toPath()) }.let { Files.createSymbolicLink(directory.toPath(), it.toPath()) }
                "outside" -> Files.createSymbolicLink(directory.toPath(), outside.also { it.mkdirs() }.toPath())
                else -> directory.writeText("not a directory\n")
            }
        }
    }

    @Nested
    inner class UserScopeFailures {

        @Test
        fun `should collect a file the user scope cannot write as a failure of that tool, and go on with the next tool`() {
            // given
            writeAgent("basic", "base")
            writeUserDeployment(tools = listOf("claude", "codex"))
            // - a directory holding a file where the agent file of Claude Code belongs, which no write can replace; a regular file where the agents directory belongs is found by the check of the tool directories before anything is written
            userHome.resolve(".claude/agents/basic.md").mkdirs()
            userHome.resolve(".claude/agents/basic.md/keep").writeText("mine\n")

            // when
            val error = runCatching { engineWith(ToolType.CLAUDE, ToolType.CODEX).process(locations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ExportFailedException::class.java)
                .hasMessageContaining("no further claude files of user deployment 'globals'")
            assertThat((error as ExportFailedException).failures.map { Triple(it.deploymentId, it.toolType, it.cause::class.java) })
                .containsExactly(Triple("globals", ToolType.CLAUDE, ToolExportStoppedException::class.java))
            assertThat(userHome.resolve(".codex/skills/agent-basic/SKILL.md")).exists()
            assertThat(userHome.resolve(".codex/AGENTS.md")).exists()
        }
    }

    private fun locations() = Locations(
        agents = listOf(workspace.resolve("agents")),
        deployments = listOf(workspace.resolve("deployments")),
        prompts = listOf(workspace.resolve("prompts")),
        rulesets = listOf(workspace.resolve("rulesets")),
        fragments = emptyList(),
        skills = listOf(workspace.resolve("skills")),
        mcps = listOf(workspace.resolve("mcps")),
    )

    private fun agentFile(id: String) = destination.resolve(".claude/agents/$id.md")

    /**
     * Returns the entries the MCP ledger [file] records for each file it names, sorted, once it is checked to be a ledger of version 2 whose every entry carries a fingerprint.
     */
    private fun recordedEntries(file: File): Map<String, List<String>> {
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        assertThat(root["version"]).isEqualTo(JsonPrimitive(2))
        return root.getValue("files").jsonObject.mapValues { (_, entries) ->
            entries.jsonObject.values.forEach { assertThat(it.jsonPrimitive.content).matches("sha256:[0-9a-f]{64}") }
            entries.jsonObject.keys.sorted()
        }
    }

    private fun promptFile(id: String) = destination.resolve(".claude/commands/$id.md")

    private fun cursorProjectFile() = destination.resolve(".cursor/rules/project.mdc")

    private fun cursorAgentFile(id: String) = destination.resolve(".cursor/rules/agent-$id.mdc")

    private fun warnings() = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun errors() = logAppender.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }

    private fun infos() = logAppender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }

    private fun writeRuleset(id: String, fileName: String = "$id.yml") = writeYaml(
        "rulesets/$fileName",
        "id: $id\ndescription: A ruleset\nrules:\n  - A rule from $id.\n",
    )

    private fun writeAgent(id: String, ruleset: String, mcps: List<String> = emptyList()) = writeYaml(
        "agents/$id.yml",
        "id: $id\ndescription: An agent\nrulesets:\n  - $ruleset\n" +
            "persona: A persona\nprompt: An agent prompt\n" +
            (if (mcps.isEmpty()) "" else "mcps: [${mcps.joinToString()}]\n"),
    )

    /**
     * Writes an inline stdio MCP server [id] started as [command], which reads the secret variable [secret] when one is named.
     */
    private fun writeStdioServer(id: String, command: String = "$id-server", secret: String? = null) = writeYaml(
        "mcps/$id.yml",
        "id: $id\ndescription: The $id server\ntransport:\n  type: stdio\n  command: $command\n" +
            (secret?.let { "variables:\n  - name: $it\n    description: Token\n    secret: true\n" } ?: ""),
    )

    /**
     * An engine of this class deploying through [toolTypes], each built for a dry run when [dryRun] is set.
     */
    private fun engineWith(
        vararg toolTypes: ToolType,
        dryRun: Boolean = false,
        variables: VariableResolver = VariableResolver(emptyMap(), emptyEnvironment),
    ) = ToolsEngine(
        workspace,
        variables = variables,
        userHome = userHome,
        tools = toolTypes.map { ToolFactory.create(it, dryRun) },
        dryRun = dryRun,
    )

    /**
     * Runs [block] with a list appender on the logger of [type], returning the formatted INFO lines it logged.
     */
    private fun infosOf(type: Class<*>, block: () -> Unit): List<String> {
        val appender = ListAppender<ILoggingEvent>()
        val logger = LoggerFactory.getLogger(type) as Logger
        appender.start()
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }
    }

    private fun writePrompt(id: String, ruleset: String) = writeYaml(
        "prompts/$id.yml",
        "id: $id\ndescription: A prompt\nrulesets:\n  - $ruleset\ncontent: Some prompt content\n",
    )

    private fun writeStandaloneSkill(id: String, file: String) = writeYaml(
        "skills/$id.yml",
        "id: $id\ndescription: A skill\nsections:\n  - text: Some skill content\n" +
            "files:\n  - path: $file\n",
    )

    private fun writeDirectorySkill(id: String, file: String, companionFileExists: Boolean): File {
        val manifest = writeYaml(
            "skills/$id/skill.yml",
            "id: $id\ndescription: A skill\nsections:\n  - text: Some skill content\n" +
                "files:\n  - path: $file\n",
        )
        if (companionFileExists) {
            manifest.parentFile.resolve(file).writeText("Companion content.\n")
        }
        return manifest
    }

    /**
     * @param root the configured `locations.deployments` directory to write this project under, which decides when the loader reads it relative to the projects of another root
     * @param skillFilter the skill ids the project whitelists, or `null` for every skill there is
     * @param mcpFilter the MCP server ids the project whitelists, or `null` for every MCP server there is
     */
    // A test builder: every parameter is one field of the manifest with the default a test rarely needs to change.
    @Suppress("LongParameterList")
    private fun writeProject(
        directoryName: String = "test-project",
        id: String = "test-project",
        deployDirectory: String = destination.absolutePath,
        tools: List<String>? = null,
        root: String = "deployments",
        replace: Boolean = false,
        skillFilter: List<String>? = null,
        mcpFilter: List<String>? = null,
        mcpTools: Map<String, McpToolRestriction> = emptyMap(),
    ) = writeYaml(
        "$root/$directoryName/project.yml",
        "id: $id\ndescription: A project\n" +
            "context:\n  documentation:\n    readme: README.md\n" +
            "deploy:\n  directory: \"$deployDirectory\"\n  replace: $replace\n" + toolsDeclaration(tools) +
            (whitelistDeclaration("skills", skillFilter) + mcpsDeclaration(mcpFilter, mcpTools)).lineSequence().filter { it.isNotEmpty() }.joinToString("") { "  $it\n" },
    )

    /**
     * Renders the `mcps` block: absent for `null`, otherwise a whitelist of [ids] followed by the restriction of each server of [tools].
     */
    private fun mcpsDeclaration(ids: List<String>?, tools: Map<String, McpToolRestriction>): String {
        if (ids == null) return ""
        val restrictions = tools.entries.joinToString("") { (server, restriction) ->
            "    $server:\n" +
                (if (restriction.allow.isEmpty()) "" else "      allow: [${restriction.allow.joinToString()}]\n") +
                (if (restriction.deny.isEmpty()) "" else "      deny: [${restriction.deny.joinToString()}]\n")
        }
        return whitelistDeclaration("mcps", ids) + if (restrictions.isEmpty()) "" else "  tools:\n$restrictions"
    }

    /**
     * Renders the optional `deploy.tools` list: absent for `null`, an explicit empty list for an empty one, since the two mean opposite things to the engine.
     */
    private fun toolsDeclaration(tools: List<String>?) = when {
        tools == null -> ""
        tools.isEmpty() -> "  tools: []\n"
        else -> tools.joinToString(separator = "", prefix = "  tools:\n") { "    - $it\n" }
    }

    /**
     * Writes a `user.yml` beside the projects, which is how the loader tells the two kinds apart.
     *
     * @param agentFilter the agent ids the deployment whitelists, or `null` for every agent there is
     */
    // A test builder, see writeProject.
    @Suppress("LongParameterList")
    private fun writeUserDeployment(
        id: String = "globals",
        directoryName: String = id,
        tools: List<String>? = listOf("claude"),
        agentFilter: List<String>? = null,
        promptFilter: List<String>? = null,
        replace: Boolean = false,
        mcpFilter: List<String>? = null,
        mcpTools: Map<String, McpToolRestriction> = emptyMap(),
    ) = writeYaml(
        "deployments/$directoryName/user.yml",
        "id: $id\ndescription: A user deployment\nreplace: $replace\n" + userToolsDeclaration(tools) +
            whitelistDeclaration("agents", agentFilter) + whitelistDeclaration("prompts", promptFilter) + mcpsDeclaration(mcpFilter, mcpTools),
    )

    /**
     * Renders the optional `tools` list of a user deployment, which sits at the top level rather than under `deploy`.
     */
    private fun userToolsDeclaration(tools: List<String>?) = when {
        tools == null -> ""
        tools.isEmpty() -> "tools: []\n"
        else -> tools.joinToString(separator = "", prefix = "tools:\n") { "  - $it\n" }
    }

    /**
     * Renders a whitelist filter for one kind: absent for `null`, which selects everything of that kind, and an empty list of ids - which selects nothing - for an empty one.
     */
    private fun whitelistDeclaration(kind: String, ids: List<String>?) = when {
        ids == null -> ""
        ids.isEmpty() -> "$kind:\n  filter:\n    - type: whitelist\n      ids: []\n"
        else -> ids.joinToString(
            separator = "",
            prefix = "$kind:\n  filter:\n    - type: whitelist\n      ids:\n",
        ) { "        - $it\n" }
    }

    private fun writeFeature(projectDirectoryName: String, fileName: String, id: String) = writeYaml(
        "deployments/$projectDirectoryName/features/$fileName",
        "id: $id\ndescription: A feature\nprompt: A feature prompt\n",
    )

    private fun writeTextSkill(id: String) = writeYaml(
        "skills/$id.yml",
        "id: $id\ndescription: A skill\nsections:\n  - text: Some skill content\n",
    )

    private fun writePointerSkill(id: String, source: String) = writeYaml(
        "skills/$id/skill.yml",
        "id: $id\nsource: \"$source\"\n",
    )

    private fun writeSourceFolderWithTemplate(directory: File): File {
        writePlainSkill(directory, "jira-ticket")
        directory.resolve("templates").mkdirs()
        directory.resolve("templates/task.txt").writeText("Task template.\n")
        return directory
    }

    private fun writePlainSkill(directory: File, name: String): File {
        directory.mkdirs()
        directory.resolve("SKILL.md").writeText("---\nname: $name\ndescription: The $name skill\n---\n\n# $name\n\nPlain body.\n")
        return directory
    }

    private fun writeYaml(relativePath: String, content: String): File {
        val file = workspace.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(content + "metadata:\n  version: 1.0.0\n")
        return file
    }

    companion object {
        /** A value only the environment of a test run carries for a secret variable, which no generated file may ever contain. */
        private const val SECRET_VALUE = "s3cr3t-value-that-must-never-be-written"

        /** A value the environment or the config of a leak scenario carries, which no file, log line or error may ever contain. */
        private const val LEAKED_VALUE = "leaked-4f1c9e"

        /** The names the environment of a leak scenario carries [LEAKED_VALUE] under. */
        private val LEAKING_NAMES =
            setOf("GITHUB_TOKEN", "ATLASSIAN_TOKEN", "ATLASSIAN_KEY", "DEMO_KEY", "AWS_SECRET_ACCESS_KEY", "EXAMPLE_TOKEN", "JIRA_VERIFY_SSL", "X")
    }
}
