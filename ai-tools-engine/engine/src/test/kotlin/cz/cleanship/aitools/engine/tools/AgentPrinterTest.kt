package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.agent
import cz.cleanship.aitools.engine.data.expectedAgent
import cz.cleanship.aitools.engine.data.rulepacks
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPrinterTest {

    @Test
    fun `should print agent manifest`() {
        // given
        val agent = agent
        val output = StringOutput()

        // when
        output.use {
            AgentPrinter().print(AgentContext(agent, rulepacks), it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedAgent)
    }

}
