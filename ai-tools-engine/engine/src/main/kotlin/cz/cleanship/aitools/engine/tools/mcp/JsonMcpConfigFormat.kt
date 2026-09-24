package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.services.MessageWithheldException
import cz.cleanship.aitools.engine.services.jsonOffset
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * An MCP config file in JSON, holding one object of server entries under [serversKey].
 *
 * The file is parsed strictly and written again as a whole, so every entry and every other key survives, in its order, while the layout of the file is normalized. A file holding comments, trailing commas or a duplicate key is refused rather than rewritten without them, and every result is parsed again and compared with the file before it is returned.
 */
class JsonMcpConfigFormat private constructor(
    private val serversKey: String,
    private val secretReference: (variable: String, required: Boolean) -> String,
    private val typesRemoteServers: Boolean,
) : McpConfigFormat {

    override fun merge(existing: String?, servers: List<ResolvedMcpServer>, ownedMcpIds: Set<String>, file: File): String {
        val root = parse(existing, file)
        val owned = ownedMcpIds + servers.map { it.id }
        val rendered = servers.associate { it.id to render(it) }
        val merged = LinkedHashMap<String, JsonElement>()
        // An owned entry keeps its place in the file, so a deploy moves nothing a reader has arranged.
        serversOf(root, file).forEach { (name, entry) ->
            when {
                name !in owned -> merged[name] = entry
                name in rendered -> merged[name] = rendered.getValue(name)
            }
        }
        rendered.forEach { (name, entry) -> merged.putIfAbsent(name, entry) }
        val content = LinkedHashMap(root)
        content[serversKey] = JsonObject(merged)
        val result = PRETTY.encodeToString(JsonElement.serializer(), JsonObject(content)) + "\n"
        // Verified as the bytes that will be written: text UTF-8 cannot encode, such as a lone surrogate, would otherwise change on disk after passing the check.
        verify(root, String(result.toByteArray(Charsets.UTF_8), Charsets.UTF_8), rendered, owned, file)
        return result
    }

    override fun ownedEntriesIn(existing: String, ownedMcpIds: Set<String>, file: File): Set<String> =
        serversOf(parse(existing, file), file).keys.filterTo(mutableSetOf()) { it in ownedMcpIds }

    /**
     * Fails unless [result] parses to [before] with every owned entry replaced by [rendered] and every other entry and key unchanged.
     */
    private fun verify(
        before: JsonObject,
        result: String,
        rendered: Map<String, JsonObject>,
        owned: Set<String>,
        file: File,
    ) {
        val after = parse(result, file)
        val foreignBefore = serversOf(before, file).filterKeys { it !in owned }
        val foreignAfter = serversOf(after, file).filterKeys { it !in owned }
        val ownedAfter = serversOf(after, file).filterKeys { it in owned }
        if (before.filterKeys { it != serversKey } != after.filterKeys { it != serversKey } || foreignBefore != foreignAfter || ownedAfter != rendered) {
            throw McpConfigFileException(
                "'${file.absolutePath}' would lose or change content the engine does not own if it were rewritten, so the engine leaves it untouched. Report the file layout that caused it.",
            )
        }
    }

    private fun parse(existing: String?, file: File): JsonObject {
        if (existing.isNullOrBlank()) return JsonObject(emptyMap())
        val element = try {
            STRICT.parseToJsonElement(existing)
        } catch (ex: SerializationException) {
            // The message of the parser quotes the file around the error, which may hold a token, so only the offset it names is repeated.
            throw McpConfigFileException(
                "'${file.absolutePath}' is not valid JSON${ex.jsonOffset()}, so the engine leaves it untouched rather than rewrite it. " +
                    "A comment or a trailing comma counts as invalid too, because rewriting the file would drop it. Fix the file or remove it, and deploy again.",
                MessageWithheldException(ex),
            )
        }
        val duplicate = duplicateKey(existing)
        val problem = when {
            duplicate != null -> "declares the key '$duplicate' twice in one object, so the engine leaves it untouched rather than rewrite it without one of them. Remove one, and deploy again."
            element !is JsonObject -> "is not a JSON object, so the engine leaves it untouched. Fix the file or remove it, and deploy again."
            else -> return element
        }
        throw McpConfigFileException("'${file.absolutePath}' $problem")
    }

    private fun serversOf(root: JsonObject, file: File): JsonObject = when (val servers = root[serversKey]) {
        null -> JsonObject(emptyMap())
        is JsonObject -> servers
        else -> throw McpConfigFileException("'${file.absolutePath}' holds a '$serversKey' that is not a JSON object, so the engine leaves it untouched. Fix the file or remove it, and deploy again.")
    }

    private fun render(server: ResolvedMcpServer): JsonObject = when (val transport = server.transport) {
        is ResolvedMcpTransport.Stdio -> buildJsonObject {
            put("type", "stdio")
            put("command", transport.command)
            if (transport.args.isNotEmpty()) putJsonArray("args") { transport.args.forEach { add(JsonPrimitive(it)) } }
            if (transport.env.isNotEmpty()) putJsonObject("env") { transport.env.forEach { (key, value) -> put(key, text(value)) } }
        }
        is ResolvedMcpTransport.Http -> buildJsonObject {
            if (typesRemoteServers) put("type", "http")
            put("url", transport.url)
            if (transport.headers.isNotEmpty()) putJsonObject("headers") { transport.headers.forEach { (key, value) -> put(key, text(value)) } }
        }
    }

    private fun text(value: McpValue): String = when (value) {
        is McpValue.Plain -> value.value
        is McpValue.Secret -> secretReference(value.variable, value.required)
        is McpValue.BearerSecret -> "Bearer ${secretReference(value.variable, value.required)}"
    }

    companion object {
        /**
         * `.mcp.json` of Claude Code: servers under `mcpServers`, `type` on every entry, a secret as `${NAME}`, and an optional one as `${NAME:-}` so that an unset one is empty rather than the literal reference.
         */
        val CLAUDE_CODE = JsonMcpConfigFormat(
            serversKey = "mcpServers",
            secretReference = { variable, required -> if (required) "\${$variable}" else "\${$variable:-}" },
            typesRemoteServers = true,
        )

        /**
         * `.vscode/mcp.json` of GitHub Copilot in VS Code: servers under `servers`, `type` on every entry, a secret as `${env:NAME}`.
         */
        val VS_CODE = JsonMcpConfigFormat(
            serversKey = "servers",
            secretReference = { variable, _ -> "\${env:$variable}" },
            typesRemoteServers = true,
        )

        /**
         * `.cursor/mcp.json` of Cursor: servers under `mcpServers`, `type` on a stdio entry only, since Cursor tells a remote server by its `url`, and a secret as `${env:NAME}`.
         */
        val CURSOR = JsonMcpConfigFormat(
            serversKey = "mcpServers",
            secretReference = { variable, _ -> "\${env:$variable}" },
            typesRemoteServers = false,
        )

        private val STRICT = Json

        @OptIn(ExperimentalSerializationApi::class)
        private val PRETTY = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }
}

/**
 * Returns the first key [json], which parses as JSON, declares twice in one object, or `null` when it declares none twice; the parser keeps only the last of them, silently.
 */
private fun duplicateKey(json: String): String? {
    // One set of keys per object being read, and null for an array, innermost last.
    val open = ArrayDeque<MutableSet<String>?>()
    var index = 0
    while (index < json.length) {
        when (json[index]) {
            '{' -> open.addLast(mutableSetOf())
            '[' -> open.addLast(null)
            '}', ']' -> open.removeLast()
            '"' -> {
                val end = stringEnd(json, index)
                if (isKey(json, end) && open.lastOrNull()?.add(keyText(json, index, end)) == false) return keyText(json, index, end)
                index = end
            }
        }
        index++
    }
    return null
}

/**
 * Returns whether the string closing at [end] is followed by a colon, which makes it a key.
 */
private fun isKey(json: String, end: Int): Boolean {
    var next = end + 1
    while (next < json.length && json[next].isWhitespace()) next++
    return next < json.length && json[next] == ':'
}

private fun keyText(json: String, start: Int, end: Int): String = Json.parseToJsonElement(json.substring(start, end + 1)).jsonPrimitive.content

/**
 * Returns the index of the quote closing the string that opens at [start].
 */
private fun stringEnd(json: String, start: Int): Int {
    var index = start + 1
    while (json[index] != '"') index += if (json[index] == '\\') 2 else 1
    return index
}
