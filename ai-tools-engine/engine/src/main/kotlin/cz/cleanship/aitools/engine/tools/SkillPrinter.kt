package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.SkillSection

class SkillPrinter(
    private val rulesetResolver: RulesetResolver = RulesetResolver(),
    private val fragmentResolver: FragmentResolver = FragmentResolver(),
) : Printer<SkillContext> {
    /**
     * Prints the content of the skill of [entity] to [output] and returns [output].
     *
     * A skill with a [cz.cleanship.aitools.engine.models.SkillManifest.body] is printed as that body unchanged, with no heading and no description. Any other skill is printed as a `# <id>` heading, its description and its rendered sections.
     *
     * @throws RulesetResolvingException if a ruleset section references a ruleset that cannot be resolved
     * @throws FragmentResolvingException if a fragment section references a fragment that cannot be resolved
     */
    override fun print(entity: SkillContext, output: Output): Output {
        val skill = entity.skill

        // A pointer skill is authored as a plain SKILL.md that already carries its own heading and wording; a generated heading or a repeated description would duplicate them and drift from the source.
        skill.body?.let { body ->
            // Output.appendText ends what it writes with a line break of its own, so the one the body already ends with is dropped to keep the body byte for byte.
            if (body.isNotEmpty()) output.appendText(body.removeSuffix("\n"))
            return output
        }

        output.appendTextTopic("# ${skill.id}", skill.description)

        val sections = skill.sections
        if (sections.isNotEmpty()) {
            val renderedSections = sections.map { section ->
                renderSection(section, entity)
            }
            output.appendText(renderedSections.joinToString("\n\n"))
            output.appendLine()
        }

        return output
    }

    private fun renderSection(section: SkillSection, context: SkillContext): String = when (section) {
        is SkillSection.TextSection -> section.text.trimEnd()
        is SkillSection.RulesetSection -> {
            val resolved = rulesetResolver.resolve(
                patterns = listOf(section.ruleset),
                available = context.availableRulesets,
                requestedBy = "skill '${context.skill.id}'",
                allRulesets = context.allRulesets,
            )
            resolved.flatMap { it.rules }.joinToString("\n") { "- $it" }
        }
        is SkillSection.FragmentSection -> {
            val resolved = fragmentResolver.resolve(
                patterns = listOf(section.fragment),
                available = context.availableFragments,
                requestedBy = "skill '${context.skill.id}'",
                allFragments = context.allFragments,
            )
            resolved.joinToString("\n\n") { it.content.trimEnd() }
        }
    }
}

/**
 * A skill to print and deploy, together with the rulesets and fragments its sections may reference.
 *
 * @property sourceDir the directory a relative companion file of [skill] is copied from: the `source` folder of a skill that declares one, the directory holding the `skill.yml` of a directory-based skill, or `null` for a standalone skill file.
 * @property pointerSourceDirs the source folder of every pointer skill of the run, none of which a companion file of [skill] may land in
 */
data class SkillContext(
    val skill: cz.cleanship.aitools.engine.models.SkillManifest,
    val availableRulesets: Map<String, RulesetManifest> = emptyMap(),
    val allRulesets: Map<String, RulesetManifest> = availableRulesets,
    val availableFragments: Map<String, FragmentManifest> = emptyMap(),
    val allFragments: Map<String, FragmentManifest> = availableFragments,
    val sourceDir: java.io.File? = null,
    val pointerSourceDirs: List<java.io.File>,
)
