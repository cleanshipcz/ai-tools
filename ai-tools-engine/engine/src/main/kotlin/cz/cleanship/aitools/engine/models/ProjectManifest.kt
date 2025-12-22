package cz.cleanship.aitools.engine.models

import java.io.File

data class ProjectManifest(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val directory: File,
) : VersionedManifest
