package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureManifest(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val context: InnerFeatureContext? = null,
    val prompt: String,
    @SerialName("acceptance_criteria")
    val acceptanceCriteria: List<String>? = null,
    val constraints: List<String>? = null,
) : VersionedManifest

@Serializable
data class InnerFeatureContext(
    val overview: String? = null,
    val architecture: String? = null,
    val dependencies: List<String>? = null,
    val files: List<String>? = null,
)
