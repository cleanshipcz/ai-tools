package cz.cleanship.aitools.cli

import cz.cleanship.aitools.engine.ToolsEngine
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
        engineFactory.create(toolAdapters).process(config.locations)
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
    fun create(tools: List<ToolAdapter>): ToolsEngineProcessor
}

class DefaultToolsEngineFactory : ToolsEngineFactory {
    override fun create(tools: List<ToolAdapter>): ToolsEngineProcessor {
        val engine = ToolsEngine(tools = tools)
        return ToolsEngineProcessor { locations -> engine.process(locations) }
    }
}
