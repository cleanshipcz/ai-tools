package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class FragmentManifest(
    override val id: String,
    override val description: String,
    val content: String,
    override val metadata: ManifestMetadata,
) : VersionedManifest
