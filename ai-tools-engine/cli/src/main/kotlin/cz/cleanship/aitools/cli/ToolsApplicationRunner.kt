package cz.cleanship.aitools.cli

import cz.cleanship.aitools.engine.ToolsEngine
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.ConfigService
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.ToolFactory
import java.io.File

fun interface ToolsApplicationRunner {
    /**
     * @param userHome the home directory the user deployments of the run are written under - see
     * [ToolsEngine]
     */
    fun run(workingDirectory: File, userHome: File)
}

class DefaultToolsApplicationRunner(
    private val configService: ConfigService = ConfigService(),
    private val toolAdapterFactory: ToolAdapterFactory = DefaultToolAdapterFactory,
    private val engineFactory: ToolsEngineFactory = DefaultToolsEngineFactory(),
) : ToolsApplicationRunner {
    override fun run(workingDirectory: File, userHome: File) {
        val config = configService.loadConfig(workingDirectory)
        val toolAdapters = config.tools.map(toolAdapterFactory::create)
        engineFactory.create(toolAdapters, workingDirectory, config.variables, userHome).process(config.locations)
    }
}

fun interface ToolAdapterFactory {
    fun create(toolType: ToolType): ToolAdapter
}

object DefaultToolAdapterFactory : ToolAdapterFactory {
    override fun create(toolType: ToolType): ToolAdapter = ToolFactory.create(toolType)
}

fun interface ToolsEngineProcessor {
    fun process(locations: Locations)
}

fun interface ToolsEngineFactory {
    /**
     * @param workingDirectory the `--working-dir` of the run, which the engine resolves a relative
     * `deploy.directory` against
     * @param variables the variables the config files of the run declared, which the engine substitutes a
     * `deploy.directory` with before resolving it - the same ones the `locations.*` of that config were substituted
     * with, so that one name means one directory across the whole run
     * @param userHome the `--user-home` of the run, under which the adapters write the user scope of their tool
     */
    fun create(
        tools: List<ToolAdapter>,
        workingDirectory: File,
        variables: VariableResolver,
        userHome: File,
    ): ToolsEngineProcessor
}

class DefaultToolsEngineFactory : ToolsEngineFactory {
    override fun create(
        tools: List<ToolAdapter>,
        workingDirectory: File,
        variables: VariableResolver,
        userHome: File,
    ): ToolsEngineProcessor {
        val engine = ToolsEngine(workingDirectory, variables = variables, userHome = userHome, tools = tools)
        return ToolsEngineProcessor { locations -> engine.process(locations) }
    }
}
