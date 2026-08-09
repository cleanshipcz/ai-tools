package cz.cleanship.aitools.engine.models

import cz.cleanship.aitools.engine.env.VariableResolver
import java.io.File

/**
 * @param locations the manifest locations of the run, already substituted and resolved into directories
 * @param tools the tools the run builds
 * @param variables the variables of the run, which every path declared outside the config is substituted with as
 * well - `deploy.directory` of a project manifest is expanded by [cz.cleanship.aitools.engine.ToolsEngine], not here,
 * because the manifest carrying it is only read once the locations are known.
 */
data class EngineConfig(
    val locations: Locations,
    val tools: List<ToolType>,
    val variables: VariableResolver = VariableResolver(),
)

data class Locations(
    val agents: List<File>,
    val projects: List<File>,
    val prompts: List<File>,
    val rulesets: List<File>,
    val fragments: List<File>,
    val skills: List<File>,
)
