package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.ProjectFilter
import cz.cleanship.aitools.engine.models.VersionedManifest

class FilterService {

    fun <T : VersionedManifest> filter(manifests: Collection<T>, filters: List<ProjectFilter>): List<T> {
        if (filters.isEmpty()) return manifests.toList()
        val result = filters.fold(emptySet<T>()) { acc, filter ->
            when (filter) {
                is ProjectFilter.ByTags -> acc + manifests.filter { manifest ->
                    filter.tags.any { tag -> tag in manifest.metadata.tags }
                }
                is ProjectFilter.ByWhitelistedIds -> acc + manifests.filter { it.id in filter.ids }
                is ProjectFilter.ByBlacklistedIds -> acc - manifests.filter { it.id in filter.ids }.toSet()
            }
        }
        return result.toList()
    }
}
