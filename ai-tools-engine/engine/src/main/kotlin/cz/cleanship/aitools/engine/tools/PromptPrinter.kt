package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.PromptManifest

class PromptPrinter : Printer<PromptManifest> {

    override fun print(entity: PromptManifest, output: Output): Output {
        output.appendTextTopic("# ${entity.id}", entity.description)

        output.appendListTopic(
            "## Variables",
            entity.variables.map {
                val required = if (it.required) " (required)" else ""
                "`{{${it.name}}}`$required: ${it.description}"
            }
        )

        output.appendListTopic("## Rules", entity.rules)

        output.appendTextTopic("## Prompt", entity.content)

        return output
    }
}
