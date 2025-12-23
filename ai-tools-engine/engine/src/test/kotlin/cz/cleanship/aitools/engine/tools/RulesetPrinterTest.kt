package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.expectedRuleset
import cz.cleanship.aitools.engine.data.ruleset
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RulesetPrinterTest {

    @Test
    fun `should print ruleset manifest`() {
        // given
        val ruleset = ruleset
        val output = StringOutput()

        // when
        output.use {
            RulesetPrinter().print(ruleset, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedRuleset)
    }

}
