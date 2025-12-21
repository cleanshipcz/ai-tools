package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.PromptManifest

class PromptPrinter : Printer<PromptManifest> {

    override fun print(entity: PromptManifest, output: Output): Output {
        output.appendLine("# ${entity.id}")
        output.appendLine()
        output.appendText(entity.description)
        output.appendLine()

        if (entity.variables.isNotEmpty()) {
            output.appendLine("## Variables")
            output.appendLine()
            for (variable in entity.variables) {
                val required = if (variable.required) " (required)" else ""
                val indentedDescription = variable.description.lines().joinToString("\n  ")
                output.appendLine("- `{{${variable.name}}}`$required: $indentedDescription")
            }
            output.appendLine()
        }

        if (entity.rules.isNotEmpty()) {
            output.appendLine("## Rules")
            output.appendLine()
            for (rule in entity.rules) {
                output.appendLine("- $rule")
            }
            output.appendLine()
        }

        output.appendLine("## Prompt")
        output.appendLine()
        output.appendText(entity.content)
        output.appendLine()

        return output
    }
}
