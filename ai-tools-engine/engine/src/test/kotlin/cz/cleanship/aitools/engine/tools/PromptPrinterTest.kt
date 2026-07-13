package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.expectedPrompt
import cz.cleanship.aitools.engine.data.expectedPromptWithFragments
import cz.cleanship.aitools.engine.data.fragments
import cz.cleanship.aitools.engine.data.prompt
import cz.cleanship.aitools.engine.data.promptWithFragments
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PromptPrinterTest {

    @Test
    fun `should print prompt manifest`() {
        // given
        val context = PromptContext(prompt, rulesets)
        val output = StringOutput()

        // when
        output.use {
            PromptPrinter().print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedPrompt)
    }

    @Test
    fun `should print prompt manifest with fragments`() {
        // given
        val context = PromptContext(promptWithFragments, rulesets, fragments = fragments)
        val output = StringOutput()

        // when
        output.use {
            PromptPrinter().print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedPromptWithFragments)
    }
}
