package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulesetManifest

class PromptPrinter(
    private val rulesetResolver: RulesetResolver = RulesetResolver(),
) : Printer<PromptContext> {

    override fun print(entity: PromptContext, output: Output): Output {
        val (prompt, rulesets, allRulesets) = entity
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

        return output
    }
}

data class PromptContext(
    val prompt: PromptManifest,
    val rulesets: Map<String, RulesetManifest>,
    val allRulesets: Map<String, RulesetManifest> = rulesets,
)
