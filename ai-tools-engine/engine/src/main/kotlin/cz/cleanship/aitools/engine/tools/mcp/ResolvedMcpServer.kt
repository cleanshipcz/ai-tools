package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.models.McpToolRestriction

/**
 * An MCP server as every MCP config file renders it: every plain value resolved, every secret one only named.
 *
 * @property id the name of the server entry, which is the id of its manifest
 * @property transport how a tool starts or reaches the server
 * @property tools the tools of the server the deployment allows and denies, which a format renders when its tool reads them from the server entry, as Codex does
 */
data class ResolvedMcpServer(
    val id: String,
    val transport: ResolvedMcpTransport,
    val tools: McpToolRestriction = McpToolRestriction(),
)

/**
 * How a tool starts or reaches a [ResolvedMcpServer].
 */
sealed interface ResolvedMcpTransport {

    /**
     * A server started as a local process.
     *
     * @property command the executable
     * @property args the arguments passed to [command]
     * @property env the environment of the server by variable name, in the order it is rendered; a [McpValue.Secret] entry is always keyed by the name of its own variable
     */
    data class Stdio(
        val command: String,
        val args: List<String>,
        val env: Map<String, McpValue>,
    ) : ResolvedMcpTransport

    /**
     * A remote server reached over Streamable HTTP.
     *
     * @property url the endpoint
     * @property headers the headers by name, in the order they are rendered; a [McpValue.BearerSecret] only ever under `Authorization`
     */
    data class Http(
        val url: String,
        val headers: Map<String, McpValue>,
    ) : ResolvedMcpTransport
}

/**
 * One value of an MCP config file: either written as it is, or a secret each tool reads from its own environment.
 */
sealed interface McpValue {

    /** A value written into the file as it is. */
    data class Plain(val value: String) : McpValue

    /**
     * The whole value of a secret variable, rendered as a reference to [variable] and never as its value.
     *
     * @property required whether the server cannot run without it; a tool that can fall back to an empty value is told to for an optional one
     */
    data class Secret(val variable: String, val required: Boolean) : McpValue

    /**
     * `Bearer ` followed by the value of the secret variable [variable], rendered as a reference and never as its value.
     *
     * @property required whether the server cannot run without it
     */
    data class BearerSecret(val variable: String, val required: Boolean) : McpValue
}
