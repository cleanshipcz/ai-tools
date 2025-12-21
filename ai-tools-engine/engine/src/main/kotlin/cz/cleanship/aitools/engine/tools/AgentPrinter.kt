package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.AgentManifest

class AgentPrinter : Printer<AgentManifest> {

    override fun print(entity: AgentManifest, output: Output): Output {
        output.appendTextTopic("# ${entity.id}", entity.description)

        output.appendTextTopic("## Persona", entity.persona)

        output.appendListTopic("## Rulepacks", entity.rulepacks)

        output.appendTextTopic("## Prompt", entity.prompt)

        output.appendListTopic("## Constraints", entity.constraints)

        return output
    }
}
