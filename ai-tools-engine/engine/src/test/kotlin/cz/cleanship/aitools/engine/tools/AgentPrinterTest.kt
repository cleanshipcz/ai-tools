package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPrinterTest {

    @Test
    fun `should print agent manifest`() {
        // given
        val context = AgentContext(agent, rulesets)
        val output = StringOutput()

        // when
        output.use {
            AgentPrinter().print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedAgent)
    }

}
