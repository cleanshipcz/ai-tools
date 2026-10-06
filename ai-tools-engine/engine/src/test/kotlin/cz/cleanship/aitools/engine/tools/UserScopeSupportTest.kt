package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.userDeployment
import cz.cleanship.aitools.engine.models.ToolType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        assertThat(exporter).isInstanceOf(UserScopeExporter::class.java)
    }

    @Test
    fun `should offer GitHub Copilot a user scope of the MCP config file of Copilot CLI alone`() {
        // given
        val adapter = ToolFactory.create(ToolType.GITHUB_COPILOT)

        // when
        val scope = adapter.userScope(userHome, userDeployment)

        // then
        // - no instructions, agent, prompt or skill file is written for it, so it is no exporter of artifacts
        assertThat(scope).isNotNull().isNotInstanceOf(UserScopeExporter::class.java)
        assertThat(scope?.toolDirectories).containsExactly(userHome.resolve(".copilot"))
        assertThat(scope?.mcpConfig()?.file).isEqualTo(userHome.resolve(".copilot/mcp-config.json"))
        assertThat(scope?.mcpConfig()?.hiddenBy).isNull()
        assertThat(scope?.mcpPermissions()).isNull()
        assertThat(userHome.listFiles()).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
        // - the tools that get every artifact of a deployment in the home
        "CLAUDE, true",
        "CODEX, true",
        // - the tool that gets only its MCP files there
        "GITHUB_COPILOT, false",
    )
    fun `should give the exporter of every artifact only for a tool that gets every artifact in the home`(
        toolType: ToolType,
        getsArtifacts: Boolean,
    ) {
        // given
        val scope = requireNotNull(ToolFactory.create(toolType).userScope(userHome, userDeployment))

        // when
        val exporter = scope.artifactExporter

        // then
        assertThat(exporter).isEqualTo(if (getsArtifacts) scope else null)
        assertThat(scope is McpFilesUserScope).isEqualTo(!getsArtifacts)
    }

    @ParameterizedTest
    @CsvSource("WINDSURF", "ANTIGRAVITY", "CURSOR")
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
