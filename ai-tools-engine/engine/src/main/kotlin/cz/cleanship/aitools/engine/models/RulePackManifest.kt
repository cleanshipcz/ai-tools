package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class RulePackManifest(
    override val id: String,
    override val version: Version = Version("1.0.0"),
    val description: String,
    val rules: List<String> = emptyList(),
    val extends: List<String> = emptyList(), // TODO maybe remove?
    val tags: List<String> = emptyList(),
    override val metadata: ManifestMetadata = ManifestMetadata(),
) : VersionedManifest
