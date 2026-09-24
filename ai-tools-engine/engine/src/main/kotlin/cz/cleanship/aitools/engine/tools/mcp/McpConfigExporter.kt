package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.services.ExportService
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Writes the MCP servers of one deployment into the MCP config file of one tool, obtained from [cz.cleanship.aitools.engine.tools.ToolAdapter.mcpConfig].
 *
 * It owns only the server entries named in [McpContext.ownedMcpIds]; every other entry and every other part of [file] is left as it is, and the file itself is never deleted.
 */
interface McpConfigExporter {

    /** The MCP config file this exporter writes, such as `<project>/.mcp.json`. */
    val file: File

    /**
     * Writes the servers of [context] into [file], removing every entry of [McpContext.ownedMcpIds] that [context] does not select.
     *
     * Nothing is written when [context] selects no server and [file] holds no owned entry, so a deployment without servers does not create the file.
     *
     * @throws McpConfigFileException naming [file] if it exists but cannot be read, cannot be merged in its format without losing part of it, or changes while it is merged
     */
    fun export(context: McpContext)
}

/**
 * The MCP servers one deployment writes into the MCP config file of a tool.
 *
 * @property servers the servers the deployment selects, in the order they are rendered
 * @property ownedMcpIds the id of every MCP server manifest of the run, the entries the engine owns in every MCP config file it writes
 */
data class McpContext(
    val servers: List<ResolvedMcpServer>,
    val ownedMcpIds: Set<String>,
)

/**
 * The [McpConfigExporter] of a tool whose MCP config file is [file] in [format], written through [exportService], so a dry run writes nothing.
 *
 * @param projectDir the project directory [file] belongs to; a symbolic link at [file] is written through only when its real target lies inside it
 */
class McpConfigFileExporter(
    override val file: File,
    private val format: McpConfigFormat,
    private val exportService: ExportService,
    private val projectDir: File,
) : McpConfigExporter {

    override fun export(context: McpContext) {
        val target = target()
        val existing = read(target)
        val removed = existing?.let { format.ownedEntriesIn(it, context.ownedMcpIds, file) }.orEmpty() - context.servers.map { it.id }.toSet()
        if (context.servers.isEmpty() && removed.isEmpty()) return
        val content = format.merge(existing, context.servers, context.ownedMcpIds, file)
        // Merging works from one read of the file; a tool writing it in the meantime would lose what it wrote to the rewrite, so that file is left for the next deploy instead.
        if (read(target) != existing) {
            throw McpConfigFileException("'${file.absolutePath}' changed while the engine merged it, so the engine leaves it untouched. Deploy again once the tool writing it is idle.")
        }
        val written = "MCP servers ${context.servers.map { it.id }}"
        exportService.writeConfigFile(target, content, describedBy = if (removed.isEmpty()) written else "$written, removing ${removed.sorted()}")
    }

    /**
     * Returns the file to read and write: [file] itself, or the real target of a symbolic link at [file] that lies inside [projectDir]. The directory holding [file] must lie inside [projectDir] once links are resolved too, and nothing at the path is opened before it is known to be a regular file.
     */
    private fun target(): File {
        requireDirectoryInside()
        val path = file.toPath()
        val target = if (Files.isSymbolicLink(path)) linkTarget(path) else path
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            // A FIFO would block the run on open, a device could be read without end, and a directory cannot be merged.
            throw McpConfigFileException("'${file.absolutePath}' is not a regular file, so the engine leaves it untouched. Remove what is at that path, and deploy again.")
        }
        return if (target == path) file else target.toFile()
    }

    /**
     * Fails when the directory holding [file], or its nearest existing ancestor, lies outside [projectDir] once links are resolved, as a linked `.codex` or `.vscode` of a dotfile setup would.
     */
    private fun requireDirectoryInside() {
        // A project directory that does not exist yet holds no link, and every folder below it is created by the deploy itself.
        if (!projectDir.exists()) return
        val existing = generateSequence(file.absoluteFile.parentFile) { it.parentFile }.first { it.exists() }
        val (directory, root) = realPaths(existing.toPath())
        if (!directory.startsWith(root)) {
            throw McpConfigFileException(
                "'${file.absolutePath}' lies in '$directory' once links are resolved, outside the project directory '$root'. The engine writes only inside the project, so it leaves the file untouched.",
            )
        }
    }

    private fun realPaths(path: Path): Pair<Path, Path> = try {
        path.toRealPath() to projectDir.toPath().toRealPath()
    } catch (ex: IOException) {
        // A link that leads nowhere, loops or is too long fails this file only, like every other problem of the file.
        throw McpConfigFileException("'${file.absolutePath}' is reached through a symbolic link that cannot be followed (${ex.javaClass.simpleName}), so the engine leaves it untouched.", ex)
    }

    private fun linkTarget(path: Path): Path {
        val (target, root) = realPaths(path)
        if (!target.startsWith(root)) {
            throw McpConfigFileException(
                "'${file.absolutePath}' is a symbolic link to '$target', which lies outside the project directory '$root'. The engine writes through a link only inside the project, so it leaves both untouched.",
            )
        }
        return target
    }

    private fun read(target: File): String? = try {
        if (target.exists()) target.readText() else null
    } catch (ex: IOException) {
        throw McpConfigFileException("'${file.absolutePath}' cannot be read (${ex.javaClass.simpleName}), so the engine leaves it untouched.", ex)
    }
}
