package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.expectedRulepack
import cz.cleanship.aitools.engine.data.rulepack
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RulepackPrinterTest {

    @Test
    fun `should print rulepack manifest`() {
        // given
        val rulepack = rulepack
        val output = StringOutput()

        // when
        output.use {
            RulepackPrinter().print(rulepack, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedRulepack)
    }

}
