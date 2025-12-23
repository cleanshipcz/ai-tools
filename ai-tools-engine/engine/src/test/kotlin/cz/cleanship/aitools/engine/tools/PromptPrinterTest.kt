package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.rulepacks
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PromptPrinterTest {

    @Test
    fun `should print prompt manifest`() {
        // given
        val context = PromptContext(prompt, rulepacks)
        val output = StringOutput()

        // when
        output.use {
            PromptPrinter().print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedPrompt)
    }

}