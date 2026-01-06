package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.services.ConfigService
import cz.cleanship.aitools.engine.services.Locations
import cz.cleanship.aitools.engine.tools.adapters.antigravity.AntigravityAdapter
import java.io.File

fun main() {
    ToolsEngine(
        tools = listOf(
//            WindsurfAdapter(),
            AntigravityAdapter(),
        )
    ).process(
        ConfigService().loadConfig()
    )
}
