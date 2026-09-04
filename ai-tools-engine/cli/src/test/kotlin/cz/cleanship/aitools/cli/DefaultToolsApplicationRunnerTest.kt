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
            // - a home of the run that is never read here, only handed on: no test of this suite touches a real home
            val userHome = File("home")
            val locations = Locations(
                agents = listOf(File("agents")),
                deployments = listOf(File("deployments")),
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
            every { toolAdapterFactory.create(ToolType.CLAUDE, dryRun = false) } returns claudeAdapter
            every { toolAdapterFactory.create(ToolType.CODEX, dryRun = false) } returns codexAdapter
            every {
                engineFactory.create(listOf(claudeAdapter, codexAdapter), workingDirectory, variables, userHome, dryRun = false)
            } returns engineProcessor
            every { engineProcessor.process(locations) } returns Unit

            // when
            runner.run(workingDirectory, userHome, dryRun = false)

            // then
            verify(exactly = 1) { configService.loadConfig(workingDirectory) }
            verify(exactly = 1) { toolAdapterFactory.create(ToolType.CLAUDE, dryRun = false) }
            verify(exactly = 1) { toolAdapterFactory.create(ToolType.CODEX, dryRun = false) }
            // - the home of the run reaches the engine, which is what the adapters derive their user scope from
            verify(exactly = 1) {
                engineFactory.create(listOf(claudeAdapter, codexAdapter), workingDirectory, variables, userHome, dryRun = false)
            }
            verify(exactly = 1) { engineProcessor.process(locations) }
        }

        @Test
        fun `should create dry-run adapters and a dry-run engine when asked for a dry run`() {
            // given
            // - the flag has to reach both: the adapters decide what touches the disk, the engine what it announces
            val workingDirectory = File("workspace")
            val userHome = File("home")
            val locations = Locations(
                agents = emptyList(),
                deployments = listOf(File("deployments")),
                prompts = emptyList(),
                rulesets = emptyList(),
                fragments = emptyList(),
                skills = emptyList(),
            )
            val variables = VariableResolver()
            val config = EngineConfig(locations = locations, tools = listOf(ToolType.CLAUDE), variables = variables)
            val dryRunAdapter = mockk<ToolAdapter>()

            every { configService.loadConfig(workingDirectory) } returns config
            every { toolAdapterFactory.create(ToolType.CLAUDE, dryRun = true) } returns dryRunAdapter
            every { engineFactory.create(listOf(dryRunAdapter), workingDirectory, variables, userHome, dryRun = true) } returns engineProcessor
            every { engineProcessor.process(locations) } returns Unit

            // when
            runner.run(workingDirectory, userHome, dryRun = true)

            // then
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
                deployments = emptyList(),
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
            val userHome = File("home")

            every { configService.loadConfig(workingDirectory) } returns config
            every { engineFactory.create(emptyAdapters, workingDirectory, variables, userHome, dryRun = false) } returns engineProcessor
            every { engineProcessor.process(locations) } returns Unit

            // when
            runner.run(workingDirectory, userHome, dryRun = false)

            // then
            verify(exactly = 1) { configService.loadConfig(workingDirectory) }
            verify(exactly = 1) { engineFactory.create(emptyAdapters, workingDirectory, variables, userHome, dryRun = false) }
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
            val error = runCatching { runner.run(workingDirectory, File("home"), dryRun = false) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(FileNotFoundException::class.java)
                .hasMessageContaining("Missing config")
        }
    }
}
