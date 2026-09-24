package cz.cleanship.aitools.engine.services

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The part of the MCP Registry server.json schema 2025-12-11 that McpServerReader renders; every other field is ignored.

@Serializable
internal data class ServerJson(
    @SerialName("\$schema") val schema: String? = null,
    val description: String = "",
    val packages: List<PackageJson> = emptyList(),
    val remotes: List<RemoteJson> = emptyList(),
)

@Serializable
internal data class PackageJson(
    val registryType: String,
    val identifier: String,
    val version: String? = null,
    val runtimeHint: String? = null,
    val transport: PackageTransportJson,
    val runtimeArguments: List<ArgumentJson> = emptyList(),
    val packageArguments: List<ArgumentJson> = emptyList(),
    val environmentVariables: List<KeyValueInputJson> = emptyList(),
)

@Serializable
internal data class PackageTransportJson(
    val type: String,
)

@Serializable
internal data class RemoteJson(
    val type: String,
    val url: String,
    val headers: List<KeyValueInputJson> = emptyList(),
    val variables: Map<String, InputJson> = emptyMap(),
)

@Serializable
internal data class InputJson(
    val description: String? = null,
    val isRequired: Boolean = false,
    val isSecret: Boolean = false,
)

@Serializable
internal data class KeyValueInputJson(
    val name: String,
    val description: String? = null,
    val isRequired: Boolean = false,
    val isSecret: Boolean = false,
    val value: String? = null,
    val variables: Map<String, InputJson> = emptyMap(),
)

@Serializable
internal data class ArgumentJson(
    val type: String,
    val name: String? = null,
    val value: String? = null,
    val valueHint: String? = null,
    val description: String? = null,
    val isRequired: Boolean = false,
    val isSecret: Boolean = false,
    val variables: Map<String, InputJson> = emptyMap(),
)
