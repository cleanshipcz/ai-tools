package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpText
import cz.cleanship.aitools.engine.models.McpVariable
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The rules of the launcher for a pointer server built without the reader, which refuses every such name of a server.json before these rules run: each message offers only what a pointer manifest can change.
 */
class McpSecretSourceValidationTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            // - a secret read through the secrets manager under a name of class B, and a fixed environment variable of class A beside such a secret
            "secret | HTTPS_PROXY | takes the secret variable 'HTTPS_PROXY' from its server.json",
            "fixed  | LD_PRELOAD  | takes a secret variable from its server.json, so on a machine with a secrets manager a tool starts it through the launcher",
        ],
    )
    fun `should tell a pointer server whose name the launcher refuses only to select another package, remote or file`(
        role: String,
        name: String,
        statement: String,
    ) {
        // given
        val secret = McpVariable(if (role == "secret") name else "API_TOKEN", "Token", secret = true)
        val env = if (role == "fixed") mapOf(name to McpText.literal("x")) else emptyMap()
        val server = McpServer(
            id = "github",
            description = "GitHub",
            metadata = ManifestMetadata(version = Version("1.0.0")),
            transport = McpServerTransport.Stdio(command = "npx", args = emptyList(), env = env),
            variables = listOf(secret),
            pointer = true,
        )

        // when / then
        assertThatThrownBy { server.requireSecretSources() }
            .isInstanceOf(InvalidMcpServerManifestException::class.java)
            .hasMessageStartingWith("MCP server 'github' $statement")
            .hasMessageContaining("'$name'")
            .hasMessageEndingWith(POINTER_REMEDY)
            .hasMessageNotContaining("from: environment'.")
    }
}
