package cz.cleanship.aitools.engine.tools.mcp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * An MCP config file in JSON, holding one object of server entries under [serversKey].
 *
 * Nothing is written for the [ResolvedMcpTransport.Stdio.forwarded] names of a server.
 *
 * The file is parsed strictly, so a file holding comments, trailing commas or a duplicate key is refused rather than rewritten without them, and every result is parsed again and compared with the file before it is returned. A format that keeps the layout edits the server entries in place and keeps every byte outside the owned entries; any other format writes the file again as a whole, so every entry and every other key survives, in its order, while the layout of the file is normalized.
 */
class JsonMcpConfigFormat private constructor(
    private val serversKey: String,
    private val secretReference: (variable: String, required: Boolean) -> String,
    private val typesRemoteServers: Boolean,
    private val keepsLayout: Boolean,
    private val allowsAllTools: Boolean = false,
    private val writesEmptyArgs: Boolean = false,
) : McpConfigFormat {

    override fun merge(existing: String?, servers: List<ResolvedMcpServer>, ownedMcpIds: Set<String>, file: File): String {
        if (keepsLayout) return mergeInPlace(existing, servers, ownedMcpIds, file)
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

    override fun entryFingerprints(content: String, file: File): Map<String, String> =
        serversOf(parse(content, file), file).mapValues { (_, entry) -> entry.fingerprint() }

    /**
     * Returns [existing] with the owned entries under [serversKey] replaced, removed or added in place, and every other byte kept.
     */
    private fun mergeInPlace(
        existing: String?,
        servers: List<ResolvedMcpServer>,
        ownedMcpIds: Set<String>,
        file: File,
    ): String {
        val document = JsonDocument.parse(existing, file)
        // Checked before the edit, so a servers value of another kind is reported the way a rewriting format reports it.
        serversOf(document.content, file)
        val owned = ownedMcpIds + servers.map { it.id }
        return document.withMembers(listOf(serversKey), owned = { it in owned }, members = servers.associate { it.id to render(it) })
    }

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

    private fun parse(existing: String?, file: File): JsonObject =
        if (existing.isNullOrBlank()) JsonObject(emptyMap()) else JsonDocument.parseStrictly(existing, file)

    private fun serversOf(root: JsonObject, file: File): JsonObject = when (val servers = root[serversKey]) {
        null -> JsonObject(emptyMap())
        is JsonObject -> servers
        else -> throw McpConfigFileException("'${file.absolutePath}' holds a '$serversKey' that is not a JSON object, so the engine leaves it untouched. Fix the file or remove it, and deploy again.")
    }

    // The forwarded names of a stdio server are left out: Claude Code, VS Code, Cursor and Copilot CLI pass their own environment to it (Claude Code a reduced one when CLAUDE_CODE_MCP_ALLOWLIST_ENV is set), and none of them can forward a variable by name without holding a reference to it.
    private fun render(server: ResolvedMcpServer): JsonObject = when (val transport = server.transport) {
        is ResolvedMcpTransport.Stdio -> buildJsonObject {
            if (allowsAllTools) putJsonArray("tools") { add(JsonPrimitive(ALL_TOOLS)) }
            put("type", "stdio")
            put("command", transport.command)
            if (transport.args.isNotEmpty() || writesEmptyArgs) putJsonArray("args") { transport.args.forEach { add(JsonPrimitive(it)) } }
            if (transport.env.isNotEmpty()) putJsonObject("env") { transport.env.forEach { (key, value) -> put(key, text(value)) } }
        }
        is ResolvedMcpTransport.Http -> buildJsonObject {
            if (allowsAllTools) putJsonArray("tools") { add(JsonPrimitive(ALL_TOOLS)) }
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
            secretReference = ::shellReference,
            typesRemoteServers = true,
            keepsLayout = false,
        )

        /**
         * `~/.claude.json` of Claude Code, rendered like [CLAUDE_CODE] under its top-level `mcpServers`, and edited in place with every byte outside the owned entries kept - see [JsonDocument].
         */
        val CLAUDE_CODE_USER = JsonMcpConfigFormat(
            serversKey = "mcpServers",
            secretReference = ::shellReference,
            typesRemoteServers = true,
            // Claude Code keeps its session state in this file and rewrites it while it runs, so no byte of it may be normalized.
            keepsLayout = true,
        )

        /**
         * `.vscode/mcp.json` of GitHub Copilot in VS Code: servers under `servers`, `type` on every entry, a secret as `${env:NAME}`.
         */
        val VS_CODE = JsonMcpConfigFormat(
            serversKey = "servers",
            secretReference = { variable, _ -> "\${env:$variable}" },
            typesRemoteServers = true,
            keepsLayout = false,
        )

        /**
         * `.cursor/mcp.json` of Cursor: servers under `mcpServers`, `type` on a stdio entry only, since Cursor tells a remote server by its `url`, and a secret as `${env:NAME}`.
         */
        val CURSOR = JsonMcpConfigFormat(
            serversKey = "mcpServers",
            secretReference = { variable, _ -> "\${env:$variable}" },
            typesRemoteServers = false,
            keepsLayout = false,
        )

        /**
         * `.github/mcp.json` of a project and `~/.copilot/mcp-config.json` of GitHub Copilot CLI: servers under `mcpServers`, every entry allowing all tools of its server with `"tools": ["*"]`, `type` on every entry, `args` on every stdio entry, a secret as `${NAME}`, and an optional one as `${NAME:-}` so that an unset one is empty rather than the literal reference.
         */
        val COPILOT_CLI = JsonMcpConfigFormat(
            serversKey = "mcpServers",
            secretReference = ::shellReference,
            typesRemoteServers = true,
            keepsLayout = false,
            // `copilot mcp add` and `copilot mcp remove` of Copilot CLI 1.0.89 write the user file back with every entry normalized: `"tools": ["*"]` added as the first key, keys in the order `tools, type, command, args, env` (`tools, type, url, headers`), an empty `args` added to a stdio entry without one, and unknown fields dropped.
            // Always writing `tools` and `args` keeps the content of an engine entry, and so its fingerprint in the ledger, through such a rewrite; the fingerprint ignores key order, which render follows only so that the rewrite leaves the bytes of the entry as they were.
            allowsAllTools = true,
            writesEmptyArgs = true,
        )

        /** The value of `tools` that allows every tool of a server. */
        private const val ALL_TOOLS = "*"

        @OptIn(ExperimentalSerializationApi::class)
        private val PRETTY = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }
}

/**
 * Returns the reference to the variable [variable] that a shell and Claude Code and Copilot CLI expand: `${NAME}`, or `${NAME:-}` when it is not [required], so that an unset one is empty rather than the literal reference.
 */
private fun shellReference(variable: String, required: Boolean): String =
    if (required) "\${$variable}" else "\${$variable:-}"
