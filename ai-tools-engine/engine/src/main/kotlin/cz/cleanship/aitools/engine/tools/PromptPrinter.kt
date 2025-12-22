package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.PromptManifest

class PromptPrinter : Printer<PromptContext> {

    override fun print(entity: PromptContext, output: Output): Output {
        output.appendTextTopic("# ${entity.prompt.id}", entity.prompt.description)

        output.appendListTopic(
            "## Variables",
            entity.prompt.variables.map {
                val required = if (it.required) " (required)" else ""
                "`{{${it.name}}}`$required: ${it.description}"
            }
        )

        output.appendListTopic("## Rules", entity.prompt.rules)

        output.appendTextTopic("## Prompt", entity.prompt.content)

        return output
    }
}

data class PromptContext(
    val prompt: PromptManifest,
)
