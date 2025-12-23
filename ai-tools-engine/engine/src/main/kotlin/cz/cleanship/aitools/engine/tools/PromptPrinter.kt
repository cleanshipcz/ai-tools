package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulesetManifest

class PromptPrinter : Printer<PromptContext> {

    override fun print(entity: PromptContext, output: Output): Output {
        val (prompt, rulesets) = entity
        output.appendTextTopic("# ${prompt.id}", prompt.description)

        output.appendListTopic(
            "## Variables",
            prompt.variables.map {
                val required = if (it.required) " (required)" else ""
                "`{{${it.name}}}`$required: ${it.description}"
            }
        )

        output.appendListTopic("## Rules", prompt.rulesets.flatMap {
            rulesets[it]?.rules ?: throw IllegalArgumentException("Missing required ruleset $it in: $entity")
        } + prompt.rules)

        output.appendTextTopic("## Prompt", entity.prompt.content)

        return output
    }
}

data class PromptContext(
    val prompt: PromptManifest,
    val rulesets: Map<String, RulesetManifest>,
)
