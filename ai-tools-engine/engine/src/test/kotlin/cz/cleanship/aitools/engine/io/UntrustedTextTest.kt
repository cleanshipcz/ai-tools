package cz.cleanship.aitools.engine.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class UntrustedTextTest {

    @Test
    fun `should keep plain text as it is`() {
        // given
        val text = "mcp__github__get_me .mcp.json café 名前"

        // when
        val escaped = text.escapedForMessage()

        // then
        assertThat(escaped).isEqualTo(text)
    }

    /**
     * A key or an id read from a file must never start a line of its own in a log or a message, where it could pass for a line the engine wrote.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("breakingTexts")
    fun `should escape every character that could break a line or steer a terminal`(
        case: String,
        text: String,
        expected: String,
    ) {
        // when
        val escaped = text.escapedForMessage()

        // then
        assertThat(escaped).describedAs(case).isEqualTo(expected)
    }

    @Test
    fun `should keep text of exactly the longest length whole`() {
        // given
        val text = "x".repeat(MAX)

        // when
        val escaped = text.escapedForMessage()

        // then
        assertThat(escaped).isEqualTo(text)
    }

    @Test
    fun `should cut longer text to the longest length and end it with an ellipsis`() {
        // given
        val text = "x".repeat(MAX + 1)

        // when
        val escaped = text.escapedForMessage()

        // then
        assertThat(escaped).isEqualTo("x".repeat(MAX - 1) + "\u2026")
    }

    @Test
    fun `should count the escapes towards the longest length and never cut one in half`() {
        // given
        // - every line break becomes six characters, so the escaped text is far longer than the limit
        val text = "\n".repeat(MAX)

        // when
        val escaped = text.escapedForMessage()

        // then
        assertThat(escaped).hasSizeLessThanOrEqualTo(MAX).endsWith("\\u000A\u2026").doesNotContain("\n")
        assertThat(escaped.removeSuffix("\u2026").replace("\\u000A", "")).isEmpty()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsafeTexts")
    fun `should find a character it escapes in the text`(
        case: String,
        text: String,
    ) {
        // when
        val holds = text.holdsControlCharacter()

        // then
        assertThat(holds).describedAs(case).isTrue()
    }

    @Test
    fun `should find no character it escapes in plain text, whatever its script`() {
        // given
        val text = "mcp__github__get_me .mcp.json café 名前 \uD83D\uDE00"

        // when
        val holds = text.holdsControlCharacter()

        // then
        assertThat(holds).isFalse()
    }

    companion object {
        private const val MAX = 160

        @JvmStatic
        fun breakingTexts(): Stream<Arguments> = Stream.of(
            Arguments.of("a line break followed by a forged log line", "a\nERROR forged", "a\\u000AERROR forged"),
            Arguments.of("a carriage return, a tab and a NUL", "a\rb\tc\u0000d", "a\\u000Db\\u0009c\\u0000d"),
            Arguments.of("an escape sequence a terminal would run", "\u001b[2Jx", "\\u001B[2Jx"),
            Arguments.of("DEL and a C1 control character", "a\u007fb\u0085c", "a\\u007Fb\\u0085c"),
            Arguments.of("the line and paragraph separators of Unicode", "a\u2028b\u2029c", "a\\u2028b\\u2029c"),
            // - format characters start no line, but a terminal renders them, so they could reverse or hide text inside a message
            Arguments.of("a right-to-left override", "a\u202Eb", "a\\u202Eb"),
            Arguments.of("a zero width space", "a\u200Bb", "a\\u200Bb"),
            Arguments.of("a byte order mark", "a\uFEFFb", "a\\uFEFFb"),
            Arguments.of("a left-to-right isolate", "a\u2066b", "a\\u2066b"),
            Arguments.of("a format character outside the basic plane, as its two halves", "a\uDB40\uDC01b", "a\\uDB40\\uDC01b"),
        )

        @JvmStatic
        fun unsafeTexts(): Stream<Arguments> = Stream.of(
            Arguments.of("a line break", "a\nb"),
            Arguments.of("an escape sequence", "\u001b[2J"),
            Arguments.of("a C1 control character", "a\u0085b"),
            Arguments.of("a paragraph separator", "a\u2029b"),
            Arguments.of("a lone surrogate", "a\uD800b"),
            Arguments.of("a right-to-left override", "a\u202Eb"),
            Arguments.of("a zero width space", "a\u200Bb"),
            Arguments.of("a byte order mark", "\uFEFFa"),
            Arguments.of("a left-to-right isolate", "a\u2066b"),
            Arguments.of("a format character outside the basic plane", "a\uDB40\uDC01b"),
        )
    }
}
