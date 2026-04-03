package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.FragmentManifest

/**
 * Resolves fragment patterns (supporting regexps like `confluence-.*`) against available fragments.
 */
class FragmentResolver {

    /**
     * Resolves fragment patterns against available fragments.
     *
     * @param patterns List of fragment IDs or patterns (e.g., `["confluence-guide", "mcp-.*"]`)
     * @param available Map of available fragments (project-filtered)
     * @param requestedBy Human-readable description of the entity requesting resolution (e.g., "agent 'developer-feature'")
     * @param allFragments Map of all fragments before project filtering, used to detect filtered-out matches
     * @return List of matched fragments, deduplicated by ID
     * @throws FragmentResolvingException if a pattern matches no fragments
     */
    fun resolve(
        patterns: List<String>,
        available: Map<String, FragmentManifest>,
        requestedBy: String = "",
        allFragments: Map<String, FragmentManifest> = available,
    ): List<FragmentManifest> = patterns
        .flatMap { pattern ->
            val regex = pattern.toRegex()
            val matched = available.values.filter { it.id.matches(regex) }
            if (matched.isEmpty()) {
                val filteredOut = allFragments.values
                    .filter { it.id.matches(regex) }
                    .map { it.id }
                val suggestions = findSuggestions(pattern, available.keys)
                throw FragmentResolvingException(
                    pattern = pattern,
                    requestedBy = requestedBy,
                    availableIds = available.keys.sorted(),
                    filteredOutIds = filteredOut,
                    suggestions = suggestions,
                )
            }
            matched
        }.distinctBy { it.id }

    internal fun findSuggestions(pattern: String, availableIds: Set<String>): List<String> {
        val normalizedPattern = pattern
            .replace(Regex("[.*+?^\\\\{}()\\[\\]|]"), "")
            .trim('-')
        if (normalizedPattern.isBlank()) return emptyList()
        return availableIds
            .filter { id ->
                id.contains(normalizedPattern, ignoreCase = true) ||
                    normalizedPattern.contains(id, ignoreCase = true) ||
                    normalizedPattern.split("-").any { part ->
                        part.length >= 3 && id.contains(part, ignoreCase = true)
                    }
            }.sorted()
    }
}

class FragmentResolvingException(
    val pattern: String,
    val requestedBy: String = "",
    val availableIds: List<String> = emptyList(),
    val filteredOutIds: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
) : RuntimeException(
        buildString {
            append("No fragments match pattern '$pattern'")
            if (requestedBy.isNotBlank()) {
                append(" (required by $requestedBy)")
            }
            append(".")
            if (filteredOutIds.isNotEmpty()) {
                append(" Note: matched by pattern but excluded by project filter: $filteredOutIds.")
            }
            if (suggestions.isNotEmpty()) {
                append(" Similar available: $suggestions.")
            }
            if (availableIds.isNotEmpty()) {
                append(" All available: $availableIds.")
            }
        },
    )
