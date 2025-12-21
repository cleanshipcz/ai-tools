package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class RulepackManifest(
    override val id: String,
    override val description: String,
    val rules: List<String> = emptyList(),
    override val metadata: ManifestMetadata,
) : VersionedManifest
