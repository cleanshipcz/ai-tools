package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.Project
import cz.cleanship.aitools.engine.models.serialName
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.mcp.McpContext
import cz.cleanship.aitools.engine.tools.mcp.McpServerResolver
import cz.cleanship.aitools.engine.tools.mcp.McpServerResolvingException
import cz.cleanship.aitools.engine.tools.mcp.ResolvedMcpServer
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Plans the export of the MCP servers of each project through each adapter of one run, resolving every server once however many projects and tools render it, so a variable the config does not declare or a secret the environment does not set is reported once rather than per tool.
 *
 * An instance holds the results of one run and is not thread-safe.
 *
 * @param resolver resolves the variables of a server into the values every MCP config file renders
 */
internal class McpConfigExportPlanner(
    private val resolver: McpServerResolver,
) {
    private val results = mutableMapOf<String, Result<ResolvedMcpServer>>()

    /**
     * Returns the name and the export of the MCP servers of [project] into the MCP config file of [adapter] in [destination], or `null` when [project] selects no server or this tool does not support MCP servers, the latter logged as a warning.
     *
     * A project that selects no server gets no export at all, so none of its MCP config files is read or written. Running the export throws [McpServerResolvingException] when a selected server cannot be resolved, and whatever [cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter.export] throws.
     */
    fun exportFor(project: Project, adapter: ToolAdapter, destination: File): Pair<String, () -> Unit>? {
        if (project.mcps.isEmpty()) return null
        val exporter = adapter.mcpConfig(destination)
        if (exporter == null) {
            LOG.warn(
                "{}: {} has no MCP support in this engine, so the MCP server(s) {} are not deployed for it.",
                project.manifest.id,
                adapter.toolType.serialName,
                project.mcps.keys,
            )
            return null
        }
        return "MCP servers ${project.mcps.keys}" to {
            exporter.export(McpContext(project.mcps.values.map(::resolve), project.ownedMcpIds))
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
        // Logged under the engine, which reports every other skip of the run, so one logger holds the whole transcript.
        private val LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
    }
}
