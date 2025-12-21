package cz.cleanship.aitools.engine.models

interface VersionedEntity {
    val version: Version
    val id: String
}