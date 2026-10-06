package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.io.escapedForMessage
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.McpToolRestriction
import cz.cleanship.aitools.engine.models.UserDeployment
import cz.cleanship.aitools.engine.models.serialName
import cz.cleanship.aitools.engine.tools.ToolAdapter
import org.slf4j.LoggerFactory

/**
 * Returns every way the MCP servers a deployment selects do not fit what it deploys, each as the message that reports it; empty when they fit.
 *
 * An agent the deployment deploys must use only servers it selects, a restriction may name only a server it selects, and every tool a restriction names must be 1 to 128 of the characters `A-Z a-z 0-9 _ - .`, which MCP tool names are made of. Neither the id of a restricted server nor a tool it names may hold `__`, which separates the two in a permission entry `mcp__<server>__<tool>`. Each message names the deployment, the agent or the server, with its control characters escaped, and never a value or a tool.
 *
 * @param subject the deployment as a message names it, such as `Project 'x'` or `User deployment 'x'`
 * @param field the key of the `mcps` block in the manifest of the deployment, such as `deploy.mcps` or `mcps`
 * @param agents the agents the deployment deploys
 * @param selected the ids of the MCP servers the deployment selects
 * @param restrictions the tool restrictions the deployment declares, by server id
 */
internal fun mcpProblems(
    subject: String,
    field: String,
    agents: Collection<AgentManifest>,
    selected: Set<String>,
    restrictions: Map<String, McpToolRestriction>,
): List<String> {
    val unselectedByAgents = agents.flatMap { agent ->
        agent.mcps.distinct().filterNot { it in selected }.map { server ->
            "$subject deploys the agent '${agent.id}', which uses the MCP server '${server.escapedForMessage()}', but does not select that server under '$field'. Select the server, or leave the agent out."
        }
    }
    // A key of the restriction map is text of the manifest that no grammar checks before it is named here.
    val unselectedRestrictions = restrictions.keys.filterNot { it in selected }.map { server ->
        "$subject restricts the tools of the MCP server '${server.escapedForMessage()}' under '$field.tools', but does not select that server. Select it under '$field', or remove the restriction."
    }
    // A name is not repeated: it is text of the manifest that failed the grammar, which could hold anything, and the server it belongs to is enough to find it.
    val invalidToolNames = restrictions.filterValues { restriction -> (restriction.allow + restriction.deny).any { !MCP_TOOL_NAME.matches(it) } }.keys.map { server ->
        "$subject names a tool of the MCP server '${server.escapedForMessage()}' under '$field.tools' that is not 1 to 128 of the characters A-Z a-z 0-9 _ - . that MCP tool names are made of. Correct the name."
    }
    // Claude Code names a tool `mcp__<server>__<tool>`: with `__` inside either part, an entry of one server could be read as an entry of another, and the engine could no longer tell its own entries from foreign ones.
    val ambiguousServers = restrictions.keys.filter { SEPARATOR in it }.map { server ->
        "$subject restricts the tools of the MCP server '${server.escapedForMessage()}' under '$field.tools', whose id holds '$SEPARATOR', which separates the server from the tool in a permission entry 'mcp__<server>__<tool>'. Rename the server, or remove the restriction."
    }
    val ambiguousToolNames = restrictions.filterValues { restriction -> (restriction.allow + restriction.deny).any { SEPARATOR in it } }.keys.map { server ->
        "$subject names a tool of the MCP server '${server.escapedForMessage()}' under '$field.tools' that holds '$SEPARATOR', which separates the server from the tool in a permission entry 'mcp__<server>__<tool>'. Remove the tool from the restriction."
    }
    return unselectedByAgents + unselectedRestrictions + invalidToolNames + ambiguousServers + ambiguousToolNames
}

private const val SEPARATOR = "__"

private val MCP_TOOL_NAME = Regex("[A-Za-z0-9_.-]{1,128}")

/**
 * Reports every tool of [adapters] that gets none of the MCP servers [deployment] selects in the user scope, once each, with the reason the tool gives.
 */
internal fun reportUserScopeMcpSkips(deployment: UserDeployment, adapters: List<ToolAdapter>) {
    if (deployment.mcps.isEmpty()) return
    adapters.forEach { adapter ->
        adapter.mcpLimits.userScope?.let { reason ->
            MCP_LOG.warn("{}: {} gets none of the MCP servers {} in the user scope: {}.", deployment.manifest.id, adapter.toolType.serialName, deployment.mcps.keys, reason)
        }
    }
}

/**
 * Reports, once for a deployment and a tool, the agents of [agents] that use MCP servers the tool of [adapter] cannot attach to an agent, with the reason it gives.
 */
internal fun reportAgentMcpSkip(deploymentId: String, agents: Collection<AgentManifest>, adapter: ToolAdapter) {
    val reason = adapter.mcpLimits.agentServers ?: return
    val using = agents.filter { it.mcps.isNotEmpty() }.map { "'${it.id}'" }
    if (using.isEmpty()) return
    MCP_LOG.warn("{}: {} attaches no MCP server to the agent(s) {}: {}.", deploymentId, adapter.toolType.serialName, using.joinToString(), reason)
}

// Logged under the engine, which reports every other skip of the run, so one logger holds the whole transcript.
private val MCP_LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
