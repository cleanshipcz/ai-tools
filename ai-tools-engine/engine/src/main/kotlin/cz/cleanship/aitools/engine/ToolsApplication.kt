package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.services.ConfigService
import cz.cleanship.aitools.engine.tools.ToolFactory

fun main() {
    val config = ConfigService().loadConfig()
    ToolsEngine(
        tools = config.tools.map { ToolFactory.create(it) },
    ).process(config.locations)
}
