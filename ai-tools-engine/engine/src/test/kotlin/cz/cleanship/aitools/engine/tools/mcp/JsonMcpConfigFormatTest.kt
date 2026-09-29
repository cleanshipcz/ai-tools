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
        fun `should write nothing for the variables a server reads from the environment of the tool, which Claude Code passes on`() {
            // given
            val forwarding = stdioServer.copy(transport = (stdioServer.transport as ResolvedMcpTransport.Stdio).copy(forwarded = listOf("DBUS_SESSION_BUS_ADDRESS", "XDG_RUNTIME_DIR")))

            // when
            val content = format.merge(null, listOf(forwarding), setOf("atlassian"), file)

            // then
            assertThat(content).isEqualTo(format.merge(null, listOf(stdioServer), setOf("atlassian"), file)).doesNotContain("DBUS_SESSION_BUS_ADDRESS")
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
        fun `should name every entry an existing file holds`() {
            // given
            val existing = """{ "mcpServers": { "github": {}, "playwright": {} } }"""

            // when
            val entries = format.entryFingerprints(existing, file).keys

            // then
            assertThat(entries).containsExactly("github", "playwright")
        }

        @Test
        fun `should give an entry the same fingerprint whatever its layout and the order of its keys, since a tool may write the file again`() {
            // given
            val compact = """{"mcpServers":{"a":{"command":"x","args":["1","2"],"env":{"A":"1","B":"2"}}}}"""
            val rearranged = "{\n  \"other\": 1,\n  \"mcpServers\": {\n    \"a\": {\n      \"env\": {\"B\": \"2\", \"A\": \"1\"},\n      \"args\": [ \"1\", \"2\" ],\n      \"command\": \"\\u0078\"\n    }\n  }\n}\n"

            // when
            val first = format.entryFingerprints(compact, file)
            val second = format.entryFingerprints(rearranged, file)

            // then
            assertThat(second).isEqualTo(first)
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                // - another value
                """{"mcpServers":{"a":{"command":"y","args":["1","2"]}}}""",
                // - the elements of an array in another order
                """{"mcpServers":{"a":{"command":"x","args":["2","1"]}}}""",
                // - one more key
                """{"mcpServers":{"a":{"command":"x","args":["1","2"],"type":"stdio"}}}""",
                // - a number where a string was
                """{"mcpServers":{"a":{"command":"x","args":[1,"2"]}}}""",
            ],
        )
        fun `should give an entry another fingerprint once its content changes`(changed: String) {
            // given
            val original = """{"mcpServers":{"a":{"command":"x","args":["1","2"]}}}"""

            // when
            val before = format.entryFingerprints(original, file).getValue("a")
            val after = format.entryFingerprints(changed, file).getValue("a")

            // then
            assertThat(after).isNotEqualTo(before)
        }
    }

    /**
     * `~/.claude.json`, which Claude Code rewrites while it runs and which holds its session state: every byte outside the owned entries is kept.
     */
    @Nested
    inner class ClaudeCodeUser {

        private val format = JsonMcpConfigFormat.CLAUDE_CODE_USER
        private val userFile = File("/home/user/.claude.json")

        @Test
        fun `should only insert the owned entry into a large file of session state, change only that entry, and give the original bytes back once it is removed`() {
            // given
            val existing = claudeJson()
            assertThat(existing.length).isGreaterThan(200_000)

            // when
            val added = format.merge(existing, listOf(httpServer), setOf("github", "atlassian"), userFile)
            val changed = format.merge(added, listOf(httpServer.copy(transport = ResolvedMcpTransport.Http("https://changed/mcp", emptyMap()))), setOf("github", "atlassian"), userFile)
            val removed = format.merge(changed, emptyList(), setOf("github", "atlassian"), userFile)

            // then
            assertThat(onlyInsertion(existing, added)).describedAs("the first deploy only inserts the owned entry").isTrue()
            assertThat(servers(added, "mcpServers").keys).containsExactly("playwright", "github")
            assertThat(servers(added, "mcpServers").getValue("github").toString()).contains("\${GITHUB_TOKEN}").contains("\"type\":\"http\"")
            assertThat(onlyReplacement(added, changed, "github")).describedAs("a later deploy changes only the owned entry").isTrue()
            assertThat(removed).describedAs("removing the owned entry restores the file byte for byte").isEqualTo(existing)
        }

        /**
         * The large file in the other layouts a `~/.claude.json` is found in: each is only inserted into, changed only inside the owned entry, and given back byte for byte.
         */
        @ParameterizedTest
        @CsvSource(
            // - lines ending in CRLF
            "crlf",
            // - no line break after the closing brace
            "no-final-newline",
            // - a string holding an escaped NUL character
            "escaped-nul",
            // - an empty servers object
            "empty-servers",
            // - the servers object as the first member
            "first-servers",
        )
        fun `should only insert, change and remove the owned entry of a large file in every layout it is found in`(
            layout: String,
        ) {
            // given
            val existing = claudeJsonIn(layout)
            assertThat(existing.length).isGreaterThan(200_000)

            // when
            val added = format.merge(existing, listOf(httpServer), setOf("github", "atlassian"), userFile)
            val changed = format.merge(added, listOf(httpServer.copy(transport = ResolvedMcpTransport.Http("https://changed/mcp", emptyMap()))), setOf("github", "atlassian"), userFile)
            val removed = format.merge(changed, emptyList(), setOf("github", "atlassian"), userFile)

            // then
            assertThat(onlyInsertion(existing, added)).describedAs("the first deploy only inserts the owned entry").isTrue()
            assertThat(servers(added, "mcpServers").keys).contains("github")
            assertThat(onlyReplacement(added, changed, "github")).describedAs("a later deploy changes only the owned entry").isTrue()
            assertThat(removed).describedAs("removing the owned entry restores the file byte for byte").isEqualTo(existing)
        }

        @Test
        fun `should keep the file byte for byte when it holds no owned entry and none is selected`() {
            // given
            val existing = claudeJson()

            // when
            val merged = format.merge(existing, emptyList(), setOf("github"), userFile)

            // then
            assertThat(merged).isEqualTo(existing)
        }

        @Test
        fun `should create the servers object at the end of a file that has none`() {
            // given
            val existing = "{\n  \"numStartups\": 3\n}\n"

            // when
            val merged = format.merge(existing, listOf(stdioServer), setOf("atlassian"), userFile)

            // then
            assertThat(merged).startsWith("{\n  \"numStartups\": 3,\n  \"mcpServers\": {\n    \"atlassian\": {\n      \"type\": \"stdio\",").endsWith("\n  }\n}\n")
        }

        @Test
        fun `should refuse a file whose servers are not an object, naming the file`() {
            // when / then
            assertThatThrownBy { format.merge("{\"mcpServers\": []}", listOf(stdioServer), setOf("atlassian"), userFile) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(userFile.absolutePath)
                .hasMessageContaining("'mcpServers'")
        }

        /**
         * Returns whether [after] is [before] with one piece of text inserted and nothing else changed.
         */
        private fun onlyInsertion(before: String, after: String): Boolean {
            val prefix = before.commonPrefixWith(after).length
            val suffix = before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
            return after.length > before.length && prefix + suffix == before.length
        }

        /**
         * Returns whether [after] differs from [before] only inside the server entry [id] of [before], which the fixture indents by four spaces.
         */
        private fun onlyReplacement(before: String, after: String, id: String): Boolean {
            val prefix = before.commonPrefixWith(after).length
            val suffix = before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
            val entryStart = before.indexOf("\n    \"$id\": {")
            val entryEnd = before.indexOf("\n    }", entryStart) + "\n    }".length
            return entryStart >= 0 && prefix > entryStart && before.length - suffix <= entryEnd
        }

        /**
         * Returns the file of [claudeJson] in the layout [layout] names.
         */
        private fun claudeJsonIn(layout: String): String {
            val servers = "  \"mcpServers\": {\n    \"playwright\": {\n      \"type\": \"stdio\",\n      \"command\": \"npx\",\n      \"args\": [\n        \"@playwright/mcp@latest\"\n      ],\n      \"env\": {}\n    }\n  },\n"
            val file = claudeJson()
            return when (layout) {
                "crlf" -> file.replace("\n", "\r\n")
                "no-final-newline" -> file.removeSuffix("\n")
                "escaped-nul" -> file.replaceFirst("{\n", "{\n  \"lastPaste\": \"a\\u0000b\",\n")
                "empty-servers" -> file.replace(servers, "  \"mcpServers\": {},\n")
                "first-servers" -> file.replace(servers, "").replaceFirst("{\n", "{\n$servers")
                else -> error("Unknown layout $layout")
            }.also { check(it != file) { "the layout $layout changed nothing" } }
        }

        /**
         * Returns a `~/.claude.json` of more than 200 KB in the layout Claude Code writes: session state, numbers of every form, unicode as escapes and as text, a `projects` object of many entries, and a server added by hand.
         */
        private fun claudeJson(): String = buildString {
            append("{\n")
            append("  \"numStartups\": 412,\n")
            append("  \"installMethod\": \"native\",\n")
            append("  \"autoUpdates\": false,\n")
            append("  \"tipsHistory\": {\n    \"new-user-warmup\": 7,\n    \"memory-command\": 1.5e3,\n    \"theme-command\": -0.25\n  },\n")
            append("  \"oauthAccount\": {\n    \"accountUuid\": \"00000000-0000-0000-0000-000000000000\",\n    \"displayName\": \"Zo\\u00eb \\ud83d\\ude00 François\",\n    \"organizationRole\": null\n  },\n")
            append("  \"cachedChangelog\": \"# Changelog\\n\\n- fixed \\\"quotes\\\" and \\/slashes\\/ \\t tabs\",\n")
            append("  \"bigNumber\": 12345678901234567890,\n")
            append("  \"projects\": {\n")
            val projects = (0 until 400).map { index ->
                "    \"/home/user/Documents/Projects/project-$index\": {\n" +
                    "      \"allowedTools\": [],\n" +
                    "      \"history\": [\n" +
                    (0 until 3).joinToString(",\n") { entry -> "        {\n          \"display\": \"run the tests of module $entry \\u2014 then fix\",\n          \"pastedContents\": {}\n        }" } + "\n" +
                    "      ],\n" +
                    "      \"lastCost\": 0.${index}1,\n" +
                    "      \"hasTrustDialogAccepted\": ${index % 2 == 0}\n" +
                    "    }"
            }
            append(projects.joinToString(",\n")).append("\n  },\n")
            append("  \"mcpServers\": {\n    \"playwright\": {\n      \"type\": \"stdio\",\n      \"command\": \"npx\",\n      \"args\": [\n        \"@playwright/mcp@latest\"\n      ],\n      \"env\": {}\n    }\n  },\n")
            append("  \"userID\": \"abcdef0123456789\"\n")
            append("}\n")
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
