package cz.cleanship.aitools.engine.tools.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class JsonMcpConfigFormatTest {

    private val file = File("/project/.mcp.json")

    private val stdioServer = ResolvedMcpServer(
        id = "atlassian",
        transport = ResolvedMcpTransport.Stdio(
            command = "/work/jira-mcp-server",
            args = listOf("--verbose"),
            env = linkedMapOf(
                "JIRA_BASE_URL" to McpValue.Plain("https://jira.example.com"),
                "JIRA_PAT" to McpValue.Secret("JIRA_PAT", required = true),
                "CONFLUENCE_PAT" to McpValue.Secret("CONFLUENCE_PAT", required = false),
            ),
        ),
    )

    private val httpServer = ResolvedMcpServer(
        id = "github",
        transport = ResolvedMcpTransport.Http(
            url = "https://api.githubcopilot.com/mcp/",
            headers = linkedMapOf(
                "Authorization" to McpValue.BearerSecret("GITHUB_TOKEN", required = true),
                "X-Api-Key" to McpValue.Secret("API_KEY", required = true),
                "X-Region" to McpValue.Plain("eu"),
            ),
        ),
    )

    @Nested
    inner class ClaudeCode {

        private val format = JsonMcpConfigFormat.CLAUDE_CODE

        @Test
        fun `should write every server under mcpServers with an explicit type and secrets as shell references`() {
            // when
            val content = format.merge(null, listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // then
            assertThat(content).isEqualTo(
                """
                {
                  "mcpServers": {
                    "atlassian": {
                      "type": "stdio",
                      "command": "/work/jira-mcp-server",
                      "args": [
                        "--verbose"
                      ],
                      "env": {
                        "JIRA_BASE_URL": "https://jira.example.com",
                        "JIRA_PAT": "${'$'}{JIRA_PAT}",
                        "CONFLUENCE_PAT": "${'$'}{CONFLUENCE_PAT:-}"
                      }
                    },
                    "github": {
                      "type": "http",
                      "url": "https://api.githubcopilot.com/mcp/",
                      "headers": {
                        "Authorization": "Bearer ${'$'}{GITHUB_TOKEN}",
                        "X-Api-Key": "${'$'}{API_KEY}",
                        "X-Region": "eu"
                      }
                    }
                  }
                }

                """.trimIndent(),
            )
        }

        @Test
        fun `should leave out arguments and environment a server does not have`() {
            // given
            val bare =
                ResolvedMcpServer("bare", ResolvedMcpTransport.Stdio(command = "server", args = emptyList(), env = emptyMap()))

            // when
            val content = format.merge(null, listOf(bare), setOf("bare"), file)

            // then
            assertThat(servers(content, "mcpServers").getValue("bare").jsonObject.keys).containsExactly("type", "command")
        }
    }

    @Nested
    inner class VsCode {

        private val format = JsonMcpConfigFormat.VS_CODE

        @Test
        fun `should write every server under servers with an explicit type and secrets as env references`() {
            // when
            val content = format.merge(null, listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // then
            val servers = servers(content, "servers")
            assertThat(servers.getValue("atlassian").toString())
                .contains("\"type\":\"stdio\"")
                .contains("\"JIRA_PAT\":\"\${env:JIRA_PAT}\"")
                .contains("\"CONFLUENCE_PAT\":\"\${env:CONFLUENCE_PAT}\"")
            assertThat(servers.getValue("github").toString())
                .contains("\"type\":\"http\"")
                .contains("\"Authorization\":\"Bearer \${env:GITHUB_TOKEN}\"")
                .contains("\"X-Api-Key\":\"\${env:API_KEY}\"")
        }
    }

    @Nested
    inner class Cursor {

        private val format = JsonMcpConfigFormat.CURSOR

        @Test
        fun `should type a stdio server, leave a remote one to its url and write secrets as env references`() {
            // when
            val content = format.merge(null, listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // then
            val servers = servers(content, "mcpServers")
            assertThat(servers.getValue("atlassian").jsonObject["type"].toString()).isEqualTo("\"stdio\"")
            assertThat(servers.getValue("atlassian").toString()).contains("\"JIRA_PAT\":\"\${env:JIRA_PAT}\"")
            assertThat(servers.getValue("github").jsonObject.keys).containsExactly("url", "headers")
            assertThat(servers.getValue("github").toString()).contains("\"Authorization\":\"Bearer \${env:GITHUB_TOKEN}\"")
        }
    }

    @Nested
    inner class EntryOwnership {

        private val format = JsonMcpConfigFormat.CLAUDE_CODE

        @Test
        fun `should keep every entry and key it does not own, replace an owned entry in place and append a new one`() {
            // given
            // - a file the user edited: a foreign server before and after an owned one, and a top-level key of their own
            val existing =
                """
                {
                  "${'$'}comment": "kept",
                  "mcpServers": {
                    "playwright": { "type": "stdio", "command": "npx", "args": ["-y", "@playwright/mcp"] },
                    "atlassian": { "type": "stdio", "command": "old" },
                    "slack": { "type": "http", "url": "https://slack.example.com" }
                  }
                }
                """.trimIndent()

            // when
            val content = format.merge(existing, listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // then
            val root = Json.parseToJsonElement(content).jsonObject
            assertThat(root.keys).containsExactly("\$comment", "mcpServers")
            val servers = root.getValue("mcpServers").jsonObject
            assertThat(servers.keys).containsExactly("playwright", "atlassian", "slack", "github")
            assertThat(servers.getValue("playwright")).isEqualTo(Json.parseToJsonElement("""{ "type": "stdio", "command": "npx", "args": ["-y", "@playwright/mcp"] }"""))
            assertThat(servers.getValue("slack")).isEqualTo(Json.parseToJsonElement("""{ "type": "http", "url": "https://slack.example.com" }"""))
            assertThat(servers.getValue("atlassian").toString()).contains("/work/jira-mcp-server").doesNotContain("old")
        }

        @Test
        fun `should remove an owned entry the deployment no longer selects`() {
            // given
            val existing = """{ "mcpServers": { "github": { "type": "http", "url": "https://old" }, "playwright": { "command": "npx" } } }"""

            // when
            val content = format.merge(existing, listOf(stdioServer), setOf("atlassian", "github"), file)

            // then
            assertThat(servers(content, "mcpServers").keys).containsExactly("playwright", "atlassian")
        }

        @Test
        fun `should produce the same file when merged twice`() {
            // given
            val first = format.merge("""{ "mcpServers": { "playwright": { "command": "npx" } } }""", listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // when
            val second = format.merge(first, listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // then
            assertThat(second).isEqualTo(first)
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            // - the cases are JSON, so the quote character is one they never contain
            quoteCharacter = '~',
            value = [
                // - not JSON at all
                "{ not json                                  | is not valid JSON",
                // - JSON with a comment, which a rewrite would drop
                "{ // mine{n} \"mcpServers\": {} }             | is not valid JSON",
                // - a top level that is not an object
                "[]                                          | is not a JSON object",
                // - a server list that is not an object
                "{ \"mcpServers\": [] }                        | 'mcpServers'",
            ],
        )
        fun `should refuse to rewrite a file it cannot read without losing content`(
            existing: String,
            expected: String,
        ) {
            // when / then
            assertThatThrownBy { format.merge(existing.replace("{n}", "\n"), listOf(stdioServer), setOf("atlassian"), file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining(expected)
        }
    }

    @Nested
    inner class UntrustedContent {

        private val format = JsonMcpConfigFormat.CURSOR

        @Test
        fun `should refuse a file with a comment without echoing any of its content`() {
            // given
            // - VS Code and Cursor accept comments, so a foreign entry holding a literal token sits next to one
            val existing = "{ \"mcpServers\": { \"mine\": { \"env\": { \"API_KEY\": \"sk-LITERAL-CURSOR-TOKEN\" } } } // my server\n}"

            // when / then
            assertThatThrownBy { format.merge(existing, listOf(stdioServer), setOf("atlassian"), file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageNotContaining("sk-LITERAL-CURSOR-TOKEN")
                .hasMessageNotContaining("my server")
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            quoteCharacter = '~',
            value = [
                // - two server entries of one name, which a rewrite would collapse into the last one
                "{ \"mcpServers\": { \"mine\": { \"command\": \"a\" }, \"mine\": { \"command\": \"b\" } } } | 'mine'",
                // - two top-level keys of one name
                "{ \"x\": 1, \"mcpServers\": {}, \"x\": 2 } | 'x'",
                // - a duplicate key in a nested object of a foreign entry
                "{ \"mcpServers\": { \"mine\": { \"env\": { \"A\": \"1\", \"A\": \"2\" } } } } | 'A'",
            ],
        )
        fun `should refuse a file holding a duplicate key instead of collapsing it`(
            existing: String,
            expected: String,
        ) {
            // when / then
            assertThatThrownBy { format.merge(existing, listOf(stdioServer), setOf("atlassian"), file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining(expected)
        }

        @Test
        fun `should refuse a file whose foreign content cannot be written back byte for byte`() {
            // given
            // - a lone surrogate in a foreign entry, which the UTF-8 encoding of a rewrite would turn into '?'
            val existing = "{\"mcpServers\":{\"mine\":{\"command\":\"\\ud800x\"}}}"

            // when / then
            assertThatThrownBy { format.merge(existing, listOf(stdioServer), setOf("atlassian"), file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
        }

        @Test
        fun `should name the owned entries an existing file holds`() {
            // given
            val existing = """{ "mcpServers": { "github": {}, "playwright": {} } }"""

            // when
            val owned = format.ownedEntriesIn(existing, setOf("github", "atlassian"), file)

            // then
            assertThat(owned).containsExactly("github")
        }
    }

    private fun servers(content: String, key: String): JsonObject = Json
        .parseToJsonElement(content)
        .jsonObject
        .getValue(key)
        .jsonObject

    /**
     * A JSON config file is written again as a whole, so whatever mix of LF and CRLF it uses and whether it ends in a newline, the merge reads it and keeps every foreign entry.
     */
    @ParameterizedTest
    @CsvSource(
        "lf, true",
        "lf, false",
        "crlf, true",
        "crlf, false",
        "mixed, true",
        "mixed, false",
    )
    fun `should merge into a file of any line ending and with or without a final newline`(
        style: String,
        finalNewline: Boolean,
    ) {
        // given
        val formats = listOf(JsonMcpConfigFormat.CLAUDE_CODE, JsonMcpConfigFormat.VS_CODE, JsonMcpConfigFormat.CURSOR)
        val endings = (0 until 5).map { index -> if (style == "crlf" || (style == "mixed" && index % 2 == 0)) "\r\n" else "\n" }
        // - one file per format, holding a foreign entry under the key that format reads
        val existing = formats.associateWith { format ->
            val key = if (format === JsonMcpConfigFormat.VS_CODE) "servers" else "mcpServers"
            val lines = listOf("{", "  \"$key\": {", "    \"mine\": { \"command\": \"npx\" }", "  }", "}")
            lines.zip(endings).joinToString("") { (line, ending) -> line + ending }.let { if (finalNewline) it else it.removeSuffix(endings.last()) }
        }

        // when
        val contents = existing.map { (format, text) -> format.merge(text, listOf(stdioServer), setOf("atlassian"), file) }

        // then
        assertThat(contents).hasSize(3).allSatisfy {
            assertThat(it)
                .contains("\"mine\"")
                .contains("\"atlassian\"")
                .endsWith("}\n")
                .doesNotContain("\r")
        }
    }
}
