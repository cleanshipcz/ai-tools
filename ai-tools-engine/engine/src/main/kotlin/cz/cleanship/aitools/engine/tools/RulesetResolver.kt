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
     * @param available Map of available rulesets
     * @return List of matched rulesets, deduplicated by ID
     * @throws IllegalArgumentException if a pattern matches no rulesets
     */
    fun resolve(
        patterns: List<String>,
        available: Map<String, RulesetManifest>,
    ): List<RulesetManifest> {
        return patterns.flatMap { pattern ->
            val regex = pattern.toRegex()
            val matched = available.values.filter { it.id.matches(regex) }
            require(matched.isNotEmpty()) { "No rulesets match pattern: $pattern" }
            matched
        }.distinctBy { it.id }
    }
}
