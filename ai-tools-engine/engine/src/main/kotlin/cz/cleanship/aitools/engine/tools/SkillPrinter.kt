package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.SkillSection

class SkillPrinter(
    private val rulesetResolver: RulesetResolver = RulesetResolver(),
    private val fragmentResolver: FragmentResolver = FragmentResolver(),
) : Printer<SkillContext> {
    override fun print(entity: SkillContext, output: Output): Output {
        val skill = entity.skill

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

data class SkillContext(
    val skill: cz.cleanship.aitools.engine.models.SkillManifest,
    val availableRulesets: Map<String, RulesetManifest> = emptyMap(),
    val allRulesets: Map<String, RulesetManifest> = availableRulesets,
    val availableFragments: Map<String, FragmentManifest> = emptyMap(),
    val allFragments: Map<String, FragmentManifest> = availableFragments,
    val sourceDir: java.io.File? = null,
)
