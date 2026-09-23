package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.expectedSkillWithFragment
import cz.cleanship.aitools.engine.data.expectedSkillWithMixedSections
import cz.cleanship.aitools.engine.data.expectedSkillWithRuleset
import cz.cleanship.aitools.engine.data.expectedTextOnlySkill
import cz.cleanship.aitools.engine.data.fragments
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.skillWithFragment
import cz.cleanship.aitools.engine.data.skillWithMixedSections
import cz.cleanship.aitools.engine.data.skillWithRuleset
import cz.cleanship.aitools.engine.data.sourceBackedSkill
import cz.cleanship.aitools.engine.data.sourceBackedSkillBody
import cz.cleanship.aitools.engine.data.textOnlySkill
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SkillPrinterTest {

    private lateinit var printer: SkillPrinter

    @BeforeEach
    fun setUp() {
        printer = SkillPrinter()
    }

    @Test
    fun `should print skill with text-only sections`() {
        // given
        val context = SkillContext(textOnlySkill)
        val output = StringOutput()

        // when
        output.use {
            printer.print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedTextOnlySkill)
    }

    @Test
    fun `should print skill with ruleset section`() {
        // given
        val context = SkillContext(
            skill = skillWithRuleset,
            availableRulesets = rulesets,
        )
        val output = StringOutput()

        // when
        output.use {
            printer.print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedSkillWithRuleset)
    }

    @Test
    fun `should print skill with fragment section`() {
        // given
        val context = SkillContext(
            skill = skillWithFragment,
            availableFragments = fragments,
        )
        val output = StringOutput()

        // when
        output.use {
            printer.print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedSkillWithFragment)
    }

    @Test
    fun `should print skill with mixed sections`() {
        // given
        val context = SkillContext(
            skill = skillWithMixedSections,
            availableRulesets = rulesets,
            availableFragments = fragments,
        )
        val output = StringOutput()

        // when
        output.use {
            printer.print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedSkillWithMixedSections)
    }

    @Test
    fun `should print skill with no sections`() {
        // given
        val context = SkillContext(textOnlySkill.copy(sections = emptyList()))
        val output = StringOutput()

        // when
        output.use {
            printer.print(context, it)
        }

        // then
        val content = output.getContent().trimIndent()
        assertThat(content).contains("# ${textOnlySkill.id}")
        assertThat(content).contains(textOnlySkill.description)
        assertThat(content).doesNotContain("## When to use")
    }

    @Test
    fun `should throw when ruleset section references unknown ruleset`() {
        // given
        val context = SkillContext(
            skill = skillWithRuleset,
            availableRulesets = emptyMap(),
        )
        val output = StringOutput()

        // when / then
        assertThrows<RulesetResolvingException> {
            output.use {
                printer.print(context, it)
            }
        }
    }

    @Test
    fun `should throw when fragment section references unknown fragment`() {
        // given
        val context = SkillContext(
            skill = skillWithFragment,
            availableFragments = emptyMap(),
        )
        val output = StringOutput()

        // when / then
        assertThrows<FragmentResolvingException> {
            output.use {
                printer.print(context, it)
            }
        }
    }

    @Test
    fun `should print the body of a source-backed skill verbatim without a heading or the description`() {
        // given
        val context = SkillContext(sourceBackedSkill)
        val output = StringOutput()

        // when
        output.use {
            printer.print(context, it)
        }

        // then
        assertThat(output.getContent()).isEqualTo(sourceBackedSkillBody)
    }

    @Test
    fun `should print an empty body of a source-backed skill as nothing`() {
        // given
        val context = SkillContext(sourceBackedSkill.copy(body = ""))
        val output = StringOutput()

        // when
        output.use {
            printer.print(context, it)
        }

        // then
        assertThat(output.getContent()).isEmpty()
    }
}
