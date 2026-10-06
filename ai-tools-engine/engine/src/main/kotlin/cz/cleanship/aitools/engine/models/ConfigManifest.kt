package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param envVars the variables a declared path of the run may reference as `${NAME}` - see
 * [cz.cleanship.aitools.engine.env.VariableResolver]. Unlike `locations` and `tools`, which `config.local.yml`
 * replaces as a whole, these merge per key, so a local config can redeclare the one base that differs on its machine
 * without repeating the variables it agrees with.
 * @param secretsManager the `configValue` of the [SecretsManagerKind] of the machine, written `secrets_manager` in YAML, or `null` when this file names none; a value this engine does not know fails the run, naming the file and the accepted values.
 */
@Serializable
data class ConfigManifest(
    val locations: LocationsConfig? = null,
    val tools: List<ToolType>? = null,
    @SerialName("env_vars")
    val envVars: Map<String, String>? = null,
    // Decoded as text rather than as SecretsManagerKind, so that a value this engine does not know fails naming the file and the accepted values instead of failing inside the decoder.
    @SerialName("secrets_manager")
    val secretsManager: String? = null,
)

/**
 * @param projects the retired name of [deployments]. It is still decoded, and only so that a config left on it is
 * rejected by [cz.cleanship.aitools.engine.services.ConfigService] instead of being dropped as an unknown key -
 * `config.local.yml` is gitignored, so no rename in the repository can reach the one on another machine, and a
 * silently dropped list means a run that deploys nothing and reports success.
 * @param mcps the directories holding the MCP server manifests of the run - see [McpServerManifest]. Omitting it loads no MCP server, so no deployment writes an MCP config file.
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
    val mcps: List<String>? = null,
)
