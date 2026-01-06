package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.ProjectFilter
import cz.cleanship.aitools.engine.models.VersionedManifest

class FilterService {

    fun <T : VersionedManifest> filter(manifests: Collection<T>, filter: ProjectFilter?) = manifests.filter(createFilter(filter))

    fun <T : VersionedManifest> createFilter(filter: ProjectFilter?) = when (filter) {
        is ProjectFilter.ByTags -> { manifest: T -> filter.tags.any { tag -> manifest.metadata.tags.contains(tag) } }
        is ProjectFilter.ByWhitelistedIds -> { manifest: T -> manifest.id in filter.ids }
        is ProjectFilter.ByBlacklistedIds -> { manifest: T -> manifest.id !in filter.ids }
        null -> { _: T -> true }
    }
}
