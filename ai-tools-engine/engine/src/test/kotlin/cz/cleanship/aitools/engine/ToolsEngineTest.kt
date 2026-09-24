package cz.cleanship.aitools.engine

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.UnresolvedVariableException
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.io.ArtifactPathException
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.serialName
import cz.cleanship.aitools.engine.services.DryRunArtifactSink
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
import cz.cleanship.aitools.engine.services.ManifestLoadingException
import cz.cleanship.aitools.engine.tools.ToolFactory
import cz.cleanship.aitools.engine.tools.adapters.claude.ClaudeAdapter
import cz.cleanship.aitools.engine.tools.adapters.codex.CodexAdapter
import cz.cleanship.aitools.engine.tools.adapters.cursor.CursorAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter
import cz.cleanship.aitools.engine.utils.contentSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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
            // - Claude and Codex replace their whole tool directory, which is the link here
            "CLAUDE, .claude, .claude/skills/jira-ticket, CLAUDE.md, true",
            "CLAUDE, .claude, .claude/skills/jira-ticket, CLAUDE.md, false",
            "CODEX, .codex, .codex/skills/skill-jira-ticket, AGENTS.md, true",
            "CODEX, .codex, .codex/skills/skill-jira-ticket, AGENTS.md, false",
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
            "CODEX, .codex, AGENTS.md, true",
            "CODEX, .codex, AGENTS.md, false",
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

    private fun locations() = Locations(
        agents = listOf(workspace.resolve("agents")),
        deployments = listOf(workspace.resolve("deployments")),
        prompts = listOf(workspace.resolve("prompts")),
        rulesets = listOf(workspace.resolve("rulesets")),
        fragments = emptyList(),
        skills = listOf(workspace.resolve("skills")),
    )

    private fun agentFile(id: String) = destination.resolve(".claude/agents/$id.md")

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

    private fun writeAgent(id: String, ruleset: String) = writeYaml(
        "agents/$id.yml",
        "id: $id\ndescription: An agent\nrulesets:\n  - $ruleset\n" +
            "persona: A persona\nprompt: An agent prompt\n",
    )

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
    ) = writeYaml(
        "$root/$directoryName/project.yml",
        "id: $id\ndescription: A project\n" +
            "context:\n  documentation:\n    readme: README.md\n" +
            "deploy:\n  directory: \"$deployDirectory\"\n  replace: $replace\n" + toolsDeclaration(tools) +
            whitelistDeclaration("skills", skillFilter).lineSequence().filter { it.isNotEmpty() }.joinToString("") { "  $it\n" },
    )

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
    ) = writeYaml(
        "deployments/$directoryName/user.yml",
        "id: $id\ndescription: A user deployment\nreplace: $replace\n" + userToolsDeclaration(tools) +
            whitelistDeclaration("agents", agentFilter) + whitelistDeclaration("prompts", promptFilter),
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
}
