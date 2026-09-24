package cz.cleanship.aitools.engine.tools.adapters.codex

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.sourceBackedSkill
import cz.cleanship.aitools.engine.data.sourceBackedSkillBody
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
import cz.cleanship.aitools.engine.tools.Frontmatter
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import cz.cleanship.aitools.engine.tools.UserInstructionsContext
import cz.cleanship.aitools.engine.tools.prepare
import cz.cleanship.aitools.engine.utils.SOURCE_BACKED_TEMPLATE
import cz.cleanship.aitools.engine.utils.contentSnapshot
import cz.cleanship.aitools.engine.utils.writeSourceBackedSkillFiles
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class CodexAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val printers = Printers
    private lateinit var targetDir: File
    private lateinit var adapter: CodexAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".codex").toFile()
        adapter = CodexAdapter(printers)
    }

    @Test
    fun `should output project context to AGENTS dot md`() {
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
        adapter.export(tempDir.toFile(), GlobalContext(project))

        // then
        val content = tempDir.resolve("AGENTS.md").toFile().readText()
        assertThat(content).contains("# test-project")
        assertThat(content).contains("Test Project")
        assertThat(content).contains("## Rules")
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        adapter.export(tempDir.toFile(), prompt)

        // then
        val content = targetDir.resolve("skills/prompt-${prompt.prompt.id}/SKILL.md").readText().trim()
        assertThat(content).startsWith("---")
        assertThat(content).contains("name: ${prompt.prompt.id}")
        assertThat(content).contains("description: ${Frontmatter.value(prompt.prompt.description)}")
        assertThat(content).contains(expectedPrompt.trim())
    }

    @Test
    fun `should output an agent skill`() {
        // given
        val agent = agent

        // when
        adapter.export(tempDir.toFile(), AgentContext(agent, rulesets))

        // then
        val content = targetDir.resolve("skills/agent-${agent.id}/SKILL.md").readText().trim()
        assertThat(content).startsWith("---")
        assertThat(content).contains("name: ${agent.id}")
        assertThat(content).contains("description: ${Frontmatter.value(agent.description)}")
        assertThat(content).contains(expectedAgent.trim())
    }

    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        adapter.export(tempDir.toFile(), featureContext)

        // then
        assertThat(targetDir.resolve("features/feature-${feature.id}.md").readText()).contains(feature.description)
    }

    @Test
    fun `should output a source-backed skill as the frontmatter followed by the source body verbatim`() {
        // given
        // - the source folder holding the companion file the loader listed for the skill
        val sourceDir = writeSourceBackedSkillFiles(tempDir.toFile())
        val sourceBefore = sourceDir.contentSnapshot()
        val skillContext = SkillContext(sourceBackedSkill, sourceDir = sourceDir, pointerSourceDirs = emptyList())

        // when
        adapter.export(tempDir.toFile(), skillContext)

        // then
        val content = targetDir.resolve("skills/skill-${sourceBackedSkill.id}/SKILL.md").readText()
        assertThat(content).isEqualTo("---\nname: ${sourceBackedSkill.id}\ndescription: ${Frontmatter.value(sourceBackedSkill.description)}\n---\n\n" + sourceBackedSkillBody)
        assertThat(targetDir.resolve("skills/skill-${sourceBackedSkill.id}").resolve("templates/task.txt")).hasContent(SOURCE_BACKED_TEMPLATE)
        assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
    }

    @Test
    fun `should name every path the export of a skill writes among the skill paths of that skill`() {
        // given
        val sourceDir = writeSourceBackedSkillFiles(tempDir.toFile())
        val projectDir = tempDir.resolve("project").toFile()

        // when
        adapter.export(projectDir, SkillContext(sourceBackedSkill, sourceDir = sourceDir, pointerSourceDirs = emptyList()))

        // then
        val skillPaths = adapter.skillPaths(projectDir, sourceBackedSkill.id)
        val written = projectDir.walkTopDown().filter { it.isFile }.toList()
        assertThat(written).isNotEmpty.allSatisfy { file -> assertThat(skillPaths).anyMatch { file.startsWith(it) } }
    }

    @Test
    fun `should output a skill`() {
        // given
        val skillContext = SkillContext(textOnlySkill, pointerSourceDirs = emptyList())

        // when
        adapter.export(tempDir.toFile(), skillContext)

        // then
        val skillFile = targetDir.resolve("skills/skill-${textOnlySkill.id}/SKILL.md")
        assertThat(skillFile).exists()
        val content = skillFile.readText()
        assertThat(content).startsWith("---")
        assertThat(content).contains("name: ${textOnlySkill.id}")
        assertThat(content).contains(textOnlySkill.description)
    }

    @Test
    fun `prepare should delete the generated codex directories and keep the MCP config file when replace is true`() {
        // given
        val projectDir = tempDir.toFile()
        val codexDir = projectDir.resolve(".codex")
        // - every directory this adapter generates holds a file of an earlier export
        val generated = listOf("skills", "features").map { codexDir.resolve(it) }
        generated.forEach {
            it.mkdirs()
            File(it, "some-file.txt").writeText("content")
        }
        // - the MCP config file, whose entries the engine owns one by one, sits beside them
        val mcpConfig = codexDir.resolve("config.toml")
        mcpConfig.writeText("kept")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns true

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(generated).allSatisfy { assertThat(it).doesNotExist() }
        assertThat(mcpConfig).hasContent("kept")
    }

    @Test
    fun `prepare should keep codex directory when replace is false`() {
        // given
        val projectDir = tempDir.toFile()
        val codexDir = projectDir.resolve(".codex")
        codexDir.mkdirs()
        File(codexDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns false

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(codexDir).exists()
        assertThat(File(codexDir, "some-file.txt")).exists()
    }

    /**
     * The user scope writes into the per-user configuration of Codex. Every test here points the home base at a temporary directory: the real home of whoever runs the suite is never read and never written.
     */
    @Nested
    inner class UserScope {

        private lateinit var userHome: File
        private lateinit var codexDir: File

        @BeforeEach
        fun setUp() {
            userHome = tempDir.resolve("home").toFile()
            codexDir = userHome.resolve(".codex")
        }

        @Test
        fun `should write the instructions file from the rulesets of the deployment`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(UserInstructionsContext(userDeployment, rulesets))

            // then
            val content = codexDir.resolve("AGENTS.md").readText()
            assertThat(content).contains("# ${userDeployment.id}")
            assertThat(content).contains(userDeployment.description)
            assertThat(content).contains("Rule number one.")
        }

        @Test
        fun `should overwrite the instructions file it already owns`() {
            // given
            codexDir.mkdirs()
            codexDir.resolve("AGENTS.md").writeText("Hand-written content.\n")
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(UserInstructionsContext(userDeployment, rulesets))

            // then
            assertThat(codexDir.resolve("AGENTS.md").readText()).doesNotContain("Hand-written content.")
        }

        @Test
        fun `should write an agent as a skill of the user skills directory`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(AgentContext(agent, rulesets))

            // then
            val content = codexDir.resolve("skills/agent-${agent.id}/SKILL.md").readText().trim()
            assertThat(content).startsWith("---")
            assertThat(content).contains("name: ${agent.id}")
            assertThat(content).contains(expectedAgent.trim())
        }

        @Test
        fun `should write a prompt as a skill of the user skills directory`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(PromptContext(prompt, rulesets))

            // then
            val content = codexDir.resolve("skills/prompt-${prompt.id}/SKILL.md").readText().trim()
            assertThat(content).startsWith("---")
            assertThat(content).contains("name: ${prompt.id}")
            assertThat(content).contains(expectedPrompt.trim())
        }

        @Test
        fun `should write a skill into the user skills directory`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(SkillContext(textOnlySkill, pointerSourceDirs = emptyList()))

            // then
            val skillFile = codexDir.resolve("skills/skill-${textOnlySkill.id}/SKILL.md")
            assertThat(skillFile).exists()
            assertThat(skillFile.readText()).contains("name: ${textOnlySkill.id}")
        }

        @Test
        fun `should rewrite the directory of a skill when replace is true`() {
            // given
            val staleFile = codexDir.resolve("skills/skill-${textOnlySkill.id}/stale.md")
            staleFile.parentFile.mkdirs()
            staleFile.writeText("Stale content.\n")
            val exporter = exporterFor(userDeployment.copy(replace = true))

            // when
            exporter.export(SkillContext(textOnlySkill, pointerSourceDirs = emptyList()))

            // then
            assertThat(staleFile).doesNotExist()
            assertThat(codexDir.resolve("skills/skill-${textOnlySkill.id}/SKILL.md")).exists()
        }

        @Test
        fun `should keep everything it did not deploy when replace is true`() {
            // given
            // - the engine owns the paths of its own artifacts, never the directories of the tool that hold them
            val neighbourSkill = codexDir.resolve("skills/hand-made-skill/SKILL.md")
            neighbourSkill.parentFile.mkdirs()
            neighbourSkill.writeText("A skill installed by hand.\n")
            val replacingDeployment = userDeployment.copy(replace = true)
            val exporter = exporterFor(replacingDeployment)

            // when
            exporter.export(UserInstructionsContext(replacingDeployment, rulesets))
            exporter.export(AgentContext(agent, rulesets))
            exporter.export(PromptContext(prompt, rulesets))
            exporter.export(SkillContext(textOnlySkill, pointerSourceDirs = emptyList()))

            // then
            assertThat(neighbourSkill).hasContent("A skill installed by hand.\n")
        }

        @Test
        fun `should delete nothing when the id of a skill climbs out of the skills directory`() {
            // given
            // - nothing validates a manifest id today, so a replacing deploy must not follow one out of its own directory: the delete is the single irreversible thing this engine does
            val neighbour = userHome.resolve("unrelated/notes.md")
            neighbour.parentFile.mkdirs()
            neighbour.writeText("Not written by any deploy.\n")
            // - one segment more than Claude needs, because the 'skill-' prefix of this layout swallows the first one
            val escapingSkill = textOnlySkill.copy(id = "../../../unrelated")
            val exporter = exporterFor(userDeployment.copy(replace = true))

            // when
            val error = runCatching { exporter.export(SkillContext(escapingSkill, pointerSourceDirs = emptyList())) }.exceptionOrNull()

            // then
            assertThat(neighbour).exists()
            assertThat(error)
                .isInstanceOf(ArtifactPathException::class.java)
                .hasMessageContaining("unrelated")
        }

        @Test
        fun `should write nothing outside the home it was given`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(UserInstructionsContext(userDeployment, rulesets))
            exporter.export(SkillContext(textOnlySkill, pointerSourceDirs = emptyList()))

            // then
            assertThat(tempDir.toFile().listFiles()!!.map { it.name }).containsExactly("home")
        }

        @Test
        fun `should name every path the export of a skill writes among the skill paths of that skill`() {
            // given
            val sourceDir = writeSourceBackedSkillFiles(tempDir.toFile())
            val exporter = exporterFor(userDeployment)

            // when
            exporter.export(SkillContext(sourceBackedSkill, sourceDir = sourceDir, pointerSourceDirs = emptyList()))

            // then
            val skillPaths = exporter.skillPaths(sourceBackedSkill.id)
            val written = userHome.walkTopDown().filter { it.isFile }.toList()
            assertThat(written).isNotEmpty.allSatisfy { file -> assertThat(skillPaths).anyMatch { file.startsWith(it) } }
        }

        @Test
        fun `should name the directory of every prompt, agent and skill as the paths a replacing deploy deletes`() {
            // given
            val exporter = exporterFor(userDeployment.copy(replace = true))

            // when
            val replacedPaths = exporter.replacedPaths(promptIds = listOf(prompt.id), agentIds = listOf(agent.id), skillIds = listOf(textOnlySkill.id))

            // then
            assertThat(replacedPaths).containsExactly(
                codexDir.resolve("skills/prompt-${prompt.id}"),
                codexDir.resolve("skills/agent-${agent.id}"),
                codexDir.resolve("skills/skill-${textOnlySkill.id}"),
            )
        }

        @Test
        fun `should name no path when the deployment does not replace`() {
            // given
            val exporter = exporterFor(userDeployment)

            // when
            val replacedPaths = exporter.replacedPaths(promptIds = listOf(prompt.id), agentIds = listOf(agent.id), skillIds = listOf(textOnlySkill.id))

            // then
            assertThat(replacedPaths).isEmpty()
        }

        @Test
        fun `should delete nothing outside the replaced paths when a replacing deploy exports every kind of artifact`() {
            // given
            val replacingDeployment = userDeployment.copy(replace = true)
            val exporter = exporterFor(replacingDeployment)
            // - stale files inside every directory an export of these artifacts rewrites, and one beside them
            val staleFiles = listOf(
                "skills/prompt-${prompt.id}/stale.md",
                "skills/agent-${agent.id}/stale.md",
                "skills/skill-${textOnlySkill.id}/stale.md",
                "skills/hand-made/SKILL.md",
            ).map { relative -> writeStale(codexDir.resolve(relative)) }

            // when
            exporter.export(UserInstructionsContext(replacingDeployment, rulesets))
            exporter.export(AgentContext(agent, rulesets))
            exporter.export(PromptContext(prompt, rulesets))
            exporter.export(SkillContext(textOnlySkill, pointerSourceDirs = emptyList()))

            // then
            val replacedPaths = exporter.replacedPaths(promptIds = listOf(prompt.id), agentIds = listOf(agent.id), skillIds = listOf(textOnlySkill.id))
            assertThat(staleFiles.filterNot { it.exists() }).hasSize(3).allSatisfy { deleted -> assertThat(replacedPaths).anyMatch { deleted.startsWith(it) } }
        }

        private fun writeStale(file: File): File {
            file.parentFile.mkdirs()
            file.writeText("Stale.\n")
            return file
        }

        private fun exporterFor(deployment: UserDeploymentManifest) =
            requireNotNull(adapter.userScope(userHome, deployment)) { "Codex has a user scope" }
    }
}
