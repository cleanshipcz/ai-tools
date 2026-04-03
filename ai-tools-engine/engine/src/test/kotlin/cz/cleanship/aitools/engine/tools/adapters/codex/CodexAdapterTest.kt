package cz.cleanship.aitools.engine.tools.adapters.codex

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.commandSkill
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectContext
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectDocumentation
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
        assertThat(content).contains("description: ${prompt.prompt.description.replace("\n", " ")}")
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
        assertThat(content).contains("description: ${agent.description.replace("\n", " ")}")
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
    fun `should output a skill`() {
        // given
        val skillContext = SkillContext(commandSkill)

        // when
        adapter.export(tempDir.toFile(), skillContext)

        // then
        val skillFile = targetDir.resolve("skills/skill-${commandSkill.id}/SKILL.md")
        assertThat(skillFile).exists()
        val content = skillFile.readText()
        assertThat(content).startsWith("---")
        assertThat(content).contains("name: ${commandSkill.id}")
        assertThat(content).contains(commandSkill.description)
    }

    @Test
    fun `prepare should delete codex directory when replace is true`() {
        // given
        val projectDir = tempDir.toFile()
        val codexDir = projectDir.resolve(".codex")
        codexDir.mkdirs()
        File(codexDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns true

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(codexDir).doesNotExist()
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
}
