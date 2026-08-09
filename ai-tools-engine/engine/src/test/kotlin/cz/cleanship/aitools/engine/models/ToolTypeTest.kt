package cz.cleanship.aitools.engine.models

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ToolTypeTest {

    @ParameterizedTest
    @CsvSource(
        "WINDSURF, windsurf",
        "ANTIGRAVITY, antigravity",
        "GITHUB_COPILOT, github_copilot",
        "CURSOR, cursor",
        "CLAUDE, claude",
        "CODEX, codex",
    )
    fun `should expose the spelling a manifest uses for a tool`(toolType: ToolType, expected: String) {
        // when
        val serialName = toolType.serialName

        // then
        assertThat(serialName).isEqualTo(expected)
    }

    @Test
    fun `should spell every tool in lower case and without duplicates`() {
        // when
        val serialNames = ToolType.entries.map { it.serialName }

        // then
        // - a constant added without its @SerialName would fall back to the upper-case Kotlin name and be caught here
        assertThat(serialNames).allMatch { it == it.lowercase() }
        assertThat(serialNames).doesNotHaveDuplicates()
    }
}
