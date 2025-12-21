package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.RulepackManifest

class AgentPrinter : Printer<AgentContext> {

    override fun print(entity: AgentContext, output: Output): Output {
        val (agent, rulepacks) = entity
        output.appendTextTopic("# ${agent.id}", agent.description)

        output.appendTextTopic("## Persona", agent.persona)

        output.appendListTopic("## Rules", agent.rulepacks.flatMap {
            rulepacks[it]?.rules ?: throw IllegalArgumentException("Missing required rulepack ${it} in: $entity")
        })

        output.appendTextTopic("## Prompt", agent.prompt)

        output.appendListTopic("## Constraints", agent.constraints)

        return output
    }
}

data class AgentContext(
    val agent: AgentManifest,
    val rulepacks: Map<String, RulepackManifest>,
)
