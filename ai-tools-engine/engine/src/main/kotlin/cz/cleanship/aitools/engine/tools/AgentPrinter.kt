package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.telemetry.Telemetry
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val telemetry = Telemetry.create()

class AgentPrinter(
    private val rulesetResolver: RulesetResolver = RulesetResolver(),
) : Printer<AgentContext> {

    override fun print(entity: AgentContext, output: Output): Output = runBlocking {
        telemetry.inSpan(
            name = "AgentPrinter.print",
            attributes = mapOf(
                "agent" to entity.agent,
            )
        ) {
            try {
                val (agent, rulesets) = entity
                output.appendTextTopic("# ${agent.id}", agent.description)

                output.appendTextTopic("## Persona", agent.persona)

                val matchedRulesets = rulesetResolver.resolve(agent.rulesets, rulesets)
                output.appendListTopic("## Rules", matchedRulesets.flatMap { it.rules } + agent.rules)

                output.appendTextTopic("## Prompt", agent.prompt)

                output.appendListTopic("## Constraints", agent.constraints)

                return@inSpan output
            } catch (ex: Exception) {
                logger.atError().log("Error during agent printing: ${entity.agent.id}", ex)
                throw ex
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentPrinter::class.java.name)
    }
}

data class AgentContext(
    val agent: AgentManifest,
    val rulesets: Map<String, RulesetManifest>,
)
