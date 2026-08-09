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
    fun run(workingDirectory: File)
}

class DefaultToolsApplicationRunner(
    private val configService: ConfigService = ConfigService(),
    private val toolAdapterFactory: ToolAdapterFactory = DefaultToolAdapterFactory,
    private val engineFactory: ToolsEngineFactory = DefaultToolsEngineFactory(),
) : ToolsApplicationRunner {
    override fun run(workingDirectory: File) {
        val config = configService.loadConfig(workingDirectory)
        val toolAdapters = config.tools.map(toolAdapterFactory::create)
        engineFactory.create(toolAdapters, workingDirectory, config.variables).process(config.locations)
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
     */
    fun create(tools: List<ToolAdapter>, workingDirectory: File, variables: VariableResolver): ToolsEngineProcessor
}

class DefaultToolsEngineFactory : ToolsEngineFactory {
    override fun create(
        tools: List<ToolAdapter>,
        workingDirectory: File,
        variables: VariableResolver,
    ): ToolsEngineProcessor {
        val engine = ToolsEngine(workingDirectory, variables = variables, tools = tools)
        return ToolsEngineProcessor { locations -> engine.process(locations) }
    }
}
