package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulesetManifest

class PromptPrinter(
    private val rulesetResolver: RulesetResolver = RulesetResolver(),
    private val fragmentResolver: FragmentResolver = FragmentResolver(),
) : Printer<PromptContext> {

    override fun print(entity: PromptContext, output: Output): Output {
        val (prompt, rulesets, allRulesets, fragments, allFragments) = entity
        output.appendTextTopic("# ${prompt.id}", prompt.description)

        output.appendListTopic(
            "## Variables",
            prompt.variables.map {
                val required = if (it.required) " (required)" else ""
                "`{{${it.name}}}`$required: ${it.description}"
            },
        )

        val matchedRulesets = rulesetResolver.resolve(
            patterns = prompt.rulesets,
            available = rulesets,
            requestedBy = "prompt '${prompt.id}'",
            allRulesets = allRulesets,
        )
        output.appendListTopic("## Rules", matchedRulesets.flatMap { it.rules } + prompt.rules)

        output.appendTextTopic("## Prompt", entity.prompt.content)

        val matchedFragments = fragmentResolver.resolve(
            patterns = prompt.fragments,
            available = fragments,
            requestedBy = "prompt '${prompt.id}'",
            allFragments = allFragments,
        )
        if (matchedFragments.isNotEmpty()) {
            output.appendLine("## Fragments")
            output.appendLine()
            for (fragment in matchedFragments) {
                output.appendTextTopic("### ${fragment.id}", fragment.content)
            }
        }

        return output
    }
}

data class PromptContext(
    val prompt: PromptManifest,
    val rulesets: Map<String, RulesetManifest>,
    val allRulesets: Map<String, RulesetManifest> = rulesets,
    val fragments: Map<String, FragmentManifest> = emptyMap(),
    val allFragments: Map<String, FragmentManifest> = fragments,
)
