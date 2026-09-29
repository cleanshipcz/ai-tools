package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.models.SecretsManagerKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The secrets manager a machine switch names, for the repository the engine runs from.
 */
class McpSecretsManagerTest {

    @TempDir
    lateinit var tempDir: Path

    // - no PATH and no bus, so nothing this test builds could start a program
    private val environment = EnvironmentSource { null }

    private val transport = ResolvedMcpTransport.Stdio(command = "jira-mcp-server", args = emptyList(), env = linkedMapOf("JIRA_PAT" to McpValue.Secret("JIRA_PAT", required = true)))

    @Test
    fun `should use the launcher under scripts of the repository for libsecret`() {
        // given
        val launcher = FakePrograms.executable(tempDir.resolve("repo/scripts/mcp-launch"), "#!/bin/sh\n")

        // when
        val manager = SecretsManagerKind.LIBSECRET.managerFor(tempDir.resolve("repo").toFile(), environment)

        // then
        assertThat(manager).isInstanceOf(LibsecretSecretsManager::class.java)
        assertThat(manager?.launch("atlassian", transport, listOf(McpValue.Secret("JIRA_PAT", required = true)))?.command).isEqualTo(launcher.toRealPath().toString())
    }

    @Test
    fun `should use no secrets manager when the machine reads every secret from the environment`() {
        // when
        val manager = SecretsManagerKind.ENVIRONMENT.managerFor(tempDir.toFile(), environment)

        // then
        assertThat(manager).isNull()
    }
}
