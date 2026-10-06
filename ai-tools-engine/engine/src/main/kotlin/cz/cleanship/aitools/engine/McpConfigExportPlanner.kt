package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.io.resolvedPath
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpToolRestriction
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeployment
import cz.cleanship.aitools.engine.models.serialName
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.tools.McpLimits
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.UserScope
import cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter
import cz.cleanship.aitools.engine.tools.mcp.McpConfigFileException
import cz.cleanship.aitools.engine.tools.mcp.McpContext
import cz.cleanship.aitools.engine.tools.mcp.McpLedger
import cz.cleanship.aitools.engine.tools.mcp.McpOwnership
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsContext
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsExporter
import cz.cleanship.aitools.engine.tools.mcp.McpServerResolver
import cz.cleanship.aitools.engine.tools.mcp.McpServerResolvingException
import cz.cleanship.aitools.engine.tools.mcp.PreparedMcpEdit
import cz.cleanship.aitools.engine.tools.mcp.ResolvedMcpServer
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Plans the export of the MCP servers and tool restrictions of each deployment through each tool of one run, resolving every server once however many deployments and tools render it, so a variable the config does not declare or a secret the environment does not set is reported once rather than per tool. It keeps one [McpLedger] per target directory, found by its real path, read once and shared by every tool and deployment that writes into it.
 *
 * An instance holds the results of one run and is not thread-safe.
 *
 * @param resolver resolves the variables of a server into the values every MCP config file renders
 * @param ledgerService writes the ledgers, so a dry run writes none
 */
internal class McpConfigExportPlanner(
    private val resolver: McpServerResolver,
    private val ledgerService: ExportService,
) {
    private val results = mutableMapOf<String, Result<ResolvedMcpServer>>()
    private val ledgers = mutableMapOf<File, Result<McpLedger>>()
    private var coveredBy: Map<File, Set<String>> = emptyMap()

    /**
     * Records which deployments of the run cover each MCP file with their `mcps` block, before any export is planned, and logs every file more than one deployment covers as an error. Files are compared by their real paths, so a project reached through a link to another project directory covers the files of that directory.
     *
     * A deployment covering a file another deployment covers too writes none of its MCP files - see [exportsFor]; a deployment without an `mcps` block never touches a file another deployment covers.
     *
     * @param claims every MCP config and permissions file each deployment that declares an `mcps` block writes through its tools
     */
    fun claim(claims: List<McpFileClaim>) {
        val byFile = claims.groupBy { it.file.resolvedPath() }
        coveredBy = byFile.mapValues { (_, claimed) -> claimed.map { it.deployment }.toSortedSet() }
        byFile.filterValues { claimed -> claimed.map { it.deployment }.toSet().size > 1 }.values.forEach { claimed ->
            LOG.error(
                "Not writing the MCP entries of '{}': the deployments {} each declare an 'mcps' block covering it. Keep the 'mcps' block in only one of them.",
                claimed.first().file.absolutePath,
                claimed.map { it.deployment }.toSortedSet().toList(),
            )
        }
    }

    /**
     * Returns the exports that write the MCP servers and tool restrictions of [deployment] through one tool: into each of its MCP config files [configs] and into its permissions file [permissions], each when the deployment selects or restricts something for that file, or the ledger of the target records entries in it. A tool without [configs] is logged as skipped for the servers of the deployment; a tool whose [limits] cannot apply the `allow` or the `deny` list of a restriction, as skipped for that list, which none of its files then receives.
     *
     * Nothing is planned, and no file but the ledger of the target is read, for a deployment that selects and restricts nothing and whose ledger records nothing for these files, nor for a file another deployment covers with its `mcps` block while [deployment] declares none - see [claim]. A deployment whose `mcps` block covers a file another deployment covers too gets one export that throws [ContendedMcpFileException].
     *
     * An export that writes servers into a config file whose [McpConfigExporter.hiddenBy] file exists, while [McpDeployment.mcpConfigFiles] does not hold that file, logs one warning naming both files once the edit is committed, in a dry run as in a deploy.
     *
     * Running an export prepares the edit of its file, records in the ledger what the edit writes beside what the ledger recorded, commits the edit, and then narrows the record to what the file holds of the engine; an edit that cannot be prepared records nothing, and a file that holds nothing of the engine is dropped from the record. A ledger that cannot be written before the commit leaves the file as it is; a ledger that cannot be written after the commit leaves the file written and the ledger recording both what the file held and what the edit wrote, so the engine still owns every entry the edit changed. Either failure is thrown once as [McpLedgerException], and the other exports of the tool then write nothing. Running an export also throws [McpServerResolvingException] when a selected server cannot be resolved, and [McpConfigFileException] for every failure of the file, and of a ledger that cannot be read.
     *
     * @param deletedFirst the paths the deploy deletes before it writes the files of this tool; a permissions file below one of them is planned as missing, without being read or reached, which is how a dry run, deleting nothing, plans what the deploy does
     */
    // One parameter per part of a tool the export needs, as the adapters answer them one by one; bundling them would only move the list.
    @Suppress("LongParameterList")
    fun exportsFor(
        deployment: McpDeployment,
        toolType: ToolType,
        configs: List<McpConfigExporter>,
        permissions: McpPermissionsExporter?,
        limits: McpLimits,
        deletedFirst: Collection<File> = emptyList(),
    ): List<PlannedMcpExport> {
        if (configs.isEmpty() && deployment.servers.isNotEmpty()) {
            LOG.warn("{}: {} has no MCP support in this engine, so the MCP server(s) {} are not deployed for it.", deployment.id, toolType.serialName, deployment.servers.keys)
        }
        if (configs.isNotEmpty()) reportSkippedLists(deployment, toolType, limits)
        if (configs.isEmpty() && permissions == null) return emptyList()
        contendedExport(deployment, configs.map { it.file } + listOfNotNull(permissions?.file))?.let { return listOf(it) }
        val ledger = ledgerFor(deployment.root).getOrElse { failure ->
            // A ledger that cannot be read decides nothing: every MCP file of the tool in this target is left as it is, and the failure names the ledger.
            return listOf(PlannedMcpExport(LEDGER, directory = null) { throw failure })
        }
        val restrictions = deployment.restrictions.appliedBy(limits)
        return configs.filter { mayWrite(deployment, it.file) }.mapNotNull { configExport(deployment, toolType, restrictions, it, ledger) } + listOfNotNull(
            permissions?.takeIf { mayWrite(deployment, it.file) }?.let { permissionsExport(deployment, restrictions, it, ledger, presumedAbsent = deletedFirst.any { deleted -> it.file.isBelow(deleted) }) },
        )
    }

    /**
     * Logs the lists of the restrictions of [deployment] the tool [toolType] cannot apply, with the reasons of [limits]: once for the whole restriction when neither list can be applied for the same reason, otherwise once for each list.
     */
    private fun reportSkippedLists(deployment: McpDeployment, toolType: ToolType, limits: McpLimits) {
        val allowing = deployment.restrictions.filterValues { it.allow.isNotEmpty() }.keys
        val denying = deployment.restrictions.filterValues { it.deny.isNotEmpty() }.keys
        val allowReason = limits.allowedTools?.takeIf { allowing.isNotEmpty() }
        val denyReason = limits.deniedTools?.takeIf { denying.isNotEmpty() }
        if (allowReason != null && allowReason == denyReason) {
            LOG.warn("{}: {} does not restrict the tools of the MCP server(s) {}: {}.", deployment.id, toolType.serialName, allowing + denying, allowReason)
            return
        }
        allowReason?.let { LOG.warn("{}: {} does not apply the 'allow' list of the MCP server(s) {}: {}.", deployment.id, toolType.serialName, allowing, it) }
        denyReason?.let { LOG.warn("{}: {} does not apply the 'deny' list of the MCP server(s) {}: {}.", deployment.id, toolType.serialName, denying, it) }
    }

    /**
     * Returns the export that fails [deployment] for the files of [files] its `mcps` block covers together with another deployment, or `null` when it shares none.
     */
    private fun contendedExport(deployment: McpDeployment, files: List<File>): PlannedMcpExport? {
        if (!deployment.declaresMcps) return null
        val contended = files.filter { coverersOf(it).size > 1 }
        if (contended.isEmpty()) return null
        val deployments = contended.flatMap(::coverersOf).toSortedSet().joinToString()
        val named = contended.joinToString(" and ") { "'${it.absolutePath}'" }
        val (verb, pronoun) = if (contended.size == 1) "is" to "it" else "are" to "them"
        return PlannedMcpExport("MCP servers ${deployment.servers.keys}", directory = null) {
            throw ContendedMcpFileException(
                "$named $verb covered by the 'mcps' block of more than one deployment of this run: $deployments. None of them may write $pronoun; keep the 'mcps' block in only one of them, and deploy again.",
            )
        }
    }

    // A deployment without an mcps block only cleans up what the ledger records: once another deployment of the run covers the file, those entries are that deployment's to keep or remove.
    private fun mayWrite(deployment: McpDeployment, file: File): Boolean =
        deployment.declaresMcps || coverersOf(file).isEmpty()

    private fun coverersOf(file: File): Set<String> = coveredBy[file.resolvedPath()].orEmpty()

    private fun configExport(
        deployment: McpDeployment,
        toolType: ToolType,
        restrictions: Map<String, McpToolRestriction>,
        config: McpConfigExporter,
        ledger: McpLedger,
    ): PlannedMcpExport? {
        val path = deployment.root.pathOf(config.file)
        if (deployment.servers.isEmpty() && ledger.recorded(path).isEmpty()) return null
        return PlannedMcpExport("MCP servers ${deployment.servers.keys}", config.file.parentFile) {
            val servers = deployment.servers.values.map { resolve(it).copy(tools = restrictions[it.id] ?: McpToolRestriction()) }
            if (recordedAround(deployment.root, path) { recorded -> config.prepare(McpContext(servers, deployment.ownedMcpIds, recorded)) }) {
                reportHidden(deployment, toolType, config)
            }
        }
    }

    /**
     * Logs a warning naming the file that hides [config] from its tool when [deployment] selects servers, that file exists, and [McpDeployment.mcpConfigFiles] of [deployment] does not hold it.
     */
    // The file that hides it is compared by its real path, so a project reached through a link still counts as writing it. The servers are written anyway, so they take effect as soon as that file is removed.
    private fun reportHidden(deployment: McpDeployment, toolType: ToolType, config: McpConfigExporter) {
        val shadow = config.hiddenBy ?: return
        if (deployment.servers.isEmpty() || !shadow.file.exists()) return
        if (deployment.mcpConfigFiles.any { it.resolvedPath() == shadow.file.resolvedPath() }) return
        val shadowName = shadow.file.name
        val hiddenName = config.file.relativeTo(shadow.file.parentFile).invariantSeparatorsPath
        LOG.warn(
            "{}: '{}' gets the MCP servers {} for {}, but '{}' exists and this deployment does not write it: {} reads only {} in that directory and ignores {} there. Let this deployment write {} too, or remove that file.",
            deployment.id,
            config.file.absolutePath,
            deployment.servers.keys,
            toolType.serialName,
            shadow.file.absolutePath,
            shadow.reader,
            shadowName,
            hiddenName,
            shadowName,
        )
    }

    private fun permissionsExport(
        deployment: McpDeployment,
        restrictions: Map<String, McpToolRestriction>,
        permissions: McpPermissionsExporter,
        ledger: McpLedger,
        presumedAbsent: Boolean,
    ): PlannedMcpExport? {
        val path = deployment.root.pathOf(permissions.file)
        if (restrictions.isEmpty() && ledger.recorded(path).isEmpty()) return null
        return PlannedMcpExport("MCP tool permissions ${restrictions.keys}", permissions.file.parentFile) {
            recordedAround(deployment.root, path) { recorded -> permissions.prepare(McpPermissionsContext(restrictions, recorded, presumedAbsent)) }
        }
    }

    /**
     * Prepares the edit of the file at [path] of [root] with what the ledger of [root] records for it, and commits it between two records of that ledger: [McpOwnership.pendingRecord] before, so the ledger owns every entry of the engine the file holds whether the commit runs or not, and [McpOwnership.committedRecord] after it succeeded. Nothing runs when the ledger of [root] could not be written earlier in the run, a failure already reported.
     *
     * @return whether an edit was committed
     * @throws McpLedgerException if the ledger cannot be written: before the commit, the file is left as it was; after it, the file holds the edit and the ledger the pending record
     */
    private fun recordedAround(
        root: TargetRoot,
        path: String,
        prepare: (recorded: Map<String, Set<String>>) -> PreparedMcpEdit?,
    ): Boolean {
        val ledger = ledgers[root.directory.resolvedPath()]?.getOrNull() ?: return false
        val recorded = ledger.recorded(path)
        val edit = prepare(recorded)
        if (edit == null) {
            // Nothing of the engine is left in the file: what the ledger still names there was removed by hand or changed since, and is no longer the engine's.
            record(root, path, emptyMap())
            return false
        }
        // A record wider than the file is harmless, since an entry is removed only while it holds a fingerprint recorded for it. The pending record keeps what the file holds until the commit and adds what the edit writes, so a failed commit and a ledger that cannot be narrowed after a commit both leave every entry of the engine owned.
        if (!record(root, path, McpOwnership.pendingRecord(recorded, edit.entries))) return false
        edit.commit()
        record(root, path, McpOwnership.committedRecord(edit.entries))
        return true
    }

    /**
     * Records [entries] for the file at [path] in the ledger of [root], and returns whether that ledger can still be written.
     */
    private fun record(root: TargetRoot, path: String, entries: Map<String, Set<String>>): Boolean {
        val key = root.directory.resolvedPath()
        val ledger = ledgers[key]?.getOrNull() ?: return false
        try {
            ledger.record(path, entries)
        } catch (ex: McpConfigFileException) {
            // What the ledger holds in memory is no longer what its file holds, so it decides nothing more in this run: the other exports of this tool write nothing, and every later tool of the target fails naming it.
            ledgers[key] = Result.failure(ex)
            throw McpLedgerException(ex)
        }
        return true
    }

    private fun ledgerFor(root: TargetRoot): Result<McpLedger> = ledgers.getOrPut(root.directory.resolvedPath()) {
        try {
            Result.success(McpLedger.load(root, ledgerService))
        } catch (ex: McpConfigFileException) {
            Result.failure(ex)
        }
    }

    private fun resolve(server: McpServer): ResolvedMcpServer = results
        .getOrPut(server.id) {
            try {
                Result.success(resolver.resolve(server))
            } catch (ex: McpServerResolvingException) {
                Result.failure(ex)
            }
        }.getOrThrow()

    companion object {
        /** The name every failure of an MCP ledger is reported under. */
        const val LEDGER = "MCP ledger"

        // Logged under the engine, which reports every other skip of the run, so one logger holds the whole transcript.
        private val LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
    }
}

/**
 * Returns the exports of the MCP files of [deployment] in the user scope [scope] of the tool of [adapter] under [userHome] - see [McpConfigExportPlanner.exportsFor].
 */
internal fun McpConfigExportPlanner.userScopeExportsFor(
    deployment: UserDeployment,
    adapter: ToolAdapter,
    scope: UserScope,
    userHome: File,
): List<PlannedMcpExport> {
    val manifest = deployment.manifest
    return exportsFor(
        McpDeployment(manifest.id, TargetRoot.UserHome(userHome), deployment.mcps, deployment.ownedMcpIds, manifest.mcps?.tools.orEmpty(), declaresMcps = manifest.mcps != null),
        adapter.toolType,
        listOfNotNull(scope.mcpConfig()),
        scope.mcpPermissions(),
        adapter.mcpLimits,
    )
}

/**
 * Returns these restrictions with every list [limits] names a reason for emptied, leaving out a server left with no list, so a tool never receives what it cannot apply.
 */
private fun Map<String, McpToolRestriction>.appliedBy(
    limits: McpLimits,
): Map<String, McpToolRestriction> = mapValues { (_, restriction) ->
    McpToolRestriction(
        allow = if (limits.allowedTools == null) restriction.allow else emptyList(),
        deny = if (limits.deniedTools == null) restriction.deny else emptyList(),
    )
}.filterValues { it.allow.isNotEmpty() || it.deny.isNotEmpty() }

/**
 * Returns the path of [file], a file below the directory of this root, relative to that directory and separated by `/`, as the [McpLedger] of the root names it.
 */
private fun TargetRoot.pathOf(file: File): String = file.absoluteFile
    .normalize()
    .relativeTo(directory.absoluteFile.normalize())
    .invariantSeparatorsPath

private fun File.isBelow(directory: File): Boolean = absoluteFile.normalize().startsWith(directory.absoluteFile.normalize())

/**
 * One MCP file a deployment that declares an `mcps` block writes through one of its tools.
 *
 * @property file the MCP config or permissions file
 * @property deployment the deployment as a message names it, such as `project 'x'`
 */
internal data class McpFileClaim(
    val file: File,
    val deployment: String,
)

/**
 * Thrown when the MCP ledger of a target cannot be written, so the export that needed the record leaves its file as it is. Its message names the ledger, never its content.
 */
class McpLedgerException(
    cause: McpConfigFileException,
) : RuntimeException(cause.message, cause)

/**
 * What one deployment - a project or a user deployment - asks of the MCP files of its target.
 *
 * @property id the id of the deployment, as a log line names it
 * @property root the directory the deployment writes into, and how far a link below it may lead
 * @property servers the MCP servers the deployment selects, in the order they are rendered
 * @property ownedMcpIds the id of every MCP server manifest of the run
 * @property restrictions the allowed and denied tools of each selected server that the deployment restricts, by server id
 * @property declaresMcps whether the deployment declares an `mcps` block, which makes it cover the MCP files of its tools - see [McpConfigExportPlanner.claim]
 * @property mcpConfigFiles every MCP config file of every tool the deployment deploys through; a [McpConfigExporter.hiddenBy] file among them, compared by real path, is one the deployment writes itself, so it is not reported as hiding anything - see [McpConfigExportPlanner.exportsFor]. Empty by default.
 */
internal data class McpDeployment(
    val id: String,
    val root: TargetRoot,
    val servers: Map<String, McpServer>,
    val ownedMcpIds: Set<String>,
    val restrictions: Map<String, McpToolRestriction>,
    val declaresMcps: Boolean,
    val mcpConfigFiles: Set<File> = emptySet(),
)

/**
 * One export of MCP entries, named [name] the way every other export of the run is named, and run by [run].
 *
 * @property directory the directory of the file it writes, which the engine checks with the directories of the tool, or `null` when it writes none
 */
internal data class PlannedMcpExport(
    val name: String,
    val directory: File?,
    val run: () -> Unit,
)
