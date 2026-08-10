package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.userDeployment
import cz.cleanship.aitools.engine.models.ToolType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

/**
 * Which tools the engine knows a user scope for. A tool that has none says so by returning no exporter, which is
 * what lets the engine report the manifest as skipped for it instead of dropping it without a word.
 */
class UserScopeSupportTest {

    @TempDir
    lateinit var userHome: File

    @ParameterizedTest
    @CsvSource("CLAUDE", "CODEX")
    fun `should offer a user scope for the tools whose per-user layout is implemented`(toolType: ToolType) {
        // given
        val adapter = ToolFactory.create(toolType)

        // when
        val exporter = adapter.userScope(userHome, userDeployment)

        // then
        assertThat(exporter).isNotNull()
    }

    @ParameterizedTest
    @CsvSource("WINDSURF", "ANTIGRAVITY", "GITHUB_COPILOT", "CURSOR")
    fun `should offer no user scope for the tools whose per-user layout is not implemented yet`(toolType: ToolType) {
        // given
        val adapter = ToolFactory.create(toolType)

        // when
        val exporter = adapter.userScope(userHome, userDeployment)

        // then
        assertThat(exporter).isNull()
        // - and nothing was written to the home while finding that out
        assertThat(userHome.listFiles()).isEmpty()
    }
}
