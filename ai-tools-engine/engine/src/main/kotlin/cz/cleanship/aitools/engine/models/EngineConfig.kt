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

/**
 * @param deployments the directories holding the deployment manifests of the run, of both kinds: a `project.yml`
 * deploys into a project directory - see [ProjectManifest] - and a `user.yml` into the user scope of each tool -
 * see [UserDeploymentManifest]. One list feeds both, because a directory that holds one kind usually holds the other.
 */
data class Locations(
    val agents: List<File>,
    val deployments: List<File>,
    val prompts: List<File>,
    val rulesets: List<File>,
    val fragments: List<File>,
    val skills: List<File>,
)
