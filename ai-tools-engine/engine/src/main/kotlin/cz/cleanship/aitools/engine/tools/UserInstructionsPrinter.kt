package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.UserDeploymentManifest

/**
 * Prints the instructions file a user deployment owns - `~/.claude/CLAUDE.md`, `~/.codex/AGENTS.md` - from a header
 * naming the manifest it was generated from and the rules of every ruleset that manifest selected.
 *
 * It is the user-scope counterpart of [GlobalFilePrinter], which prints the same file for a project. There is no
 * overview, no documentation and no repository to describe here: a user scope belongs to a person rather than to a
 * codebase. The rules of the selected rulesets are flattened into one list, the way every other artifact of the
 * engine renders the rulesets it was given - see [AgentPrinter] and [PromptPrinter].
 */
class UserInstructionsPrinter : Printer<UserInstructionsContext> {

    override fun print(entity: UserInstructionsContext, output: Output): Output {
        output.appendTextTopic("# ${entity.deployment.id}", entity.deployment.description)

        output.appendListTopic("## Rules", entity.rulesets.values.flatMap { it.rules })

        return output
    }
}

/**
 * @param rulesets the rulesets [deployment] selected, already filtered by
 * [cz.cleanship.aitools.engine.services.FilterService]
 */
data class UserInstructionsContext(
    val deployment: UserDeploymentManifest,
    val rulesets: Map<String, RulesetManifest>,
)
