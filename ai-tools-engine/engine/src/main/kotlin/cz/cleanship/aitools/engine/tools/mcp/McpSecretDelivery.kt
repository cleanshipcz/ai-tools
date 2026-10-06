package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpVariable
import cz.cleanship.aitools.engine.models.SecretSource
import org.slf4j.LoggerFactory

/**
 * Decides how each secret of an MCP server reaches it when a tool starts it - through [manager], or from the environment of the tool - and reports where each one is found, between resolving a server and formatting it.
 *
 * @param manager the secrets manager of the machine, or `null` when the machine uses none. It supplies a secret of a stdio server that the manifest does not declare `from: environment`; every other secret, those of an http server included, is read from the environment of the tool
 * @param variables where a secret is checked for in the environment of the run, without its value being used
 */
class McpSecretDelivery(
    private val manager: McpSecretsManager?,
    private val variables: VariableResolver,
) {

    /**
     * Returns [resolved], the resolved [server], started through [manager] when the manager supplies at least one of its secrets - see [McpSecretsManager.launch] - and otherwise [resolved] itself.
     *
     * @throws McpServerResolvingException naming the server when [manager] cannot start it on this machine
     */
    fun deliver(server: McpServer, resolved: ResolvedMcpServer): ResolvedMcpServer {
        val manager = manager ?: return resolved
        val supplied = suppliedSecrets(server).mapTo(mutableSetOf()) { it.name }
        val transport = resolved.transport as? ResolvedMcpTransport.Stdio ?: return resolved
        if (supplied.isEmpty()) return resolved
        val secrets = transport.env.values
            .filterIsInstance<McpValue.Secret>()
            .filter { it.variable in supplied }
        return resolved.copy(transport = manager.launch(server.id, transport, secrets))
    }

    /**
     * Returns the label of [manager] when it supplies a secret variable of [server] that the manifest does not declare `from: environment`, or `null` when the environment of the tool alone supplies such a secret: for an http server, and on a machine without a manager.
     */
    fun defaultManagerLabel(
        server: McpServer,
    ): String? = manager?.takeIf { server.transport is McpServerTransport.Stdio }?.label

    /**
     * Logs, for every secret variable of [server], where a tool starting it will find the value, naming the server and the variable and never a value.
     *
     * A secret [manager] supplies is reported as exactly one of: held by the manager; not held, but set in the environment of the run to a value the manager counts as set - see [McpSecretsManager.countsAsSet]; found nowhere, with the command to store it; or not known, with the reason. The last two are warnings. Any other secret is reported only when the environment of the run does not set it, as a warning.
     */
    fun report(server: McpServer) {
        val supplied = suppliedSecrets(server).toSet()
        server.variables.filter { it.secret }.forEach { variable ->
            if (manager != null && variable in supplied) reportSupplied(manager, server.id, variable) else reportFromEnvironment(server.id, variable)
        }
    }

    private fun suppliedSecrets(server: McpServer): List<McpVariable> =
        if (manager == null || server.transport !is McpServerTransport.Stdio) emptyList() else server.variables.filter { it.secret && it.source == SecretSource.MANAGER }

    private fun reportSupplied(manager: McpSecretsManager, serverId: String, variable: McpVariable) {
        val kind = variable.kind()
        val name = variable.name
        val label = manager.label
        when (val presence = manager.presenceOf(name)) {
            SecretPresence.Stored -> LOG.info("MCP server '{}' reads the {} secret variable '{}' from {}, which holds it.", serverId, kind, name, label)
            SecretPresence.Absent -> when {
                variables.environmentHolds(name) { manager.countsAsSet(name, it) } -> LOG.info(
                    "MCP server '{}' reads the {} secret variable '{}' from the environment of the tool that starts it: {} does not hold it, and the environment of this run sets it.",
                    serverId,
                    kind,
                    name,
                    label,
                )
                else -> LOG.warn(
                    "MCP server '{}' reads the {} secret variable '{}' from {} or the environment of the tool that starts it, but {} does not hold it and the environment of this run {}. Store it with: {}",
                    serverId,
                    kind,
                    name,
                    label,
                    label,
                    if (variables.isSetInEnvironment(name)) "sets it empty or to its own unexpanded reference, which counts as not set" else "does not set it",
                    manager.storeCommand(name),
                )
            }
            is SecretPresence.Unknown ->
                LOG.warn("MCP server '{}' reads the {} secret variable '{}' from {}, and the engine cannot tell whether {} holds it: {}.", serverId, kind, name, label, label, presence.reason)
        }
    }

    private fun reportFromEnvironment(serverId: String, variable: McpVariable) {
        if (variables.isSetInEnvironment(variable.name)) return
        LOG.warn(
            "MCP server '{}' reads the {} secret variable '{}' from the environment of the tool that starts it, and the environment of this run does not set it. Export it before starting the tool.",
            serverId,
            variable.kind(),
            variable.name,
        )
    }

    private fun McpVariable.kind() = if (required) "required" else "optional"

    companion object {
        // Logged under the resolver, which reported an unset secret before a secrets manager existed, so a transcript keeps every report of a server under one logger.
        private val LOG = LoggerFactory.getLogger(McpServerResolver::class.java)
    }
}
