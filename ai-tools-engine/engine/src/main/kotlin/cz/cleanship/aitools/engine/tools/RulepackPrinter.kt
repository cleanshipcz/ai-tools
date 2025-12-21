package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.RulepackManifest

class RulepackPrinter : Printer<RulepackManifest> {

    override fun print(entity: RulepackManifest, output: Output): Output {
        output.appendTextTopic("# ${entity.id}", entity.description)

        output.appendListTopic("## Rules", entity.rules)

        return output
    }
}
