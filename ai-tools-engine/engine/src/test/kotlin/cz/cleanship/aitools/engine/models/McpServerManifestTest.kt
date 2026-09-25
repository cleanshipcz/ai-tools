package cz.cleanship.aitools.engine.models

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.SerializationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class McpServerManifestTest {

    // Decoded with the configuration cz.cleanship.aitools.engine.services.LoaderService reads manifests with, so that strictness here is the strictness a deploy applies.
    private val yaml = Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property))

    @Test
    fun `should decode an inline stdio server with its variables`() {
        // given
        val input = """
            |id: atlassian
            |description: Jira and Confluence
            |transport:
            |    type: stdio
            |    command: ${'$'}{PROJECTS_FOLDER}/jira/.venv/bin/jira-mcp-server
            |    args: [--verbose]
            |    env:
            |        LOG_LEVEL: info
            |variables:
            |    - name: JIRA_PAT
            |      description: Personal access token
            |      secret: true
            |    - name: JIRA_BASE_URL
            |      description: Base URL
            |      secret: false
            |      required: false
            |metadata:
            |    version: 1.0.0
            |    tags: [ai-tools]
        """.trimMargin()

        // when
        val result = yaml.decodeFromString(McpServerManifest.serializer(), input)

        // then
        assertThat(result.id).isEqualTo("atlassian")
        assertThat(result.transport).isEqualTo(
            McpTransport.Stdio(
                command = "\${PROJECTS_FOLDER}/jira/.venv/bin/jira-mcp-server",
                args = listOf("--verbose"),
                env = mapOf("LOG_LEVEL" to "info"),
            ),
        )
        assertThat(result.variables).containsExactly(
            McpVariable(name = "JIRA_PAT", description = "Personal access token", secret = true, required = true),
            McpVariable(name = "JIRA_BASE_URL", description = "Base URL", secret = false, required = false),
        )
        assertThat(result.source).isNull()
        assertThat(result.select).isNull()
        assertThat(result.metadata.tags).containsExactly("ai-tools")
    }

    @Test
    fun `should decode an inline http server with its headers`() {
        // given
        val input = """
            |id: remote
            |description: A remote server
            |transport:
            |    type: http
            |    url: https://example.com/mcp
            |    headers:
            |        Authorization: Bearer ${'$'}{REMOTE_TOKEN}
            |variables:
            |    - name: REMOTE_TOKEN
            |      description: Token
            |      secret: true
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when
        val result = yaml.decodeFromString(McpServerManifest.serializer(), input)

        // then
        assertThat(result.transport).isEqualTo(
            McpTransport.Http(url = "https://example.com/mcp", headers = mapOf("Authorization" to "Bearer \${REMOTE_TOKEN}")),
        )
    }

    @Test
    fun `should decode a pointer server selecting a remote`() {
        // given
        val input = """
            |id: github
            |source: ${'$'}{PROJECTS_FOLDER}/github-mcp-server
            |select:
            |    remote: https://api.githubcopilot.com/mcp/
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when
        val result = yaml.decodeFromString(McpServerManifest.serializer(), input)

        // then
        assertThat(result.source).isEqualTo("\${PROJECTS_FOLDER}/github-mcp-server")
        assertThat(result.select).isEqualTo(McpSourceSelection(remote = "https://api.githubcopilot.com/mcp/"))
        assertThat(result.description).isEmpty()
        assertThat(result.transport).isNull()
        assertThat(result.variables).isEmpty()
    }

    @Test
    fun `should decode a pointer server selecting a package`() {
        // given
        val input = """
            |id: github
            |source: ../github-mcp-server
            |select:
            |    package: ghcr.io/github/github-mcp-server
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when
        val result = yaml.decodeFromString(McpServerManifest.serializer(), input)

        // then
        assertThat(result.select).isEqualTo(McpSourceSelection(packageIdentifier = "ghcr.io/github/github-mcp-server"))
    }

    @ParameterizedTest
    @CsvSource(
        // - a misspelling of a top-level field
        "sources",
        // - the transport of the Codex format is not a key of this model
        "env_vars",
    )
    fun `should reject a key the model does not declare`(key: String) {
        // given
        val input = """
            |id: github
            |source: ../github-mcp-server
            |$key: something
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when / then
        assertThatThrownBy { yaml.decodeFromString(McpServerManifest.serializer(), input) }
            .isInstanceOf(YamlException::class.java)
            .hasMessageContaining(key)
    }

    @Test
    fun `should reject a variable that does not say whether it is secret`() {
        // given
        // - secrecy has no default, so forgetting it can never write a secret value into a generated file
        val input = """
            |id: atlassian
            |description: Jira
            |transport:
            |    type: stdio
            |    command: jira-mcp-server
            |variables:
            |    - name: JIRA_PAT
            |      description: Token
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when / then
        assertThatThrownBy { yaml.decodeFromString(McpServerManifest.serializer(), input) }
            .isInstanceOf(YamlException::class.java)
            .hasMessageContaining("secret")
    }

    @Test
    fun `should decode the pin of a pointer server and leave it null when the manifest declares none`() {
        // given
        val input = """
            |id: github
            |source: ${'$'}{PROJECTS_FOLDER}/github-mcp-server
            |pin: sha256:38d2395945342d544b57055e46d5faaae01f51cb4304bbd5182ec60489c33372
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when
        val result = yaml.decodeFromString(McpServerManifest.serializer(), input)

        // then
        assertThat(result.pin).isEqualTo("sha256:38d2395945342d544b57055e46d5faaae01f51cb4304bbd5182ec60489c33372")
        assertThat(result.copy(pin = null).pin).isNull()
        assertThat(yaml.decodeFromString(McpServerManifest.serializer(), input.lines().filterNot { it.startsWith("pin:") }.joinToString("\n")).pin).isNull()
    }

    @Test
    fun `should reject a transport type the model does not declare`() {
        // given
        val input = """
            |id: legacy
            |description: Legacy
            |transport:
            |    type: sse
            |    url: https://example.com/sse
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when / then
        // - kaml reports an unknown polymorphic type as a plain serialization failure rather than a YamlException
        assertThatThrownBy { yaml.decodeFromString(McpServerManifest.serializer(), input) }
            .isInstanceOf(SerializationException::class.java)
            .hasMessageContaining("sse")
    }
}
