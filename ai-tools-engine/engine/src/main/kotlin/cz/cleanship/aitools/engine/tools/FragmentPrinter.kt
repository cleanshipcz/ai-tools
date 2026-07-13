package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.FragmentManifest

class FragmentPrinter : Printer<FragmentManifest> {

    override fun print(entity: FragmentManifest, output: Output): Output {
        output.appendTextTopic("# ${entity.id}", entity.description)

        output.appendTextTopic("## Content", entity.content)

        return output
    }
}
