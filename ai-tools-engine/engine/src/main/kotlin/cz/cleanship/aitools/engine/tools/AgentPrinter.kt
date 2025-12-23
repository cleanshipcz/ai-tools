package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.RulesetManifest

class AgentPrinter : Printer<AgentContext> {

    override fun print(entity: AgentContext, output: Output): Output {
        val (agent, rulesets) = entity
        output.appendTextTopic("# ${agent.id}", agent.description)

        output.appendTextTopic("## Persona", agent.persona)

        output.appendListTopic("## Rules", agent.rulesets.flatMap {
            rulesets[it]?.rules ?: throw IllegalArgumentException("Missing required ruleset $it in: $entity")
        } + agent.rules)

        output.appendTextTopic("## Prompt", agent.prompt)

        output.appendListTopic("## Constraints", agent.constraints)

        return output
    }
}

data class AgentContext(
    val agent: AgentManifest,
    val rulesets: Map<String, RulesetManifest>,
)
