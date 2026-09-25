package cz.cleanship.aitools.engine.tools.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.io.File
import java.util.stream.Stream
import kotlin.random.Random

/**
 * The ownership contract every [McpConfigFormat] keeps, checked on the parsed result rather than on the text of one format: foreign entries survive unchanged, an owned entry is replaced in its place, a deselected owned entry is removed, a second merge changes nothing, and no secret value is ever written.
 */
class McpConfigFormatContractTest {

    private val file = File("/project/mcp-config")

    private val foreign =
        ResolvedMcpServer("playwright", ResolvedMcpTransport.Stdio(command = "npx", args = listOf("-y", "@playwright/mcp"), env = emptyMap()))

    private val atlassian = ResolvedMcpServer(
        "atlassian",
        ResolvedMcpTransport.Stdio(
            command = "/work/jira-mcp-server",
            args = emptyList(),
            env = linkedMapOf("JIRA_PAT" to McpValue.Secret("JIRA_PAT", required = true), "JIRA_BASE_URL" to McpValue.Plain("https://jira.example.com")),
        ),
    )

    private val github = ResolvedMcpServer(
        "github",
        ResolvedMcpTransport.Http(url = "https://api.githubcopilot.com/mcp/", headers = linkedMapOf("Authorization" to McpValue.BearerSecret("GITHUB_TOKEN", required = true))),
    )

    private val owned = setOf("atlassian", "github", "retired")

    @ParameterizedTest(name = "{0}")
    @MethodSource("formats")
    fun `should keep foreign entries, replace owned ones in place, remove deselected ones and be idempotent`(
        name: String,
        format: McpConfigFormat,
    ) {
        // given
        // - a file holding a foreign server between two owned ones, one of which the deployment no longer selects
        val existing = format
            .merge(null, listOf(foreign), setOf("playwright"), file)
            .let { format.merge(it, listOf(github.copy(transport = ResolvedMcpTransport.Http("https://old", emptyMap()))), setOf("github"), file) }
            .let { format.merge(it, listOf(atlassian.copy(id = "retired")), setOf("retired"), file) }

        // when
        val merged = format.merge(existing, listOf(atlassian, github), owned, file)

        // then
        val entries = entriesOf(format, merged)
        assertThat(merged).describedAs("merged %s file", name).isNotBlank()
        // - the owned entries take the place of the owned ones they replace: one by one in JSON, as one block of tables where the first owned table stood in TOML
        assertThat(entries.keys.first()).isEqualTo("playwright")
        assertThat(entries.keys).containsExactlyInAnyOrder("playwright", "github", "atlassian")
        assertThat(entries.getValue("playwright")).isEqualTo(entriesOf(format, existing).getValue("playwright"))
        assertThat(entries.getValue("github").toString()).doesNotContain("https://old")
        assertThat(format.entryFingerprints(merged, file).keys.filter { it in owned }).containsExactlyInAnyOrder("github", "atlassian")
        assertThat(format.merge(merged, listOf(atlassian, github), owned, file)).isEqualTo(merged)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formats")
    fun `should write a secret only as a reference to its variable`(name: String, format: McpConfigFormat) {
        // when
        val merged = format.merge(null, listOf(atlassian, github), owned, file)

        // then
        // - the model carries no secret value at all, so a reference by name is the only thing that can be written
        assertThat(merged).describedAs("%s file", name).contains("JIRA_PAT").contains("GITHUB_TOKEN")
        assertThat(entriesOf(format, merged).getValue("atlassian").toString()).doesNotContain("s3cr3t")
    }

    /**
     * The fingerprint of an entry is what the MCP ledger records, and an entry the ledger records is removed only while its fingerprint still matches: it must depend on the entry alone, never on the file around it.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("formats")
    fun `should give an entry the same fingerprint in every file it is merged into, and another one once its content changes`(
        name: String,
        format: McpConfigFormat,
    ) {
        // given
        val alone = format.merge(null, listOf(atlassian, github), owned, file)
        val withForeign = format.merge(format.merge(null, listOf(foreign), setOf("playwright"), file), listOf(atlassian, github), owned, file)
        val changed = format.merge(alone, listOf(github.copy(transport = ResolvedMcpTransport.Http("https://other", emptyMap()))), setOf("github"), file)

        // when
        val fingerprintsAlone = format.entryFingerprints(alone, file)
        val fingerprintsWithForeign = format.entryFingerprints(withForeign, file)
        val fingerprintsChanged = format.entryFingerprints(changed, file)

        // then
        assertThat(fingerprintsAlone.keys).describedAs(name).containsExactlyInAnyOrder("atlassian", "github")
        assertThat(fingerprintsAlone.values).allSatisfy { assertThat(it).matches("sha256:[0-9a-f]{64}") }
        assertThat(fingerprintsWithForeign.filterKeys { it != "playwright" }).describedAs(name).isEqualTo(fingerprintsAlone)
        assertThat(fingerprintsChanged.getValue("atlassian")).describedAs(name).isEqualTo(fingerprintsAlone.getValue("atlassian"))
        assertThat(fingerprintsChanged.getValue("github")).describedAs(name).isNotEqualTo(fingerprintsAlone.getValue("github"))
        assertThat(fingerprintsAlone.getValue("atlassian")).describedAs(name).isNotEqualTo(fingerprintsAlone.getValue("github"))
    }

    @Test
    fun `should keep every foreign table of generated TOML files around owned servers`() {
        // given
        // - seeded, so the same files are generated on every run
        val random = Random(20260924)
        repeat(GENERATED_FILES) { index ->
            val existing = generatedToml(random)

            // when
            val merged = CodexTomlMcpConfigFormat.merge(existing, listOf(atlassian, github), owned, file)

            // then
            val before =
                plainWithoutOwned(Toml.parse(existing).also { assertThat(it.hasErrors()).describedAs("generated file %d", index).isFalse() })
            val after = Toml.parse(merged)
            assertThat(after.hasErrors()).describedAs("merged file %d", index).isFalse()
            assertThat(plainWithoutOwned(after)).describedAs("foreign content of file %d", index).isEqualTo(before)
            assertThat(CodexTomlMcpConfigFormat.merge(merged, listOf(atlassian, github), owned, file)).isEqualTo(merged)
        }
    }

    private fun generatedToml(random: Random): String = buildString {
        val tricky =
            listOf("it'''s", "a\"\"\"b", "[x]", "# not a comment", "team[ci]", "a]b", "tab\there", "\\\\path", "")

        fun basic(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\t", "\\t") + "\""

        fun value() = when (random.nextInt(4)) {
            0 -> basic(tricky.random(random))
            1 -> "'" + tricky.filter { '\'' !in it && '\t' !in it }.random(random) + "'"
            2 -> "[\n  " + basic(tricky.random(random)) + ",\n  [1, 2],\n]"
            else -> random.nextInt(1000).toString()
        }
        append("# generated\nmodel = ").append(basic(tricky.random(random))).append("\n")
        var tableIndex = 0
        repeat(2 + random.nextInt(5)) {
            append("\n")
            when (random.nextInt(4)) {
                0 -> listOf("atlassian", "github", "retired").random(random).let { id ->
                    // - an owned table appears at most once, as it does in a file Codex accepts
                    if (!contains("[mcp_servers.$id]\n")) append("[mcp_servers.").append(id).append("]\ncommand = \"old\"\n")
                }
                1 -> append("# comment with ''' and \"\"\" and [brackets]\n[profiles.\"p")
                    .append(tableIndex++)
                    .append("[")
                    .append(random.nextInt(9))
                    .append("]\"]\nnote = ")
                    .append(value())
                    .append(" # tail \"\"\"\n")
                2 -> append("[tables.t")
                    .append(tableIndex++)
                    .append("]\nlist = ")
                    .append(value())
                    .append("\nmultiline = '''\n[mcp_servers.github]\n'''\n")
                else -> append("[mcp_servers.foreign")
                    .append(tableIndex++)
                    .append("]\ncommand = ")
                    .append(value())
                    .append("\n")
            }
        }
    }

    private fun plainWithoutOwned(table: TomlTable): Map<String, Any?> {
        val plain = plain(table).toMutableMap()
        val servers = (plain["mcp_servers"] as? Map<*, *>)?.filterKeys { it !in owned }
        if (servers.isNullOrEmpty()) plain.remove("mcp_servers") else plain["mcp_servers"] = servers
        return plain
    }

    private fun plain(value: Any?): Any? = when (value) {
        is TomlTable -> plain(value)
        is TomlArray -> value.toList().map { plain(it) }
        else -> value
    }

    private fun plain(table: TomlTable): Map<String, Any?> = table.keySet().associateWith { plain(table.get(listOf(it))) }

    private fun entriesOf(format: McpConfigFormat, content: String): Map<String, Any?> = when (format) {
        is CodexTomlMcpConfigFormat -> (plain(Toml.parse(content))["mcp_servers"] as Map<*, *>).entries.associate { (key, value) -> key.toString() to value }
        else ->
            Json
                .parseToJsonElement(content)
                .jsonObject.values
                .filterIsInstance<JsonObject>()
                .single()
    }

    companion object {
        private const val GENERATED_FILES = 200

        @JvmStatic
        fun formats(): Stream<Arguments> = Stream.of(
            Arguments.of("Claude Code", JsonMcpConfigFormat.CLAUDE_CODE),
            Arguments.of("Claude Code user", JsonMcpConfigFormat.CLAUDE_CODE_USER),
            Arguments.of("VS Code", JsonMcpConfigFormat.VS_CODE),
            Arguments.of("Cursor", JsonMcpConfigFormat.CURSOR),
            Arguments.of("Codex", CodexTomlMcpConfigFormat),
        )
    }
}
