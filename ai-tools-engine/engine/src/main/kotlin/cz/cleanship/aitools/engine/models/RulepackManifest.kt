package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class RulepackManifest(
    override val id: String,
    override val version: Version,
    override val description: String,
    val rules: List<String> = emptyList(),
    val extends: List<String> = emptyList(), // TODO maybe remove?
    override val metadata: ManifestMetadata = ManifestMetadata(),
) : VersionedManifest
