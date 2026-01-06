package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class EngineConfig(
    val locations: LocationsConfig? = null,
)

@Serializable
data class LocationsConfig(
    val agents: List<String>? = null,
    val projects: List<String>? = null,
    val prompts: List<String>? = null,
    val rulesets: List<String>? = null,
)
