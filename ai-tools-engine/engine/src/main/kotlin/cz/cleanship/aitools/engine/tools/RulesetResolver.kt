package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.RulesetManifest

/**
 * Resolves ruleset patterns (supporting regexps like `coding-.*`) against available rulesets.
 */
class RulesetResolver {

    /**
     * Resolves ruleset patterns against available rulesets.
     *
     * @param patterns List of ruleset IDs or patterns (e.g., `["base", "coding-.*"]`)
     * @param available Map of available rulesets (project-filtered)
     * @param requestedBy Human-readable description of the entity requesting resolution (e.g., "agent 'developer-feature'")
     * @param allRulesets Map of all rulesets before project filtering, used to detect filtered-out matches
     * @return List of matched rulesets, deduplicated by ID
     * @throws RulesetResolvingException if a pattern matches no rulesets
     */
    fun resolve(
        patterns: List<String>,
        available: Map<String, RulesetManifest>,
        requestedBy: String = "",
        allRulesets: Map<String, RulesetManifest> = available,
    ): List<RulesetManifest> = patterns
        .flatMap { pattern ->
            val regex = pattern.toRegex()
            val matched = available.values.filter { it.id.matches(regex) }
            if (matched.isEmpty()) {
                val filteredOut = allRulesets.values
                    .filter { it.id.matches(regex) }
                    .map { it.id }
                val suggestions = findSuggestions(pattern, available.keys)
                throw RulesetResolvingException(
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

class RulesetResolvingException(
    val pattern: String,
    val requestedBy: String = "",
    val availableIds: List<String> = emptyList(),
    val filteredOutIds: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
) : RuntimeException(
        buildString {
            append("No rulesets match pattern '$pattern'")
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
