package cz.cleanship.aitools.engine.tools

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

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

    @ParameterizedTest
    @CsvSource(
        // - a control character, which YAML allows only escaped
        "'bell\u0007here', '\"bell\\x07here\"'",
        // - an escape character
        "'esc\u001bhere', '\"esc\\x1bhere\"'",
        // - a delete character and a control character of the C1 range
        "'del\u007fnext\u0085', '\"del\\x7fnext\\x85\"'",
    )
    fun `should escape a control character`(input: String, expected: String) {
        // when
        val value = Frontmatter.value(input)

        // then
        assertThat(value).isEqualTo(expected)
    }

    @Test
    fun `should write plain ids as plain scalars and every other id quoted`() {
        // when
        val list = Frontmatter.list(listOf("github", "xbid.bobcat", "odd: id", "true"))

        // then
        assertThat(list).isEqualTo("[github, xbid.bobcat, \"odd: id\", \"true\"]")
    }

    /**
     * Every id written into a list reads back as the same string, whatever a YAML parser would otherwise make of it.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            // - words YAML reads as a boolean or null
            "true", "False", "Null", "yes", "on", "y", "NO", "off",
            // - a leading digit, a number, and an exponent
            "1abc", "12", "1e3", "0x1F", ".inf", "NaN",
            // - characters YAML reads as syntax
            "~", "a#b", "#a", "a: b", "a:b", "a,b", "a]b", "{}", "*a", "&a", "!a", "%a", "@a", "`a", "|a", ">a", "?a", "-a", " a", "a ",
            // - quotes and a backslash
            "a\"b", "a'b", "'a", "a\\b",
            // - text beyond ASCII, and a control character
            "\u00e9", "a\u0007b",
        ],
    )
    fun `should write a list every id of which reads back unchanged`(id: String) {
        // given
        val ids = listOf("github", id)

        // when
        val parsed = Yaml.default.decodeFromString(ListSerializer(String.serializer()), Frontmatter.list(ids))

        // then
        assertThat(parsed).isEqualTo(ids)
    }
}
