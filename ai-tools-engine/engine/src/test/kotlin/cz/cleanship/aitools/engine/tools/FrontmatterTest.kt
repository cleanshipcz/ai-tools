package cz.cleanship.aitools.engine.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FrontmatterTest {

    @Test
    fun `should wrap plain prose in double quotes`() {
        // when
        val value = Frontmatter.value("Run Python tests with pytest")

        // then
        assertThat(value).isEqualTo("\"Run Python tests with pytest\"")
    }

    @Test
    fun `should keep a colon followed by a space inside the quoted value`() {
        // given
        // - the shape that made GitHub Copilot reject a file with "mapping values are not allowed in this context"
        val description = "Orchestrate delivery of a change: plan, implement, review (claude --agent team-lead)."

        // when
        val value = Frontmatter.value(description)

        // then
        assertThat(value).isEqualTo("\"$description\"")
    }

    @Test
    fun `should collapse line breaks to spaces`() {
        // when
        val value = Frontmatter.value("Multiline\ndescription\r\nwith breaks")

        // then
        assertThat(value).isEqualTo("\"Multiline description with breaks\"")
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Say \"hello\" | \"Say \\\"hello\\\"\"",
            "A back\\slash | \"A back\\\\slash\"",
            "Leading # hash | \"Leading # hash\"",
            "'' | \"\"",
        ],
    )
    fun `should escape the characters that are special in a double-quoted scalar`(input: String, expected: String) {
        // when
        val value = Frontmatter.value(input)

        // then
        assertThat(value).isEqualTo(expected)
    }

    @Test
    fun `should escape a tab`() {
        // when
        val value = Frontmatter.value("tab\there")

        // then
        assertThat(value).isEqualTo("\"tab\\there\"")
    }
}
