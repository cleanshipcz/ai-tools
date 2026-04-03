package cz.cleanship.aitools.engine.models

import java.io.File

data class EngineConfig(
    val locations: Locations,
    val tools: List<ToolType>,
)

data class Locations(
    val agents: List<File>,
    val projects: List<File>,
    val prompts: List<File>,
    val rulesets: List<File>,
    val skills: List<File>,
)
