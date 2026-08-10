package cz.cleanship.aitools.engine.tools.adapters.claude

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.textOnlySkill
import cz.cleanship.aitools.engine.data.userDeployment
import cz.cleanship.aitools.engine.io.ArtifactPathException
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectContext
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectDocumentation
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import cz.cleanship.aitools.engine.tools.UserInstructionsContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class ClaudeAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val printers = Printers
    private lateinit var targetDir: File
    private lateinit var claudeAdapter: ClaudeAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".claude").toFile()
        claudeAdapter = ClaudeAdapter(printers)
    }

    @Test
    fun `should output project memory file`() {
        // given
        val project = ProjectManifest(
            id = "test-project",
            description = "Test Project",
            metadata = ManifestMetadata(version = Version("1.0.0")),
            context = ProjectContext(
                rules = listOf("Rule one."),
                overview = "Overview",
                documentation = ProjectDocumentation(readme = "README.md"),
            ),
            deploy = ProjectDeploy(directory = tempDir.toString()),
        )

        // when
        claudeAdapter.export(tempDir.toFile(), GlobalContext(project))

        // then
        val content = tempDir.resolve("CLAUDE.md").toFile().readText()
        assertThat(content).contains("# test-project")
        assertThat(content).contains("Test Project")
        assertThat(content).contains("## Rules")
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        claudeAdapter.export(tempDir.toFile(), prompt)

        // then
        assertThat(targetDir.resolve("commands/${prompt.prompt.id}.md").readText().trim()).isEqualTo(expectedPrompt.trim())
    }

    @Test
    fun `should output an agent subcommand`() {
        // given
        val agent = agent

        // when
        claudeAdapter.export(tempDir.toFile(), AgentContext(agent, rulesets))

        // then
        val content = targetDir.resolve("agents/${agent.id}.md").readText().trim()
        assertThat(content).startsWith("---")
        assertThat(content).contains("name: ${agent.id}")
        assertThat(content).contains("description: ${agent.description.replace("\n", " ")}")
        assertThat(content).contains(expectedAgent.trim())
    }

    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        claudeAdapter.export(tempDir.toFile(), featureContext)

        // then
        assertThat(targetDir.resolve("workflows/feature-${feature.id}.md").readText()).contains(feature.description)
    }

    @Test
    fun `should output a skill`() {
        // given
        val skillContext = SkillContext(textOnlySkill)

        // when
        claudeAdapter.export(tempDir.toFile(), skillContext)

        // then
        val skillFile = targetDir.resolve("skills/${textOnlySkill.id}/SKILL.md")
        assertThat(skillFile).exists()
        val content = skillFile.readText()
        assertThat(content).startsWith("---")
        assertThat(content).contains("name: ${textOnlySkill.id}")
        assertThat(content).contains(textOnlySkill.description)
    }

    @Test
    fun `prepare should delete claude directory when replace is true`() {
        // given
        val projectDir = tempDir.toFile()
        val claudeDir = projectDir.resolve(".claude")
        claudeDir.mkdirs()
        File(claudeDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns true

        // when
        claudeAdapter.prepare(projectDir, manifest)

        // then
        assertThat(claudeDir).doesNotExist()
    }

    @Test
    fun `prepare should keep claude directory when replace is false`() {
        // given
        val projectDir = tempDir.toFile()
        val claudeDir = projectDir.resolve(".claude")
        claudeDir.mkdirs()
        File(claudeDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns false

        // when
        claudeAdapter.prepare(projectDir, manifest)

        // then
        assertThat(claudeDir).exists()
        assertThat(File(claudeDir, "some-file.txt")).exists()
    }

    /**
     * The user scope writes into the per-user configuration of Claude Code. Every test here points the home base at
     * a temporary directory: the real home of whoever runs the suite is never read and never written.
     */
    @Nested
    inner class UserScope {

        private lateinit var userHome: File
        private lateinit var claudeDir: File

        @BeforeEach
        fun setUp() {
            userHome = tempDir.resolve("home").toFile()
            claudeDir = userHome.resolve(".claude")
        }

        @Test
        fun `should write the instructions file from the rulesets of the deployment`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(UserInstructionsContext(userDeployment, rulesets))

            // then
            val content = claudeDir.resolve("CLAUDE.md").readText()
            // - the header names the manifest the file was generated from, and there is no project to describe
            assertThat(content).contains("# ${userDeployment.id}")
            assertThat(content).contains(userDeployment.description)
            assertThat(content).contains("Rule number one.")
        }

        @Test
        fun `should overwrite the instructions file it already owns`() {
            // given
            // - the engine owns this file, so what a previous deploy or a hand edit left behind does not survive
            claudeDir.mkdirs()
            claudeDir.resolve("CLAUDE.md").writeText("Hand-written content.\n")
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(UserInstructionsContext(userDeployment, rulesets))

            // then
            val content = claudeDir.resolve("CLAUDE.md").readText()
            assertThat(content).doesNotContain("Hand-written content.")
            assertThat(content).contains("# ${userDeployment.id}")
        }

        @Test
        fun `should write an agent into the user agents directory`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(AgentContext(agent, rulesets))

            // then
            val content = claudeDir.resolve("agents/${agent.id}.md").readText().trim()
            assertThat(content).startsWith("---")
            assertThat(content).contains("name: ${agent.id}")
            assertThat(content).contains(expectedAgent.trim())
        }

        @Test
        fun `should write a prompt into the user commands directory`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(PromptContext(prompt, rulesets))

            // then
            assertThat(claudeDir.resolve("commands/${prompt.id}.md").readText().trim()).isEqualTo(expectedPrompt.trim())
        }

        @Test
        fun `should write a skill into the user skills directory`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(SkillContext(textOnlySkill))

            // then
            val skillFile = claudeDir.resolve("skills/${textOnlySkill.id}/SKILL.md")
            assertThat(skillFile).exists()
            assertThat(skillFile.readText()).contains("name: ${textOnlySkill.id}")
        }

        @Test
        fun `should keep a file left in a skill directory when replace is false`() {
            // given
            // - the documented limitation: without replace, what an earlier deploy wrote is only overwritten, never removed
            val staleFile = claudeDir.resolve("skills/${textOnlySkill.id}/stale.md")
            staleFile.parentFile.mkdirs()
            staleFile.writeText("Stale content.\n")
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(SkillContext(textOnlySkill))

            // then
            assertThat(staleFile).exists()
        }

        @Test
        fun `should rewrite the directory of a skill when replace is true`() {
            // given
            val staleFile = claudeDir.resolve("skills/${textOnlySkill.id}/stale.md")
            staleFile.parentFile.mkdirs()
            staleFile.writeText("Stale content.\n")
            val exporter = exporterFor(userDeployment.copy(replace = true))

            // when
            exporter.export(SkillContext(textOnlySkill))

            // then
            assertThat(staleFile).doesNotExist()
            assertThat(claudeDir.resolve("skills/${textOnlySkill.id}/SKILL.md")).exists()
        }

        @Test
        fun `should keep everything it did not deploy when replace is true`() {
            // given
            // - the engine owns the paths of its own artifacts, never the directories of the tool that hold them
            val neighbourSkill = claudeDir.resolve("skills/hand-made-skill/SKILL.md")
            neighbourSkill.parentFile.mkdirs()
            neighbourSkill.writeText("A skill installed by hand.\n")
            val neighbourAgent = claudeDir.resolve("agents/hand-made-agent.md")
            neighbourAgent.parentFile.mkdirs()
            neighbourAgent.writeText("An agent installed by hand.\n")
            val neighbourCommand = claudeDir.resolve("commands/hand-made-command.md")
            neighbourCommand.parentFile.mkdirs()
            neighbourCommand.writeText("A command installed by hand.\n")
            val unrelatedDirectory = claudeDir.resolve("projects/some-project")
            unrelatedDirectory.mkdirs()
            val replacingDeployment = userDeployment.copy(replace = true)
            val exporter = exporterFor(replacingDeployment)

            // when
            exporter.export(UserInstructionsContext(replacingDeployment, rulesets))
            exporter.export(AgentContext(agent, rulesets))
            exporter.export(PromptContext(prompt, rulesets))
            exporter.export(SkillContext(textOnlySkill))

            // then
            assertThat(neighbourSkill).hasContent("A skill installed by hand.\n")
            assertThat(neighbourAgent).hasContent("An agent installed by hand.\n")
            assertThat(neighbourCommand).hasContent("A command installed by hand.\n")
            assertThat(unrelatedDirectory).exists()
        }

        @Test
        fun `should delete nothing when the id of a skill climbs out of the skills directory`() {
            // given
            // - nothing validates a manifest id today, so a replacing deploy must not follow one out of its own
            //   directory: the delete is the single irreversible thing this engine does
            val neighbour = userHome.resolve("unrelated/notes.md")
            neighbour.parentFile.mkdirs()
            neighbour.writeText("Not written by any deploy.\n")
            val escapingSkill = textOnlySkill.copy(id = "../../unrelated")
            val exporter = exporterFor(userDeployment.copy(replace = true))

            // when
            val error = runCatching { exporter.export(SkillContext(escapingSkill)) }.exceptionOrNull()

            // then
            assertThat(neighbour).exists()
            assertThat(error)
                .isInstanceOf(ArtifactPathException::class.java)
                .hasMessageContaining("../../unrelated")
        }

        @Test
        fun `should keep the target of a symlink inside a skill directory when replace is true`() {
            // given
            // - a corpus linked into a skill bundle lives outside the home, and a deploy has no business deleting it
            val outsideFile = tempDir.resolve("outside/thesis.txt").toFile()
            outsideFile.parentFile.mkdirs()
            outsideFile.writeText("Years of work.\n")
            val skillDir = claudeDir.resolve("skills/${textOnlySkill.id}")
            skillDir.mkdirs()
            Files.createSymbolicLink(skillDir.resolve("corpus").toPath(), outsideFile.parentFile.toPath())
            val exporter = exporterFor(userDeployment.copy(replace = true))

            // when
            exporter.export(SkillContext(textOnlySkill))

            // then
            assertThat(outsideFile).hasContent("Years of work.\n")
            // - the link itself was removed with the directory that held it, and the skill was written again
            assertThat(skillDir.resolve("corpus")).doesNotExist()
            assertThat(skillDir.resolve("SKILL.md")).exists()
        }

        @Test
        fun `should write nothing outside the home it was given`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(UserInstructionsContext(userDeployment, rulesets))
            exporter.export(SkillContext(textOnlySkill))

            // then
            // - the project-scope destination of the very same adapter stays empty, and so does everything else
            assertThat(tempDir.toFile().listFiles()!!.map { it.name }).containsExactly("home")
        }

        private fun exporterFor(deployment: UserDeploymentManifest) =
            requireNotNull(claudeAdapter.userScope(userHome, deployment)) { "Claude has a user scope" }
    }
}
