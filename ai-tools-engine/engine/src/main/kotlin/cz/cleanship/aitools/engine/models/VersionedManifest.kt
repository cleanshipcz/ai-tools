package cz.cleanship.aitools.engine.models

interface VersionedManifest {
    val version: Version
    val id: String
    val description: String
    val metadata: ManifestMetadata
}
