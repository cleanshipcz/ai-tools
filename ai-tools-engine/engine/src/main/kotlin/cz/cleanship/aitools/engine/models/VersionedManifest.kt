package cz.cleanship.aitools.engine.models

interface VersionedManifest {
    val id: String
    val description: String
    val metadata: ManifestMetadata
}
