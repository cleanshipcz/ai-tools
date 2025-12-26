package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureManifest(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val context: InnerFeatureContext,
    val prompt: String,
    @SerialName("acceptance_criteria")
    val acceptanceCriteria: List<String>,
    val constraints: List<String>,
) : VersionedManifest

@Serializable
data class InnerFeatureContext(
    val overview: String,
    val architecture: String,
    val dependencies: List<String>,
    val files: List<String>,
)
