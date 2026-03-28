package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.RulesetManifest

class AgentPrinter(
    private val rulesetResolver: RulesetResolver = RulesetResolver(),
) : Printer<AgentContext> {

    override fun print(entity: AgentContext, output: Output): Output {
        val (agent, rulesets, allRulesets) = entity
        output.appendTextTopic("# ${agent.id}", agent.description)

        output.appendTextTopic("## Persona", agent.persona)

        val matchedRulesets = rulesetResolver.resolve(
            patterns = agent.rulesets,
            available = rulesets,
            requestedBy = "agent '${agent.id}'",
            allRulesets = allRulesets,
        )
        output.appendListTopic("## Rules", matchedRulesets.flatMap { it.rules } + agent.rules)

        output.appendTextTopic("## Prompt", agent.prompt)

        output.appendListTopic("## Constraints", agent.constraints)

        return output
    }
}

data class AgentContext(
    val agent: AgentManifest,
    val rulesets: Map<String, RulesetManifest>,
    val allRulesets: Map<String, RulesetManifest> = rulesets,
)
