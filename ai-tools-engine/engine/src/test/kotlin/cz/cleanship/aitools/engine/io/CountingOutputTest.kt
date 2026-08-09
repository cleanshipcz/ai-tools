package cz.cleanship.aitools.engine.io

import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CountingOutputTest {

    private val target = StringOutput()
    private val output = CountingOutput(target)

    @Test
    fun `should count the characters of directly written text`() {
        // when
        output.use {
            it.appendText("abc")
            it.appendLine("de")
            it.appendLine()
        }

        // then
        assertThat(target.getContent()).isEqualTo("abc\nde\n\n")
        assertThat(output.characterCount).isEqualTo(target.getContent().length)
    }

    @Test
    fun `should count the characters of a composed text topic`() {
        // when
        output.use {
            it.appendTextTopic("# Header", "Body")
        }

        // then
        assertThat(target.getContent()).isEqualTo("# Header\n\nBody\n\n")
        assertThat(output.characterCount).isEqualTo(target.getContent().length)
    }

    @Test
    fun `should count the characters of a composed list topic`() {
        // when
        output.use {
            it.appendListTopic("## Header", listOf("one", "two"))
        }

        // then
        assertThat(target.getContent()).isEqualTo("## Header\n\n- one\n- two\n\n")
        assertThat(output.characterCount).isEqualTo(target.getContent().length)
    }

    @Test
    fun `should count nothing when a topic is skipped`() {
        // when
        output.use {
            it.appendTextTopic("# Header", null)
            it.appendListTopic("## Header", emptyList())
        }

        // then
        assertThat(target.getContent()).isEmpty()
        assertThat(output.characterCount).isZero()
    }
}
