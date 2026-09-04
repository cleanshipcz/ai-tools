package cz.cleanship.aitools.engine.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscardingOutputTest {

    @Test
    fun `should accept every kind of write without failing`() {
        // when
        val error = runCatching {
            DiscardingOutput.use {
                it.appendText("text")
                it.appendLine("line")
                it.appendLine()
                it.appendTextTopic("## Header", "content")
                it.appendListTopic("## List", listOf("one", "two"))
            }
        }.exceptionOrNull()

        // then
        assertThat(error).isNull()
    }

    @Test
    fun `should let a decorating output observe what is written to it`() {
        // given
        // - a dry run renders through this output so that a printer still runs to completion; the writes it
        //   forwards have to be the real ones, or a decorator counting them would see a shorter document
        val counting = CountingOutput(DiscardingOutput)

        // when
        counting.appendText("text")
        counting.appendLine("line")

        // then
        assertThat(counting.characterCount).isEqualTo("text".length + 1 + "line".length + 1)
    }
}
