package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.io.escapedForMessage
import cz.cleanship.aitools.engine.services.ExportService
import java.io.File

/**
 * Writes the MCP servers of one deployment into the MCP config file of one tool, obtained from [cz.cleanship.aitools.engine.tools.ToolAdapter.mcpConfigs] or [cz.cleanship.aitools.engine.tools.UserScope.mcpConfig].
 *
 * It owns only the server entries [McpContext] names; every other entry and every other part of [file] is left as it is, and the file itself is never deleted.
 */
interface McpConfigExporter {

    /** The MCP config file this exporter writes, such as `<project>/.mcp.json`. */
    val file: File

    /**
     * The file whose presence makes the tool ignore [file], such as `<project>/.mcp.json` for `<project>/.github/mcp.json` of Copilot CLI, or `null` when the tool reads [file] whatever lies beside it.
     */
    val hiddenBy: McpConfigShadow? get() = null

    /**
     * Returns the edit that writes the servers of [context] into [file] and removes every entry [context] owns but does not select, prepared from one read of [file]; nothing is written before [PreparedMcpEdit.commit].
     *
     * Which entries are owned is decided by [McpOwnership.ownedServerEntries]: an entry recorded with other fingerprints only is left as it is and logged as a warning naming [file] and the entry. Returns `null` when [context] selects no server and [file] holds no owned entry, so a deployment without servers does not create the file.
     *
     * @throws McpConfigFileException naming [file] if it exists but cannot be read, is not UTF-8, is not a regular file, or cannot be merged in its format without losing part of it; if it, or a directory above it, is a symbolic link that leads nowhere, in a loop, or, in a project, outside the project; or if the nearest entry above it that exists is not a directory. Every failure of [file] is this exception, and the engine collects it as a failure of that deployment and tool while the run goes on. A dry run throws it wherever a deploy would.
     */
    fun prepare(context: McpContext): PreparedMcpEdit?
}

/**
 * A file that, while it exists, makes a tool ignore the MCP config file of an [McpConfigExporter].
 *
 * @property file the file that hides the MCP config file
 * @property reader the program that then reads only [file], as a message names it, such as `Copilot CLI`
 */
data class McpConfigShadow(
    val file: File,
    val reader: String,
)

/**
 * The MCP servers one deployment writes into the MCP config file of a tool.
 *
 * @property servers the servers the deployment selects, in the order they are rendered
 * @property manifestIds the id of every MCP server manifest of the run; an entry of such a name is owned whatever it holds while [servers] is not empty
 * @property recorded every fingerprint the [McpLedger] of the deployment records for each entry the engine wrote into the file, by entry name; such an entry is owned only while it holds one of them
 */
data class McpContext(
    val servers: List<ResolvedMcpServer>,
    val manifestIds: Set<String>,
    val recorded: Map<String, Set<String>> = emptyMap(),
)

/**
 * An edit of one config file, prepared from one read of it, that changes nothing until [commit] runs.
 *
 * @property file the file the edit writes, as its exporter names it
 * @property entries the fingerprint of every entry the file holds of the engine once the edit is committed, by entry name, as the [McpLedger] records it
 */
class PreparedMcpEdit(
    val file: File,
    val entries: Map<String, String>,
    private val write: () -> Unit,
) {
    /**
     * Writes or deletes the file as prepared, through the [ExportService] of its exporter, so a dry run only logs it.
     *
     * @throws McpConfigFileException naming [file] if it no longer holds what it held when the edit was prepared, up to the moment it is replaced, or if it cannot be written or deleted
     */
    fun commit() = write()
}

/**
 * The [McpConfigExporter] of a tool whose MCP config file is [file] in [format], written through [exportService], so a dry run writes nothing.
 *
 * @param root the directory [file] belongs to and how far a symbolic link at [file] or above it may lead - see [TargetRoot]; for a [TargetRoot.UserHome], [prepare] also logs a warning naming [file] and each entry it replaces with other content or removes although the ledger does not record it - see [McpOwnership.reportUnrecordedTakeovers]
 */
class McpConfigFileExporter(
    override val file: File,
    private val format: McpConfigFormat,
    private val exportService: ExportService,
    private val root: TargetRoot,
    override val hiddenBy: McpConfigShadow? = null,
) : McpConfigExporter {

    /**
     * The exporter of an MCP config file of the project [projectDir]; a symbolic link at [file] is written through only when its real target lies inside it.
     */
    constructor(
        file: File,
        format: McpConfigFormat,
        exportService: ExportService,
        projectDir: File,
        hiddenBy: McpConfigShadow? = null,
    ) : this(file, format, exportService, TargetRoot.Project(projectDir), hiddenBy)

    private val managed = ManagedConfigFile(file, root)

    override fun prepare(context: McpContext): PreparedMcpEdit? {
        val target = managed.target()
        val existing = managed.read(target)
        val present = existing?.let { format.entryFingerprints(it, file) }.orEmpty()
        val selected = context.servers.map { it.id }.toSet()
        val owned = McpOwnership.ownedServerEntries(file, present, context.manifestIds, selected, context.recorded)
        val removed = owned - selected
        if (context.servers.isEmpty() && removed.isEmpty()) return null
        val content = format.merge(existing, context.servers, owned, file)
        val entries = format.entryFingerprints(content, file).filterKeys { it in selected }
        // A file of the home is where a server added by hand with its credential most likely lives, and no project checkout keeps a copy of it.
        if (root is TargetRoot.UserHome) McpOwnership.reportUnrecordedTakeovers(file, present, owned, context.recorded, entries)
        val written = "MCP servers ${context.servers.map { it.id }}"
        val describedBy = if (removed.isEmpty()) written else "$written, removing ${removed.sorted().map { it.escapedForMessage() }}"
        return PreparedMcpEdit(file, entries) {
            // Merging works from one read of the file; a tool writing it in the meantime would lose what it wrote to the rewrite, so that file is left for the next deploy instead. The sink checks once more right before it replaces the file.
            managed.requireUnchanged(target, existing)
            managed.write(exportService, target, content, describedBy, previous = existing)
        }
    }
}
