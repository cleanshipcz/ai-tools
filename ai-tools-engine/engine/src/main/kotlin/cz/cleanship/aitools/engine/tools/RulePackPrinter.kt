package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.RulepackManifest

class RulePackPrinter : Printer<RulepackManifest> {

    override fun print(entity: RulepackManifest, output: Output): Output {
        output.appendLine("# ${entity.id}")
        output.appendLine()
        output.appendText(entity.description)
        output.appendLine()

        if (entity.rules.isNotEmpty()) {
            output.appendLine("## Rules")
            output.appendLine()
            for (rule in entity.rules) {
                output.appendLine("- $rule")
            }
            output.appendLine()
        }

        return output
    }
}
