package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param envVars the variables a declared path of the run may reference as `${NAME}` - see
 * [cz.cleanship.aitools.engine.env.VariableResolver]. Unlike `locations` and `tools`, which `config.local.yml`
 * replaces as a whole, these merge per key, so a local config can redeclare the one base that differs on its machine
 * without repeating the variables it agrees with.
 */
@Serializable
data class ConfigManifest(
    val locations: LocationsConfig? = null,
    val tools: List<ToolType>? = null,
    @SerialName("env_vars")
    val envVars: Map<String, String>? = null,
)

/**
 * @param projects the retired name of [deployments]. It is still decoded, and only so that a config left on it is
 * rejected by [cz.cleanship.aitools.engine.services.ConfigService] instead of being dropped as an unknown key -
 * `config.local.yml` is gitignored, so no rename in the repository can reach the one on another machine, and a
 * silently dropped list means a run that deploys nothing and reports success.
 */
@Serializable
data class LocationsConfig(
    val agents: List<String>? = null,
    val deployments: List<String>? = null,
    val projects: List<String>? = null,
    val prompts: List<String>? = null,
    val rulesets: List<String>? = null,
    val fragments: List<String>? = null,
    val skills: List<String>? = null,
)
