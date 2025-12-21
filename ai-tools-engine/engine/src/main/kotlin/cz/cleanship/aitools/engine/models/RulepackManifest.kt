package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class RulepackManifest(
    val id: String,
    val version: String = "1.0.0",
    val description: String,
    val rules: List<String> = emptyList(),
    val extends: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val metadata: ManifestMetadata = ManifestMetadata()
)
