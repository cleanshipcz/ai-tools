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

@Serializable
data class LocationsConfig(
    val agents: List<String>? = null,
    val deployments: List<String>? = null,
    val prompts: List<String>? = null,
    val rulesets: List<String>? = null,
    val fragments: List<String>? = null,
    val skills: List<String>? = null,
)
