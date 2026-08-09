package cz.cleanship.aitools.cli

import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.EngineConfig
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.ConfigService
import cz.cleanship.aitools.engine.tools.ToolAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileNotFoundException

class DefaultToolsApplicationRunnerTest {

    private lateinit var configService: ConfigService
    private lateinit var toolAdapterFactory: ToolAdapterFactory
    private lateinit var engineFactory: ToolsEngineFactory
    private lateinit var engineProcessor: ToolsEngineProcessor
    private lateinit var runner: DefaultToolsApplicationRunner

    @BeforeEach
    fun setUp() {
        configService = mockk()
        toolAdapterFactory = mockk()
        engineFactory = mockk()
        engineProcessor = mockk()
        runner = DefaultToolsApplicationRunner(
            configService = configService,
            toolAdapterFactory = toolAdapterFactory,
            engineFactory = engineFactory,
        )
    }

    @Nested
    inner class HappyPath {

        @Test
        fun `should create tool adapters and process locations when config is loaded`() {
            // given
            val workingDirectory = File("workspace")
            val locations = Locations(
                agents = listOf(File("agents")),
                projects = listOf(File("projects")),
                prompts = listOf(File("prompts")),
                rulesets = listOf(File("rulesets")),
                fragments = listOf(File("fragments")),
                skills = listOf(File("skills")),
            )
            // - the variables the config declares must reach the engine, which substitutes 'deploy.directory' with them
            val variables = VariableResolver(mapOf("PROJECTS_FOLDER" to "/home/user/Projects"))
            val config = EngineConfig(
                locations = locations,
                tools = listOf(ToolType.CLAUDE, ToolType.CODEX),
                variables = variables,
            )
            val claudeAdapter = mockk<ToolAdapter>()
            val codexAdapter = mockk<ToolAdapter>()

            every { configService.loadConfig(workingDirectory) } returns config
            every { toolAdapterFactory.create(ToolType.CLAUDE) } returns claudeAdapter
            every { toolAdapterFactory.create(ToolType.CODEX) } returns codexAdapter
            every { engineFactory.create(listOf(claudeAdapter, codexAdapter), workingDirectory, variables) } returns engineProcessor
            every { engineProcessor.process(locations) } returns Unit

            // when
            runner.run(workingDirectory)

            // then
            verify(exactly = 1) { configService.loadConfig(workingDirectory) }
            verify(exactly = 1) { toolAdapterFactory.create(ToolType.CLAUDE) }
            verify(exactly = 1) { toolAdapterFactory.create(ToolType.CODEX) }
            verify(exactly = 1) { engineFactory.create(listOf(claudeAdapter, codexAdapter), workingDirectory, variables) }
            verify(exactly = 1) { engineProcessor.process(locations) }
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `should process locations when config contains no tools`() {
            // given
            val workingDirectory = File("workspace")
            val locations = Locations(
                agents = emptyList(),
                projects = emptyList(),
                prompts = emptyList(),
                rulesets = emptyList(),
                fragments = emptyList(),
                skills = emptyList(),
            )
            val variables = VariableResolver()
            val config = EngineConfig(
                locations = locations,
                tools = emptyList(),
                variables = variables,
            )
            val emptyAdapters = emptyList<ToolAdapter>()

            every { configService.loadConfig(workingDirectory) } returns config
            every { engineFactory.create(emptyAdapters, workingDirectory, variables) } returns engineProcessor
            every { engineProcessor.process(locations) } returns Unit

            // when
            runner.run(workingDirectory)

            // then
            verify(exactly = 1) { configService.loadConfig(workingDirectory) }
            verify(exactly = 1) { engineFactory.create(emptyAdapters, workingDirectory, variables) }
            verify(exactly = 1) { engineProcessor.process(locations) }
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `should propagate error when config is missing`() {
            // given
            val workingDirectory = File("workspace")

            every { configService.loadConfig(workingDirectory) } throws FileNotFoundException("Missing config")

            // when
            val error = runCatching { runner.run(workingDirectory) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(FileNotFoundException::class.java)
                .hasMessageContaining("Missing config")
        }
    }
}
