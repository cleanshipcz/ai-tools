package cz.cleanship.aitools.engine.tools.adapters.antigravity

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.expectedFeature
import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.sourceBackedSkill
import cz.cleanship.aitools.engine.data.sourceBackedSkillBody
import cz.cleanship.aitools.engine.data.textOnlySkill
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.Frontmatter
import cz.cleanship.aitools.engine.tools.Printers
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.SkillContext
import cz.cleanship.aitools.engine.tools.prepare
import cz.cleanship.aitools.engine.utils.SOURCE_BACKED_TEMPLATE
import cz.cleanship.aitools.engine.utils.StringOutput
import cz.cleanship.aitools.engine.utils.contentSnapshot
import cz.cleanship.aitools.engine.utils.writeSourceBackedSkillFiles
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AntigravityAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    private val printers = Printers
    private lateinit var targetDir: File
    private lateinit var rulesDir: File
    private lateinit var workflowsDir: File
    private lateinit var output: StringOutput

    private lateinit var antigravityAdapter: AntigravityAdapter

    @BeforeEach
    fun setUp() {
        targetDir = tempDir.resolve(".agent").toFile()
        rulesDir = targetDir.resolve("rules")
        workflowsDir = targetDir.resolve("workflows")
        output = StringOutput()

        antigravityAdapter = AntigravityAdapter(printers)
    }

    @Test
    fun `should output a prompt`() {
        // given
        val prompt = PromptContext(prompt, rulesets)

        // when
        antigravityAdapter.export(tempDir.toFile(), prompt)

        // then
        assertThat(rulesDir.resolve("prompt-${prompt.prompt.id}.md").readText()).isEqualTo(withManualHeader(expectedPrompt))
    }

    @Test
    fun `should output an agent`() {
        // given
        val agent = agent

        // when
        antigravityAdapter.export(tempDir.toFile(), AgentContext(agent, rulesets))

        // then
        assertThat(rulesDir.resolve("agent-${agent.id}.md").readText()).isEqualTo(withManualHeader(expectedAgent))
    }

    @Test
    fun `should output a feature`() {
        // given
        val featureContext = FeatureContext(feature)

        // when
        antigravityAdapter.export(tempDir.toFile(), featureContext)

        // then
        assertThat(workflowsDir.resolve("feature-${feature.id}.md").readText()).isEqualTo(
            """
            |---
            |description: ${Frontmatter.value(feature.description)}
            |---
            |
            |$expectedFeature
            |
            """.trimMargin(),
        )
    }

    @Test
    fun `should output a source-backed skill with the source body verbatim and no heading of its own`() {
        // given
        // - the source folder holding the companion file the loader listed for the skill
        val sourceDir = writeSourceBackedSkillFiles(tempDir.toFile())
        val sourceBefore = sourceDir.contentSnapshot()
        val skillContext = SkillContext(sourceBackedSkill, sourceDir = sourceDir, pointerSourceDirs = emptyList())

        // when
        antigravityAdapter.export(tempDir.toFile(), skillContext)

        // then
        val content = rulesDir.resolve("skill-${sourceBackedSkill.id}.md").readText()
        assertThat(content).endsWith(sourceBackedSkillBody)
        assertThat(content).doesNotContain("# ${sourceBackedSkill.id}")
        assertThat(content).doesNotContain(sourceBackedSkill.description)
        assertThat(rulesDir.resolve("skill-${sourceBackedSkill.id}").resolve("templates/task.txt")).hasContent(SOURCE_BACKED_TEMPLATE)
        assertThat(sourceDir.contentSnapshot()).isEqualTo(sourceBefore)
    }

    @Test
    fun `should name every path the export of a skill writes among the skill paths of that skill`() {
        // given
        val sourceDir = writeSourceBackedSkillFiles(tempDir.toFile())
        val projectDir = tempDir.resolve("project").toFile()

        // when
        antigravityAdapter.export(projectDir, SkillContext(sourceBackedSkill, sourceDir = sourceDir, pointerSourceDirs = emptyList()))

        // then
        val skillPaths = antigravityAdapter.skillPaths(projectDir, sourceBackedSkill.id)
        val written = projectDir.walkTopDown().filter { it.isFile }.toList()
        assertThat(written).isNotEmpty.allSatisfy { file -> assertThat(skillPaths).anyMatch { file.startsWith(it) } }
    }

    @Test
    fun `should output a skill`() {
        // given
        val skillContext = SkillContext(textOnlySkill, pointerSourceDirs = emptyList())

        // when
        antigravityAdapter.export(tempDir.toFile(), skillContext)

        // then
        val skillFile = rulesDir.resolve("skill-${textOnlySkill.id}.md")
        assertThat(skillFile).exists()
        val content = skillFile.readText()
        assertThat(content).startsWith("---\ntrigger: manual\n---")
        assertThat(content).contains(textOnlySkill.description)
    }

    @Test
    fun `prepare should delete agent directory when replace is true`() {
        // given
        val projectDir = tempDir.toFile()
        val agentDir = projectDir.resolve(".agent")
        agentDir.mkdirs()
        File(agentDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns true

        // when
        antigravityAdapter.prepare(projectDir, manifest)

        // then
        assertThat(agentDir).doesNotExist()
    }

    @Test
    fun `prepare should keep agent directory when replace is false`() {
        // given
        val projectDir = tempDir.toFile()
        val agentDir = projectDir.resolve(".agent")
        agentDir.mkdirs()
        File(agentDir, "some-file.txt").writeText("content")

        val manifest = mockk<ProjectManifest>()
        val deploy = mockk<ProjectDeploy>()
        every { manifest.deploy } returns deploy
        every { deploy.replace } returns false

        // when
        antigravityAdapter.prepare(projectDir, manifest)

        // then
        assertThat(agentDir).exists()
        assertThat(File(agentDir, "some-file.txt")).exists()
    }

    private fun withManualHeader(content: String) = """
        |---
        |trigger: manual
        |---
        |
        |$content
        |
        """.trimMargin()
}
