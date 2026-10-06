package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.io.escapedForMessage
import cz.cleanship.aitools.engine.models.McpToolRestriction
import cz.cleanship.aitools.engine.services.ExportService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Writes the tools of MCP servers a deployment denies into the permissions file of one tool, obtained from [cz.cleanship.aitools.engine.tools.ToolAdapter.mcpPermissions] or [cz.cleanship.aitools.engine.tools.UserScopeExporter.mcpPermissions].
 *
 * It owns only the permission entries it writes and those [McpPermissionsContext.recorded] records; every other entry and every other part of [file] is left as it is.
 */
interface McpPermissionsExporter {

    /** The permissions file this exporter writes, such as `<project>/.claude/settings.json`. */
    val file: File

    /**
     * Returns the edit that writes the denied tools of [context] into [file] and removes every recorded entry [context] no longer denies, prepared from one read of [file]; nothing is written before [PreparedMcpEdit.commit].
     *
     * The allowed tools of [context] are never written. Which entries are owned is decided by [McpOwnership.ownedPermissionEntries]: an entry already in [file] that the engine did not record is never taken over or removed, even when [context] denies the same tool, and an entry recorded with other fingerprints only is left as it is and logged as a warning naming [file] and the entry. Returns `null` when there is nothing to write and [file] holds no recorded entry, so a deployment without restrictions does not create the file. A file left holding nothing but empty permission lists once its recorded entries are removed is deleted by the commit, unless [file] is a symbolic link, through which that emptied content is written instead.
     *
     * @throws McpConfigFileException naming [file], for every failure [McpConfigExporter.prepare] names, and if the permissions it holds have another shape than an object of arrays
     */
    fun prepare(context: McpPermissionsContext): PreparedMcpEdit?
}

/**
 * The tool restrictions one deployment writes into the permissions file of a tool.
 *
 * @property restrictions the denied tools of each server, by server id, in the order they are rendered; the allowed ones are ignored
 * @property recorded every fingerprint the [McpLedger] records for each entry the engine wrote into the file, by entry name, such as `deny:mcp__github__delete_repository`; such an entry is owned only while it holds one of them
 * @property presumedAbsent whether the file is taken to be missing without being read or reached, because the deploy deletes the directory holding it before it writes the file; a dry run, which deletes nothing, then plans what the deploy does
 */
data class McpPermissionsContext(
    val restrictions: Map<String, McpToolRestriction>,
    val recorded: Map<String, Set<String>> = emptyMap(),
    val presumedAbsent: Boolean = false,
)

/**
 * The [McpPermissionsExporter] of Claude Code: `permissions.deny` of a `settings.json`, whose entries `mcp__<id>__<tool>` block a call of that tool in every scope.
 *
 * The file is edited in place, keeping every byte outside the owned entries, and `permissions.allow` is never touched. An entry is named in the [McpLedger] by its list and its exact text, such as `deny:mcp__github__delete_repository`. A missing file is created holding only the permissions.
 *
 * @param exportService writes the file, so a dry run only logs it
 * @param root the directory [file] belongs to and how far a symbolic link at [file] or above it may lead - see [TargetRoot]
 */
class ClaudeSettingsPermissionsExporter(
    override val file: File,
    private val exportService: ExportService,
    root: TargetRoot,
) : McpPermissionsExporter {

    private val managed = ManagedConfigFile(file, root)

    override fun prepare(context: McpPermissionsContext): PreparedMcpEdit? {
        // A file the deploy deletes before writing it is neither read nor reached: in a dry run it still stands, possibly behind a link the deploy removes with it.
        val target = if (context.presumedAbsent) file else managed.target()
        val existing = if (context.presumedAbsent) null else managed.read(target)
        val document = JsonDocument.parse(existing, file)
        val present = deniedIn(document)
        val presentFingerprints = present.associate { entryName(it) to entryFingerprint(it) }
        val ownedNames = McpOwnership.ownedPermissionEntries(file, presentFingerprints, context.recorded)
        val owned = present.filterTo(mutableSetOf()) { entryName(it) in ownedNames }
        // A deny rule the user wrote is a security control of theirs: an entry the engine would write that the file already holds by hand stays theirs, and is neither added twice nor ever removed.
        val written = context.restrictions
            .flatMap { (id, restriction) -> restriction.deny.map { "$PREFIX$id$SEPARATOR$it" } }
            .distinct()
            .filter { it in owned || it !in present }
        val removed = owned - written.toSet()
        if (written.isEmpty() && owned.isEmpty()) return null
        val mine = owned + written
        val content = document.withElements(listOf(PERMISSIONS, DENY), owned = { (it as? JsonPrimitive)?.takeIf { entry -> entry.isString }?.content in mine }, elements = written.map(::JsonPrimitive))
        val removing = "removing ${removed.sorted().map { it.escapedForMessage() }}"
        if (existing != null && owned.isNotEmpty() && isDeletable(target, content)) {
            return PreparedMcpEdit(file, entries = emptyMap()) {
                managed.requireUnchanged(target, existing)
                managed.delete(exportService, target, describedBy = "the MCP tool permissions file emptied by $removing", previous = existing)
            }
        }
        val servers = "MCP tool permissions ${context.restrictions.filterValues { it.deny.isNotEmpty() }.keys.toList()}"
        return PreparedMcpEdit(file, entries = written.associate { entryName(it) to entryFingerprint(it) }) {
            if (!context.presumedAbsent) managed.requireUnchanged(target, existing)
            managed.write(exportService, target, content, describedBy = if (removed.isEmpty()) servers else "$servers, $removing", previous = existing)
        }
    }

    // A link at the file, as a dotfile setup keeps one, would lead the deletion to the file in the repository it links into and leave the link leading nowhere; the emptied content is written through the link instead.
    private fun isDeletable(
        target: File,
        content: String,
    ): Boolean = target == file && holdsNothingButEmptyPermissions(content)

    // A file holding only empty permission lists configures nothing, and is the file the engine creates once its entries are gone: deleting it gives a project back the state it had before its first restriction, where keeping it would leave a committed file nobody wrote. Any other key, or a list of another shape, makes the file the user's, and it stays.
    private fun holdsNothingButEmptyPermissions(content: String): Boolean {
        val parsed = JsonDocument.parseStrictly(content, file)
        val permissions = parsed[PERMISSIONS] as? JsonObject ?: return false
        return parsed.keys == setOf(PERMISSIONS) && permissions.keys.all { it == ALLOW || it == DENY } && permissions.values.all { it is JsonArray && it.isEmpty() }
    }

    /**
     * Returns the text of every entry of `permissions.deny` of [document] that is a string, in its order.
     */
    private fun deniedIn(document: JsonDocument): List<String> =
        ((document.content[PERMISSIONS] as? Map<*, *>)?.get(DENY) as? List<*>)
            ?.filterIsInstance<JsonElement>()
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { entry -> entry.isString }?.content }
            .orEmpty()

    private fun entryName(entry: String) = "$DENY:$entry"

    // An entry is its own text, so its fingerprint is that of the text: equal only for the exact same entry.
    private fun entryFingerprint(entry: String) = JsonPrimitive(entry).fingerprint()

    private companion object {
        const val PERMISSIONS = "permissions"
        const val ALLOW = "allow"
        const val DENY = "deny"
        const val PREFIX = "mcp__"
        const val SEPARATOR = "__"
    }
}
