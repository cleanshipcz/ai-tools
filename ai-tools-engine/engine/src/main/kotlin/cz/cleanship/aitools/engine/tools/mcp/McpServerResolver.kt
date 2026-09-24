package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.McpHeader
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpText
import cz.cleanship.aitools.engine.models.McpTextPart
import cz.cleanship.aitools.engine.models.McpVariable
import cz.cleanship.aitools.engine.services.isHttps
import cz.cleanship.aitools.engine.services.urlProblem
import org.slf4j.LoggerFactory

/**
 * Turns a loaded [McpServer] into the [ResolvedMcpServer] every MCP config file renders.
 *
 * @param variables where the value of a plain variable is read - the `env_vars` of the config files of the run, and never its environment - and where a secret one is checked for without being read
 */
class McpServerResolver(
    private val variables: VariableResolver,
) {

    /**
     * Returns [server] with every reference to a plain variable replaced by its value from the `env_vars` of the config files, and every secret variable kept as [McpValue.Secret] or [McpValue.BearerSecret], never read.
     *
     * A stdio server receives every declared variable in its environment after its fixed `env` entries. An optional plain variable the config does not declare is left out, and so is an `env` entry or header that references it. A secret variable the environment of the run does not carry is logged as a warning naming it, never its value. No message names a value.
     *
     * @throws McpServerResolvingException naming the server and the variable if a required plain variable is not declared by the config; if an optional one that is not is part of an argument or the url, which cannot be left out; if a plain value holds `${`, which a tool would expand; or if a secret variable is referenced anywhere a tool would need its value written, which a server returned by [cz.cleanship.aitools.engine.services.LoaderService.loadMcpServer] never does
     */
    fun resolve(server: McpServer): ResolvedMcpServer {
        warnAboutUnsetSecrets(server)
        val substitution = Substitution(server)
        val resolved = when (val transport = server.transport) {
            is McpServerTransport.Stdio -> substitution.stdio(transport)
            is McpServerTransport.Http -> substitution.http(transport)
        }
        return ResolvedMcpServer(server.id, resolved)
    }

    private fun warnAboutUnsetSecrets(server: McpServer) {
        server.variables.filter { it.secret && !variables.isSetInEnvironment(it.name) }.forEach {
            LOG.warn(
                "MCP server '{}' reads the {} secret variable '{}' from the environment of the tool that starts it, and the environment of this run does not set it. Export it before starting the tool.",
                server.id,
                if (it.required) "required" else "optional",
                it.name,
            )
        }
    }

    /**
     * Resolves the texts of one server, knowing its declared variables.
     */
    private inner class Substitution(private val server: McpServer) {

        private val declared: Map<String, McpVariable> = server.variables.associateBy { it.name }

        fun stdio(transport: McpServerTransport.Stdio): ResolvedMcpTransport.Stdio {
            val env = LinkedHashMap<String, McpValue>()
            transport.env.forEach { (key, text) -> text(text, "'env.$key'", omissible = true)?.let { env[key] = McpValue.Plain(it) } }
            server.variables.forEach { variable -> environmentValue(variable)?.let { env[variable.name] = it } }
            return ResolvedMcpTransport.Stdio(
                command = transport.command,
                args = transport.args.map { checkNotNull(text(it, "'args'", omissible = false)) },
                env = env,
            )
        }

        fun http(transport: McpServerTransport.Http): ResolvedMcpTransport.Http {
            val headers = LinkedHashMap<String, McpValue>()
            transport.headers.forEach { (name, header) -> header(name, header)?.let { headers[name] = it } }
            val url = checkNotNull(text(transport.url, "'url'", omissible = false))
            // A url resolved from a variable is checked here, where its value is known; the url itself is never repeated.
            val problem = when {
                url.urlProblem() != null -> "MCP server '${server.id}' resolves its 'url' to one that ${url.urlProblem()}."
                headers.values.any { it !is McpValue.Plain } && !url.isHttps() -> "MCP server '${server.id}' sends a secret header, but its 'url' resolves to one that is not https."
                else -> return ResolvedMcpTransport.Http(url = url, headers = headers)
            }
            throw McpServerResolvingException(server.id, problem)
        }

        private fun environmentValue(variable: McpVariable): McpValue? = if (variable.secret) {
            McpValue.Secret(variable.name, variable.required)
        } else {
            plainValue(variable, "its environment", omissible = true)?.let { McpValue.Plain(it) }
        }

        private fun header(name: String, header: McpHeader): McpValue? = when (header) {
            is McpHeader.Secret -> McpValue.Secret(header.variable, secretVariable(header.variable).required)
            is McpHeader.BearerSecret -> McpValue.BearerSecret(header.variable, secretVariable(header.variable).required)
            is McpHeader.Text -> text(header.text, "the header '$name'", omissible = true)?.let { McpValue.Plain(it) }
        }

        private fun secretVariable(name: String): McpVariable = declared[name]?.takeIf { it.secret }
            ?: throw McpServerResolvingException(server.id, "MCP server '${server.id}' sends '$name' as a secret header, but does not declare it as a secret variable.")

        /**
         * Returns [text] with every reference replaced by its value, or `null` when it references an optional variable the config does not declare and [omissible] allows leaving the value out.
         */
        private fun text(text: McpText, place: String, omissible: Boolean): String? = buildString {
            for (part in text.parts) {
                when (part) {
                    is McpTextPart.Literal -> append(part.text)
                    is McpTextPart.Variable -> append(plainValue(variableOf(part.name, place), place, omissible) ?: return null)
                }
            }
        }

        private fun variableOf(name: String, place: String): McpVariable {
            val variable = declared[name]
            val problem = when {
                variable == null -> "MCP server '${server.id}' references '$name' in $place, which it does not declare."
                // The loader never lets a secret reach here; refusing is what keeps a server built any other way from ever writing one.
                variable.secret -> "MCP server '${server.id}' references the secret variable '$name' in $place, where it would have to be written as a value."
                else -> return variable
            }
            throw McpServerResolvingException(server.id, problem)
        }

        /**
         * Returns the value the config declares for the plain [variable] used in [place], or `null` when it declares none, the variable is optional and [omissible] allows leaving it out.
         */
        private fun plainValue(variable: McpVariable, place: String, omissible: Boolean): String? {
            val value = variables.configValueOf(variable.name)
            val problem = when {
                value != null && McpText.REFERENCE_OPENER in value ->
                    "MCP server '${server.id}' reads the variable '${variable.name}' for $place, and its value in 'env_vars:' holds '${McpText.REFERENCE_OPENER}', which a tool would expand from its own environment. Declare a value without it."
                value != null -> return value
                variable.required ->
                    "MCP server '${server.id}' needs the variable '${variable.name}' for $place, which 'env_vars:' of config.yml and config.local.yml do not declare. " +
                        "Plain variables are read from there only, never from the environment of the run. Declare it, mark it 'required: false', or mark it 'secret: true' to pass it from the environment of the tool."
                omissible -> return null
                else ->
                    "MCP server '${server.id}' uses the optional variable '${variable.name}' in $place, which 'env_vars:' of config.yml and config.local.yml do not declare, and $place cannot be left out. " +
                        "Declare it, or mark it 'required: true'."
            }
            throw McpServerResolvingException(server.id, problem)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(McpServerResolver::class.java)
    }
}

/**
 * Thrown when the MCP server [serverId] cannot be rendered because a variable it needs is not declared by the config, or a value would carry what must never be written. It fails only the MCP config files of the deployments that select the server, and never names a value.
 */
class McpServerResolvingException(val serverId: String, message: String) : RuntimeException(message)
