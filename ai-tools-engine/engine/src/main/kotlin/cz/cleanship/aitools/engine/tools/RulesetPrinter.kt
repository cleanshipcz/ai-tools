package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.RulesetManifest

class RulesetPrinter : Printer<RulesetManifest> {

    override fun print(entity: RulesetManifest, output: Output): Output {
        output.appendTextTopic("# ${entity.id}", entity.description)

        output.appendListTopic("## Rules", entity.rules)

        return output
    }
}
