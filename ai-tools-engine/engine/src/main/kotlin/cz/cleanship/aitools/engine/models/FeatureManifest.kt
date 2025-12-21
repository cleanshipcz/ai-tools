package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class FeatureManifest(
    override val id: String,
    override val description: String,
    val context: InnerFeatureContext,
    val prompt: String,
    val acceptanceCriteria: List<String>,
    val constraints: List<String>,
    override val metadata: ManifestMetadata,
) : VersionedManifest

@Serializable
data class InnerFeatureContext(
    val overview: String,
    val architecture: String,
    val dependencies: List<String>,
    val files: List<String>,
)
