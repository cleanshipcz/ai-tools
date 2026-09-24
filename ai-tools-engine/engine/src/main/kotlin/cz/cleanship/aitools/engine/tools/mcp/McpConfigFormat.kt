package cz.cleanship.aitools.engine.tools.mcp

import java.io.File

/**
 * The file format of one tool's MCP config file, merged at the level of single server entries: the engine owns an entry only when its name is the id of an MCP server manifest of the run.
 */
interface McpConfigFormat {

    /**
     * Returns the content of [file] once [servers] are written into [existing]: each server replaces the entry of its name, an entry named in [ownedMcpIds] that [servers] does not hold is removed, and every other entry and every other part of [existing] is kept.
     *
     * The result is parsed again before it is returned, and returned only when it is valid in this format, every entry and key outside [ownedMcpIds] is unchanged, and the owned entries are exactly [servers].
     *
     * @param existing the current content of [file], or `null` when it does not exist
     * @param ownedMcpIds the name of every entry the engine owns, whether [servers] holds it or not
     * @param file the file the content is for, named in a failure
     * @throws McpConfigFileException naming [file], and never quoting its content, if [existing] cannot be read in this format without losing part of it, defines an owned entry in a way the engine could not replace, or would not keep its foreign content through the merge
     */
    fun merge(existing: String?, servers: List<ResolvedMcpServer>, ownedMcpIds: Set<String>, file: File): String

    /**
     * Returns the names of [ownedMcpIds] that [existing] holds an entry for.
     *
     * @throws McpConfigFileException naming [file], and never quoting its content, if [existing] cannot be read in this format
     */
    fun ownedEntriesIn(existing: String, ownedMcpIds: Set<String>, file: File): Set<String>
}

/**
 * Thrown when an existing MCP config file cannot be read in the format of its tool without losing part of it, or changed while it was merged, so the engine leaves it untouched instead of rewriting it. It fails only the MCP config file of the deployment that writes it, and never quotes the content of the file.
 */
class McpConfigFileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
