package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.models.McpToolRestriction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.tomlj.Toml
import java.io.File

class CodexTomlMcpConfigFormatTest {

    private val format = CodexTomlMcpConfigFormat
    private val file = File("/project/.codex/config.toml")

    private val stdioServer = ResolvedMcpServer(
        id = "atlassian",
        transport = ResolvedMcpTransport.Stdio(
            command = "/work/jira-mcp-server",
            args = listOf("--verbose", "say \"hi\""),
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
    inner class Rendering {

        @Test
        fun `should forward secrets by name and write plain values into their own tables`() {
            // when
            val content = format.merge(null, listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // then
            assertThat(content).isEqualTo(
                """
                [mcp_servers.atlassian]
                command = "/work/jira-mcp-server"
                args = ["--verbose", "say \"hi\""]
                env_vars = ["JIRA_PAT", "CONFLUENCE_PAT"]

                [mcp_servers.atlassian.env]
                JIRA_BASE_URL = "https://jira.example.com"

                [mcp_servers.github]
                url = "https://api.githubcopilot.com/mcp/"
                bearer_token_env_var = "GITHUB_TOKEN"

                [mcp_servers.github.http_headers]
                X-Region = "eu"

                [mcp_servers.github.env_http_headers]
                X-Api-Key = "API_KEY"

                """.trimIndent(),
            )
        }

        @Test
        fun `should forward the variables a server reads from the environment of the tool by name after its secrets, never with a value`() {
            // given
            // - Codex clears the environment of a server, so every such variable must be listed; one named twice is listed once
            val forwarding = stdioServer.copy(
                transport = (stdioServer.transport as ResolvedMcpTransport.Stdio).copy(forwarded = listOf("DBUS_SESSION_BUS_ADDRESS", "XDG_RUNTIME_DIR", "JIRA_PAT")),
            )

            // when
            val content = format.merge(null, listOf(forwarding), setOf("atlassian"), file)

            // then
            assertThat(content).contains("env_vars = [\"JIRA_PAT\", \"CONFLUENCE_PAT\", \"DBUS_SESSION_BUS_ADDRESS\", \"XDG_RUNTIME_DIR\"]\n")
            assertThat(Toml.parse(content).getTable("mcp_servers.atlassian.env")?.keySet()).containsExactly("JIRA_BASE_URL")
        }

        @Test
        fun `should forward the variables a server reads from the environment of the tool even when it has no secret`() {
            // given
            val forwarding =
                ResolvedMcpServer("bare", ResolvedMcpTransport.Stdio(command = "server", args = emptyList(), env = emptyMap(), forwarded = listOf("XDG_RUNTIME_DIR")))

            // when
            val content = format.merge(null, listOf(forwarding), setOf("bare"), file)

            // then
            assertThat(content).isEqualTo("[mcp_servers.bare]\ncommand = \"server\"\nenv_vars = [\"XDG_RUNTIME_DIR\"]\n")
        }

        @Test
        fun `should write the allowed and denied tools of a server as its enabled and disabled tools, before its sub-tables`() {
            // given
            val restricted = httpServer.copy(tools = McpToolRestriction(allow = listOf("get_me", "search_code"), deny = listOf("delete_repository")))

            // when
            val content = format.merge(null, listOf(restricted), setOf("github"), file)

            // then
            assertThat(content).startsWith(
                """
                [mcp_servers.github]
                url = "https://api.githubcopilot.com/mcp/"
                bearer_token_env_var = "GITHUB_TOKEN"
                enabled_tools = ["get_me", "search_code"]
                disabled_tools = ["delete_repository"]

                [mcp_servers.github.http_headers]
                """.trimIndent(),
            )
            val table = Toml.parse(content).getTable("mcp_servers.github")!!
            assertThat(table.getArray("enabled_tools")!!.toList()).containsExactly("get_me", "search_code")
            assertThat(table.getArray("disabled_tools")!!.toList()).containsExactly("delete_repository")
        }

        @Test
        fun `should write neither list for a server without restrictions`() {
            // when
            val content = format.merge(null, listOf(stdioServer), setOf("atlassian"), file)

            // then
            assertThat(content).doesNotContain("enabled_tools").doesNotContain("disabled_tools")
        }

        @Test
        fun `should quote an id that is not a bare key`() {
            // given
            val dotted =
                ResolvedMcpServer("xbid.bobcat", ResolvedMcpTransport.Stdio(command = "server", args = emptyList(), env = emptyMap()))

            // when
            val content = format.merge(null, listOf(dotted), setOf("xbid.bobcat"), file)

            // then
            assertThat(content).isEqualTo("[mcp_servers.\"xbid.bobcat\"]\ncommand = \"server\"\n")
        }
    }

    @Nested
    inner class EntryOwnership {

        @Test
        fun `should append its sections after foreign content and keep every foreign byte`() {
            // given
            val existing =
                """
                # Codex settings of this project
                model = "o3"   # the model

                [mcp_servers.playwright]
                command = "npx"
                args = ["-y", "@playwright/mcp"]
                """.trimIndent() + "\n"

            // when
            val content = format.merge(existing, listOf(stdioServer), setOf("atlassian"), file)

            // then
            assertThat(content).startsWith(existing + "\n[mcp_servers.atlassian]\n")
        }

        @Test
        fun `should replace owned sections in place and keep the comments and formatting around them`() {
            // given
            // - an owned server with a sub-table between foreign content, with a comment that belongs to the section after it
            val existing =
                """
                model = "o3"

                [mcp_servers.atlassian]
                command = "old"   # stale

                [mcp_servers.atlassian.env]
                OLD = "1"

                # The browser server, configured by hand.
                [mcp_servers.playwright]
                command   =   "npx"
                [profiles.fast]
                model = "o4-mini"
                """.trimIndent() + "\n"

            // when
            val content = format.merge(existing, listOf(stdioServer), setOf("atlassian"), file)

            // then
            assertThat(content).isEqualTo(
                """
                model = "o3"

                [mcp_servers.atlassian]
                command = "/work/jira-mcp-server"
                args = ["--verbose", "say \"hi\""]
                env_vars = ["JIRA_PAT", "CONFLUENCE_PAT"]

                [mcp_servers.atlassian.env]
                JIRA_BASE_URL = "https://jira.example.com"

                # The browser server, configured by hand.
                [mcp_servers.playwright]
                command   =   "npx"
                [profiles.fast]
                model = "o4-mini"

                """.trimIndent(),
            )
        }

        @Test
        fun `should remove the sections of an owned server the deployment no longer selects`() {
            // given
            val existing =
                """
                [mcp_servers.playwright]
                command = "npx"

                [mcp_servers."github"]
                url = "https://old"

                [mcp_servers.github.env_http_headers]
                X = "Y"
                """.trimIndent() + "\n"

            // when
            val content = format.merge(existing, emptyList(), setOf("github"), file)

            // then
            assertThat(content).isEqualTo("[mcp_servers.playwright]\ncommand = \"npx\"\n")
        }

        @Test
        fun `should not take a header-like line inside a multi-line string for a section`() {
            // given
            val existing = "instructions = \"\"\"\n[mcp_servers.atlassian]\nkeep me\n\"\"\"\n"

            // when
            val content = format.merge(existing, emptyList(), setOf("atlassian"), file)

            // then
            assertThat(content).isEqualTo(existing)
        }

        @Test
        fun `should leave a server whose id is not owned untouched even when it shares a prefix`() {
            // given
            val existing = "[mcp_servers.atlassian-legacy]\ncommand = \"old\"\n"

            // when
            val content = format.merge(existing, emptyList(), setOf("atlassian"), file)

            // then
            assertThat(content).isEqualTo(existing)
        }

        @Test
        fun `should produce the same file when merged twice`() {
            // given
            val first = format.merge("# mine\nmodel = \"o3\"\n", listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // when
            val second = format.merge(first, listOf(stdioServer, httpServer), setOf("atlassian", "github"), file)

            // then
            assertThat(second).isEqualTo(first)
        }
    }

    /**
     * Inputs that are valid TOML and that a line-based splicer reads wrongly: each must either be merged with every foreign table kept, or be refused naming the file.
     */
    @Nested
    inner class UntrustedContent {

        @Test
        fun `should keep the tables after an owned server whose argument holds a multi-line string delimiter`() {
            // given
            val existing = "[mcp_servers.atlassian]\ncommand = \"old\"\n\n[user_table]\nimportant = true\n"
            val delimiterArgument = stdioServer.copy(transport = ResolvedMcpTransport.Stdio(command = "server", args = listOf("--motd='''hello", "say \"\"\"x"), env = emptyMap()))
            val first = format.merge(existing, listOf(delimiterArgument), setOf("atlassian"), file)

            // when
            val second = format.merge(first, listOf(delimiterArgument), setOf("atlassian"), file)

            // then
            assertThat(second).isEqualTo(first).contains("[user_table]\nimportant = true")
        }

        @Test
        fun `should recognize an owned server after delimiters inside a single-line string and a comment`() {
            // given
            val foreign = "[profiles.a]\nnote = \"it'''s\" # and \"\"\" here\nother = 'x\"\"\"y'\n"
            val existing = "$foreign\n[mcp_servers.github]\nurl = \"https://old\"\n"

            // when
            val content = format.merge(existing, listOf(httpServer), setOf("github"), file)

            // then
            assertThat(Regex("""\[mcp_servers\.github]""").findAll(content).count()).isEqualTo(1)
            assertThat(content).startsWith(foreign).doesNotContain("https://old")
        }

        @Test
        fun `should keep a foreign table whose quoted key holds brackets`() {
            // given
            val existing = "[mcp_servers.github]\nurl = \"https://old\"\n\n[profiles.\"team[ci]\"]\napproval_policy = \"untrusted\"\n\n[profiles.safe]\nx = 1\n"

            // when
            val content = format.merge(existing, listOf(httpServer), setOf("github"), file)

            // then
            assertThat(content).contains("[profiles.\"team[ci]\"]\napproval_policy = \"untrusted\"\n\n[profiles.safe]\nx = 1")
        }

        @Test
        fun `should recognize its own table of an id holding a bracket on the next merge`() {
            // given
            val bracketed =
                ResolvedMcpServer("a]b", ResolvedMcpTransport.Stdio(command = "server", args = emptyList(), env = emptyMap()))
            val first = format.merge("model = \"o3\"\n", listOf(bracketed), setOf("a]b"), file)

            // when
            val second = format.merge(first, listOf(bracketed), setOf("a]b"), file)

            // then
            assertThat(second).isEqualTo(first)
            assertThat(first).contains("[mcp_servers.\"a]b\"]")
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            quoteCharacter = '~',
            value = [
                // - an inline table under [mcp_servers]
                "[mcp_servers]{n}github = { command = \"mine\" }{n}",
                // - dotted keys at the root
                "mcp_servers.github.command = \"mine\"{n}",
                // - dotted keys under [mcp_servers]
                "[mcp_servers]{n}github.command = \"mine\"{n}",
                // - an array of tables
                "[[mcp_servers.github]]{n}command = \"mine\"{n}",
            ],
        )
        fun `should refuse an owned server defined other than as its own table instead of defining it twice`(
            existing: String,
        ) {
            // when / then
            assertThatThrownBy { format.merge(existing.replace("{n}", "\n"), listOf(httpServer), setOf("github"), file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining("'github'")
        }

        @Test
        fun `should refuse a file that is not valid TOML without echoing any of its content`() {
            // given
            val existing = "token = \"sk-FOREIGN-CODEX\"\n= broken\n"

            // when / then
            assertThatThrownBy { format.merge(existing, listOf(httpServer), setOf("github"), file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining("line 2")
                .hasMessageNotContaining("sk-FOREIGN-CODEX")
        }

        @Test
        fun `should write its lines with the line ending of a CRLF file`() {
            // given
            val existing = "# mine\r\nmodel = \"o3\"\r\n"

            // when
            val content = format.merge(existing, listOf(stdioServer), setOf("atlassian"), file)

            // then
            assertThat(content).startsWith(existing).endsWith("\r\n")
            assertThat(content.replace("\r\n", "")).doesNotContain("\n")
        }

        @Test
        fun `should keep a file without a final newline without one`() {
            // given
            val existing = "model = \"o3\""

            // when
            val content = format.merge(existing, listOf(stdioServer), setOf("atlassian"), file)

            // then
            assertThat(content).startsWith("model = \"o3\"\n\n[mcp_servers.atlassian]").doesNotEndWith("\n")
        }

        @Test
        fun `should name every entry an existing file holds`() {
            // given
            val existing = "[mcp_servers.github]\nurl = \"x\"\n[mcp_servers.playwright]\ncommand = \"npx\"\n"

            // when
            val entries = format.entryFingerprints(existing, file).keys

            // then
            assertThat(entries).containsExactlyInAnyOrder("github", "playwright")
        }

        @Test
        fun `should give an entry the same fingerprint however its table is written, since only its parsed content counts`() {
            // given
            val table = "[mcp_servers.a]\ncommand = \"x\"\nargs = [\"1\", \"2\"]\n\n[mcp_servers.a.env]\nA = \"1\"\n"
            val rearranged = "# mine\nmodel = \"o3\"\n\n[mcp_servers.a]\nargs = [ \"1\",\n  \"2\" ]  # tail\ncommand = 'x'\nenv = { A = \"1\" }\n"

            // when
            val first = format.entryFingerprints(table, file)
            val second = format.entryFingerprints(rearranged, file)

            // then
            assertThat(second).isEqualTo(first)
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                // - another value
                "[mcp_servers.a]\\ncommand = \"y\"\\nargs = [\"1\", \"2\"]\\n",
                // - the elements of an array in another order
                "[mcp_servers.a]\\ncommand = \"x\"\\nargs = [\"2\", \"1\"]\\n",
                // - one more key
                "[mcp_servers.a]\\ncommand = \"x\"\\nargs = [\"1\", \"2\"]\\nenabled_tools = [\"get_me\"]\\n",
                // - a number where a string was
                "[mcp_servers.a]\\ncommand = \"x\"\\nargs = [1, 2]\\n",
            ],
        )
        fun `should give an entry another fingerprint once its content changes`(changed: String) {
            // given
            val original = "[mcp_servers.a]\ncommand = \"x\"\nargs = [\"1\", \"2\"]\n"

            // when
            val before = format.entryFingerprints(original, file).getValue("a")
            val after = format.entryFingerprints(changed.replace("\\n", "\n"), file).getValue("a")

            // then
            assertThat(after).isNotEqualTo(before)
        }
    }

    /**
     * An edit keeps every foreign byte, writes its own lines with the line ending most lines of the file use, keeps a missing final newline missing, and always leaves valid TOML, whatever mix of LF and CRLF the file uses.
     */
    @Nested
    inner class LineEndings {

        @ParameterizedTest
        @CsvSource(
            "lf, true",
            "lf, false",
            "crlf, true",
            "crlf, false",
            "mixed-lf, true",
            "mixed-lf, false",
            "mixed-crlf, true",
            "mixed-crlf, false",
        )
        fun `should append the tables of a first deploy as valid TOML in the line ending most lines use`(
            style: String,
            finalNewline: Boolean,
        ) {
            // given
            val existing = withEndings(listOf("# mine", "model = \"o3\"", "", "[other]", "x = 1"), style, finalNewline)

            // when
            val content = format.merge(existing, listOf(stdioServer), setOf("atlassian"), file)

            // then
            assertThat(content).startsWith(existing)
            assertThat(content.endsWith("\n")).isEqualTo(finalNewline)
            assertThat(Toml.parse(content).errors()).isEmpty()
            assertWrittenIn(dominantOf(style), content.substring(existing.length))
        }

        @ParameterizedTest
        @CsvSource(
            "lf, true",
            "lf, false",
            "crlf, true",
            "crlf, false",
            "mixed-lf, true",
            "mixed-lf, false",
            "mixed-crlf, true",
            "mixed-crlf, false",
        )
        fun `should replace the table of a redeploy in the middle as valid TOML in the line ending most lines use`(
            style: String,
            finalNewline: Boolean,
        ) {
            // given
            val existing = withEndings(
                listOf("# mine", "model = \"o3\"", "", "[mcp_servers.atlassian]", "command = \"old\"", "", "[other]", "x = 1"),
                style,
                finalNewline,
            )
            val head = existing.substring(0, existing.indexOf("[mcp_servers.atlassian]"))
            val tail = existing.substring(existing.indexOf("[other]"))

            // when
            val content = format.merge(existing, listOf(stdioServer), setOf("atlassian"), file)

            // then
            assertThat(content)
                .startsWith(head)
                .endsWith(tail)
                .contains("command = \"/work/jira-mcp-server\"")
                .doesNotContain("\"old\"")
            assertThat(Toml.parse(content).errors()).isEmpty()
            assertWrittenIn(dominantOf(style), content.substring(head.length, content.length - tail.length))
        }

        @Test
        fun `should edit a file with mixed line endings and no final newline`() {
            // given
            // - the layout a review found refused: most lines end in LF, one in CRLF, and the last has no line ending
            val existing = "# my settings\nmodel = \"o3\"\r\n\n[mcp_servers.mine]\ncommand = \"x\""

            // when
            val content = format.merge(existing, listOf(stdioServer), setOf("atlassian"), file)

            // then
            assertThat(content).startsWith(existing).doesNotEndWith("\n")
            assertThat(Toml.parse(content).errors()).isEmpty()
            assertWrittenIn("\n", content.substring(existing.length))
        }

        /**
         * Joins [lines] with the endings of [style]: all LF, all CRLF, or mostly one with a single line of the other; the last line gets none unless [finalNewline].
         */
        private fun withEndings(lines: List<String>, style: String, finalNewline: Boolean): String {
            val endings = lines.indices.map { index ->
                when (style) {
                    "lf" -> "\n"
                    "crlf" -> "\r\n"
                    "mixed-lf" -> if (index == 1) "\r\n" else "\n"
                    else -> if (index == 1) "\n" else "\r\n"
                }
            }
            return lines.zip(endings).joinToString("") { (line, ending) -> line + ending }.let { if (finalNewline) it else it.removeSuffix(endings.last()) }
        }

        private fun dominantOf(style: String) = if (style == "crlf" || style == "mixed-crlf") "\r\n" else "\n"

        private fun assertWrittenIn(ending: String, written: String) {
            if (ending == "\r\n") {
                assertThat(written.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r")
            } else {
                assertThat(written).doesNotContain("\r")
            }
        }
    }
}
