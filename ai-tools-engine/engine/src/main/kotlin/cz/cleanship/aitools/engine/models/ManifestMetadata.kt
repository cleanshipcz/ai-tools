package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class ManifestMetadata(
    val version: Version,
    val author: String? = null,
    val created: String? = null,
    val updated: String? = null,
    val tags: Set<String> = emptySet(),
)
