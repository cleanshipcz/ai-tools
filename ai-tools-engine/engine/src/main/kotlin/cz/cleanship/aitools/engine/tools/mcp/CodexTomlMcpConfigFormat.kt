package cz.cleanship.aitools.engine.tools.mcp

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.io.File

/**
 * `.codex/config.toml` of Codex, holding one `[mcp_servers.<id>]` table per server and its `[mcp_servers.<id>.*]` sub-tables.
 *
 * The file is edited as text rather than parsed and written again, because no TOML writer keeps comments: the tables of owned servers are cut out and written again, and every other line of the file is kept byte for byte, its line ending and a missing final newline included. A comment directly above the next table is kept with that table. Before and after the edit, the file is parsed with tomlj, a conformant TOML 1.0 parser: the edit is refused unless the existing file is valid TOML, every owned server in it is defined as a table of its own, and the edited file is valid TOML whose foreign content is unchanged and whose owned servers are exactly the ones written.
 *
 * Codex expands no `${NAME}` in this file, so a secret is written as the name of the environment variable Codex forwards: `env_vars` for a stdio server, `bearer_token_env_var` for a bearer token, and `env_http_headers` for any other header.
 */
object CodexTomlMcpConfigFormat : McpConfigFormat {

    override fun merge(existing: String?, servers: List<ResolvedMcpServer>, ownedMcpIds: Set<String>, file: File): String {
        val owned = ownedMcpIds + servers.map { it.id }
        val text = existing.orEmpty()
        val before = parse(text, file, "is not valid TOML")
        val sections = sectionsOf(text, owned, file)
        requireOwnTables(before, sections, owned, file)
        val newline = if ("\r\n" in text) "\r" else ""
        val block = servers.flatMapIndexed { index, server -> (if (index > 0) listOf("") else emptyList()) + render(server) }.map { it + newline }
        val out = Output(newline)
        var inserted = false
        for (section in sections) {
            if (!section.owned) {
                section.lines.forEach(out::line)
                continue
            }
            // The new tables take the place of the first owned one, so a deploy moves nothing a reader has arranged around them.
            if (!inserted) {
                out.block(block)
                inserted = true
            }
            section.keptTail().forEach(out::line)
        }
        if (!inserted) out.block(block)
        if (sections.lastOrNull()?.owned == true) out.trimTrailingBlankLines()
        val merged = out.text(finalNewline = text.isEmpty() || text.endsWith("\n"))
        // Verified as the bytes that will be written, so text UTF-8 cannot encode never changes on disk after passing the check.
        verify(before, String(merged.toByteArray(Charsets.UTF_8), Charsets.UTF_8), servers, owned, file)
        return merged
    }

    override fun ownedEntriesIn(existing: String, ownedMcpIds: Set<String>, file: File): Set<String> =
        serversOf(parse(existing, file, "is not valid TOML")).keys.filterTo(mutableSetOf()) { it in ownedMcpIds }

    private fun render(server: ResolvedMcpServer): List<String> {
        val table = "$SERVERS_TABLE.${key(server.id)}"
        return when (val transport = server.transport) {
            is ResolvedMcpTransport.Stdio -> {
                val secrets = transport.env.values
                    .filterIsInstance<McpValue.Secret>()
                    .map { it.variable }
                val plain = transport.env.mapNotNull { (key, value) -> (value as? McpValue.Plain)?.let { key to it.value } }
                buildList {
                    add("[$table]")
                    add("command = ${string(transport.command)}")
                    if (transport.args.isNotEmpty()) add("args = ${array(transport.args)}")
                    if (secrets.isNotEmpty()) add("env_vars = ${array(secrets)}")
                    addAll(subTable("$table.env", plain))
                }
            }
            is ResolvedMcpTransport.Http -> {
                val bearer = transport.headers.values
                    .filterIsInstance<McpValue.BearerSecret>()
                    .firstOrNull()
                val plain = transport.headers.mapNotNull { (key, value) -> (value as? McpValue.Plain)?.let { key to it.value } }
                val secret = transport.headers.mapNotNull { (key, value) -> (value as? McpValue.Secret)?.let { key to it.variable } }
                buildList {
                    add("[$table]")
                    add("url = ${string(transport.url)}")
                    bearer?.let { add("bearer_token_env_var = ${string(it.variable)}") }
                    addAll(subTable("$table.http_headers", plain))
                    addAll(subTable("$table.env_http_headers", secret))
                }
            }
        }
    }

    /**
     * Fails when an owned server of [before] is defined other than through `[mcp_servers.<id>]` tables, such as an inline table, dotted keys or an array of tables, which the engine would otherwise define a second time.
     */
    private fun requireOwnTables(before: TomlParseResult, sections: List<Section>, owned: Set<String>, file: File) {
        val tabled = sections.filter { it.owned }.mapNotNullTo(mutableSetOf()) { it.serverId }
        val otherwise = serversOf(before).keys.firstOrNull { it in owned && it !in tabled }
            ?: sections.firstOrNull { it.arrayOfTables && it.serverId in owned }?.serverId
            ?: return
        throw McpConfigFileException(
            "'${file.absolutePath}' defines the MCP server '$otherwise', which a manifest of this run owns, other than as a table '[$SERVERS_TABLE.${key(otherwise)}]', so the engine leaves the file untouched rather than define it twice. " +
                "Rewrite that definition as such a table, or remove it.",
        )
    }

    /**
     * Fails unless [merged] is valid TOML whose content outside the owned servers equals that of [before], and whose owned servers are exactly [servers] as rendered.
     */
    private fun verify(
        before: TomlParseResult,
        merged: String,
        servers: List<ResolvedMcpServer>,
        owned: Set<String>,
        file: File,
    ) {
        val after = parse(merged, file, "would not be valid TOML after the edit")
        val expected = servers.associate { server ->
            server.id to plain(serversOf(Toml.parse(render(server).joinToString("\n"))).getValue(server.id))
        }
        val actual = serversOf(after).filterKeys { it in owned }.mapValues { (_, value) -> plain(value) }
        if (withoutOwned(before, owned) != withoutOwned(after, owned) || actual != expected) {
            throw McpConfigFileException(
                "'${file.absolutePath}' would lose or change content the engine does not own if it were edited, so the engine leaves it untouched. Report the file layout that caused it.",
            )
        }
    }

    private fun parse(text: String, file: File, problem: String): TomlParseResult {
        val result = Toml.parse(text)
        val error = result.errors().firstOrNull() ?: return result
        // The message of a parse error may quote the text around it, which may hold a token; only its position is repeated.
        throw McpConfigFileException("'${file.absolutePath}' $problem at line ${error.position().line()}, column ${error.position().column()}, so the engine leaves it untouched. Fix the file or remove it, and deploy again.")
    }

    /**
     * Splits [text] into the part before the first table and one section per table header, marking the tables of the servers in [owned].
     */
    private fun sectionsOf(text: String, owned: Set<String>, file: File): List<Section> {
        if (text.isEmpty()) return emptyList()
        val headers = TomlHeaderScanner(text, file).headers()
        val sections = mutableListOf(Section(serverId = null, owned = false, arrayOfTables = false))
        text.removeSuffix("\n").split("\n").forEachIndexed { index, line ->
            headers[index]?.let { header ->
                val serverId = header.path.getOrNull(1)?.takeIf { header.path.first() == SERVERS_TABLE }
                sections += Section(serverId, owned = serverId != null && serverId in owned && !header.arrayOfTables, arrayOfTables = header.arrayOfTables)
            }
            sections.last().lines += line
        }
        return sections
    }

    /**
     * One table of the file with every line up to the next table header, or the lines before the first header when [serverId] is `null` and [owned] is false.
     */
    private class Section(val serverId: String?, val owned: Boolean, val arrayOfTables: Boolean) {
        val lines = mutableListOf<String>()

        /**
         * Returns the comment lines at the end of this section, which describe the table below them rather than this one, from the first comment on; empty when the end holds no comment.
         */
        fun keptTail(): List<String> {
            val tail = lines.drop(1).takeLastWhile { it.isBlank() || it.trimStart().startsWith("#") }
            return tail.dropWhile { it.isBlank() }
        }
    }

    /**
     * Collects the lines of the merged file, keeping one blank line between the written tables and what follows them.
     *
     * @param carriageReturn `"\r"` when the file ends its lines with CRLF, so every line the engine adds ends the same way
     */
    private class Output(private val carriageReturn: String) {
        private val lines = mutableListOf<String>()
        private var separate = false

        fun line(line: String) {
            if (separate && line.isNotBlank()) lines += carriageReturn
            separate = false
            lines += line
        }

        fun block(block: List<String>) {
            if (block.isEmpty()) return
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += carriageReturn
            lines += block
            separate = true
        }

        fun trimTrailingBlankLines() {
            while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
        }

        fun text(finalNewline: Boolean): String = if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = if (finalNewline) "\n" else "")
    }
}

private const val SERVERS_TABLE = "mcp_servers"
private val BARE_KEY = Regex("[A-Za-z0-9_-]+")

/**
 * Returns every server entry of the `mcp_servers` table of [table], by name; empty when it has none or it is not a table.
 */
private fun serversOf(table: TomlTable): Map<String, Any?> =
    (table.get(listOf(SERVERS_TABLE)) as? TomlTable)?.let { servers -> servers.keySet().associateWith { servers.get(listOf(it)) } }.orEmpty()

private fun withoutOwned(table: TomlTable, owned: Set<String>): Map<String, Any?> {
    val content = plain(table).toMutableMap()
    val foreignServers = (content[SERVERS_TABLE] as? Map<*, *>)?.filterKeys { it !in owned }
    if (foreignServers.isNullOrEmpty()) content.remove(SERVERS_TABLE) else content[SERVERS_TABLE] = foreignServers
    return content
}

/**
 * Returns [value] with every TOML table and array turned into a plain map and list, which compare by content.
 */
private fun plain(value: Any?): Any? = when (value) {
    is TomlTable -> plain(value)
    is TomlArray -> value.toList().map { plain(it) }
    else -> value
}

private fun plain(table: TomlTable): Map<String, Any?> = table.keySet().associateWith { plain(table.get(listOf(it))) }

private fun subTable(table: String, entries: List<Pair<String, String>>): List<String> =
    if (entries.isEmpty()) emptyList() else listOf("", "[$table]") + entries.map { (key, value) -> "${key(key)} = ${string(value)}" }

/**
 * Returns [key] as a TOML key: bare when it can be, quoted otherwise.
 */
private fun key(key: String): String = if (BARE_KEY.matches(key)) key else string(key)

private fun array(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") { string(it) }

private fun string(value: String): String = buildString {
    append('"')
    for (char in value) {
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\n' -> append("\\n")
            char == '\t' -> append("\\t")
            char == '\r' -> append("\\r")
            char < ' ' || char == '\u007F' -> append("\\u%04X".format(char.code))
            else -> append(char)
        }
    }
    append('"')
}

/**
 * Finds the table headers of a TOML file that tomlj has already accepted, line by line, knowing where strings, comments and arrays are.
 *
 * A line is a header only when it starts, outside any string and any array or inline table, with `[`; quoted keys in it may hold brackets and dots.
 */
private class TomlHeaderScanner(private val text: String, private val file: File) {
    private var index = 0
    private var line = 0
    private var depth = 0
    private val headers = mutableMapOf<Int, TomlHeader>()

    /** Returns the header of every line that is one, by the index of that line. */
    fun headers(): Map<Int, TomlHeader> {
        while (index < text.length) {
            if (atLineStart() && depth == 0) readHeaderIfAny()
            if (index >= text.length) break
            when (val char = text[index]) {
                '\n' -> nextLine()
                '#' -> skipTo('\n')
                '"', '\'' -> skipString(char)
                '[', '{' -> depth++.also { index++ }
                ']', '}' -> depth--.also { index++ }
                else -> index++
            }
        }
        return headers
    }

    private fun atLineStart() = index == 0 || text[index - 1] == '\n'

    private fun nextLine() {
        line++
        index++
    }

    private fun skipTo(char: Char) {
        while (index < text.length && text[index] != char) index++
    }

    private fun skipString(quote: Char) {
        val triple = "$quote$quote$quote"
        if (text.startsWith(triple, index)) skipMultiLineString(quote, triple) else skipSingleLineString(quote)
    }

    private fun skipMultiLineString(quote: Char, triple: String) {
        index += triple.length
        while (index < text.length && !text.startsWith(triple, index)) {
            if (quote == '"' && text[index] == '\\') index++
            if (index < text.length && text[index] == '\n') line++
            index++
        }
        index += triple.length
        // A multi-line string may end in up to two more quotes of its own, which belong to its content.
        repeat(2) { if (index < text.length && text[index] == quote) index++ }
    }

    private fun skipSingleLineString(quote: Char) {
        index++
        while (index < text.length && text[index] != quote && text[index] != '\n') {
            if (quote == '"' && text[index] == '\\') index++
            index++
        }
        index++
    }

    private fun readHeaderIfAny() {
        var position = skipBlanks(index)
        if (position >= text.length || text[position] != '[') return
        val arrayOfTables = text.startsWith("[[", position)
        position += if (arrayOfTables) 2 else 1
        val path = mutableListOf<String>()
        while (true) {
            val (part, next) = readKey(skipBlanks(position))
            path += part
            position = skipBlanks(next)
            if (text[position] == '.') position++ else break
        }
        val closing = if (arrayOfTables) "]]" else "]"
        if (!text.startsWith(closing, position)) unreadable()
        headers[line] = TomlHeader(path, arrayOfTables)
        index = position + closing.length
    }

    private fun skipBlanks(start: Int): Int {
        var position = start
        while (position < text.length && (text[position] == ' ' || text[position] == '\t')) position++
        return position
    }

    private fun readKey(start: Int): Pair<String, Int> {
        val quote = text[start]
        if (quote == '"' || quote == '\'') {
            val key = StringBuilder()
            var position = start + 1
            while (text[position] != quote) {
                if (quote == '"' && text[position] == '\\') {
                    key.append(escape(position))
                    position += escapeLength(position)
                } else {
                    key.append(text[position++])
                }
            }
            return key.toString() to position + 1
        }
        var position = start
        while (position < text.length && text[position].isBareKeyCharacter()) position++
        if (position == start) unreadable()
        return text.substring(start, position) to position
    }

    private fun escape(position: Int): String = when (val code = text[position + 1]) {
        'u' ->
            text
                .substring(position + 2, position + 6)
                .toInt(HEX)
                .toChar()
                .toString()
        'U' -> String(Character.toChars(text.substring(position + 2, position + 10).toInt(HEX)))
        else -> ESCAPES[code]?.toString() ?: unreadable()
    }

    private fun escapeLength(position: Int): Int = when (text[position + 1]) {
        'u' -> 6
        'U' -> 10
        else -> 2
    }

    private fun Char.isBareKeyCharacter() = isLetterOrDigit() || this == '_' || this == '-'

    // tomlj accepted the file, so a header this scanner cannot read means the scanner is wrong; the edit is refused rather than guessed.
    private fun unreadable(): Nothing = throw McpConfigFileException("'${file.absolutePath}' has a table header at line ${line + 1} the engine cannot read, so it leaves the file untouched.")

    companion object {
        private const val HEX = 16
        private val ESCAPES =
            mapOf('b' to '\b', 't' to '\t', 'n' to '\n', 'f' to '\u000C', 'r' to '\r', '"' to '"', '\\' to '\\')
    }
}

/**
 * A table header: the parts of its dotted key, unquoted, and whether it opens an array of tables.
 */
private data class TomlHeader(val path: List<String>, val arrayOfTables: Boolean)
