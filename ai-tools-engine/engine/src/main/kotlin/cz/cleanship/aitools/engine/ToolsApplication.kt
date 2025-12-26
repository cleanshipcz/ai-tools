package cz.cleanship.aitools.engine

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
        Locations(
            rulesets = listOf(File("01_rulesets")),
            prompts = listOf(File("03_prompts")),
            agents = listOf(File("04_agents")),
            projects = listOf(File("06_projects"), File("../ai-tools-projects/projects/")),
        ),
    )
}
