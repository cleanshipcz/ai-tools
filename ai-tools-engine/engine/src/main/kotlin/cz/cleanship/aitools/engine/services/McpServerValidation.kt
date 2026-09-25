package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.env.VARIABLE_REFERENCE
import cz.cleanship.aitools.engine.models.McpHeader
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerManifest
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpText
import cz.cleanship.aitools.engine.models.McpTextPart
import cz.cleanship.aitools.engine.models.McpVariable

/**
 * Fails when this MCP server, which declares no `source`, lacks what an inline server needs: a transport and a description, and no `select` or `pin`.
 *
 * @throws InvalidMcpServerManifestException naming what is missing or misplaced
 */
internal fun McpServerManifest.requireInlineShape() {
    val problem = when {
        transport == null -> "declares neither 'source' nor 'transport'. Declare a 'transport', or a 'source' naming a server.json."
        select != null -> "declares 'select' but no 'source'. Only a server read from a server.json selects a package or remote; remove 'select'."
        pin != null -> "declares 'pin' but no 'source'. Only the server.json of a pointer server is pinned; remove 'pin'."
        description.isBlank() -> "has a missing or empty 'description'. Declare one that is not blank, or declare a 'source' whose server.json provides it."
        else -> return
    }
    throw InvalidMcpServerManifestException("MCP server '$id' $problem")
}

/**
 * Fails when a name of [variables] is not an environment variable name or is declared more than once.
 *
 * @throws InvalidMcpServerManifestException naming [serverId] and the variable
 */
internal fun requireVariableNames(serverId: String, variables: List<McpVariable>) {
    val names = variables.map { it.name }
    names.firstOrNull { !ENVIRONMENT_NAME.matches(it) }?.let {
        fail(serverId, "declares the variable '$it', which is not an environment variable name. Use letters, digits and underscores, not starting with a digit.")
    }
    names.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
        fail(serverId, "declares the variable '${it.key}' more than once.")
    }
}

/**
 * Returns [value], text of an inline manifest, split into literal parts and references to the variables of [declared].
 *
 * @param place the field [value] was read from, as a message names it
 * @throws InvalidMcpServerManifestException naming [serverId] and [place] if [value] holds a `${` that is not a reference in the form `${NAME}`, or references a name [declared] does not hold; the value itself is never repeated
 */
internal fun parseText(serverId: String, value: String, place: String, declared: Set<String>): McpText {
    val parts = mutableListOf<McpTextPart>()
    var index = 0
    while (index < value.length) {
        val opener = value.indexOf(McpText.REFERENCE_OPENER, index)
        if (opener < 0) {
            parts += McpTextPart.Literal(value.substring(index))
            break
        }
        if (opener > index) parts += McpTextPart.Literal(value.substring(index, opener))
        val reference = VARIABLE_REFERENCE.find(value, opener)?.takeIf { it.range.first == opener }
            ?: fail(
                serverId,
                "holds a '${McpText.REFERENCE_OPENER}' in $place that is not a reference in the form '${McpText.REFERENCE_OPENER}NAME}'. " +
                    "Tool syntax such as a default, 'env:' or 'input:' is not passed through, because every tool would expand it from its own environment.",
            )
        val name = reference.groupValues[1]
        if (name !in declared) {
            fail(serverId, "references '$name' in $place, which it does not declare under 'variables'. Declare it, secret or not, so the engine knows whether its value may be written.")
        }
        parts += McpTextPart.Variable(name)
        index = reference.range.last + 1
    }
    return McpText(parts)
}

/**
 * Fails when this server names something no tool can use, or uses a secret variable where one of the supported tools could not pass it by name.
 *
 * Codex has no `${NAME}` expansion in its config file: it forwards the environment variables `env_vars` lists to a stdio server under their own names, and fills a header from an environment variable only as a whole value (`env_http_headers`) or as a bearer token (`bearer_token_env_var`). A secret is therefore accepted only in those places, so no tool ever needs its value in a file.
 *
 * The text a remote url starts with, up to its first variable, must be `http://` or `https://` followed by a host, with no backslash and no user or password; a url that starts with a variable passes, and the whole url is checked for the same once resolved - see [cz.cleanship.aitools.engine.tools.mcp.McpServerResolver.resolve]. When a header is secret, the url must start with `https://` as written. The url is never repeated in a failure.
 *
 * @throws InvalidMcpServerManifestException naming the server and the offending variable, environment variable or header
 */
internal fun McpServer.requireValid() {
    requireVariableNames(id, variables)
    val names = variables.mapTo(mutableSetOf()) { it.name }
    val secrets = variables.filter { it.secret }.mapTo(mutableSetOf()) { it.name }
    when (val declared = transport) {
        is McpServerTransport.Stdio -> requireStdio(declared, names, secrets)
        is McpServerTransport.Http -> requireHttp(declared, names, secrets)
    }
}

private fun McpServer.requireStdio(transport: McpServerTransport.Stdio, names: Set<String>, secrets: Set<String>) {
    transport.env.keys.firstOrNull { !ENVIRONMENT_NAME.matches(it) }?.let {
        fail(id, "sets the environment variable '$it', which is not an environment variable name.")
    }
    transport.env.keys.firstOrNull { it in names }?.let {
        fail(id, "sets '$it' under 'env' and declares it as a variable. A stdio server already receives every declared variable under its own name; remove one of them.")
    }
    val places = transport.args.map { "'args'" to it } + transport.env.map { (key, value) -> "'env.$key'" to value }
    for ((place, text) in places) {
        text.variableNames.firstOrNull { it in secrets }?.let {
            fail(
                id,
                "references the secret variable '$it' in $place. A stdio server receives a secret only in its environment under its own name, " +
                    "the one way every tool - Codex included - passes it without writing its value; remove the reference and read '$it' from the environment in the server.",
            )
        }
    }
}

private fun McpServer.requireHttp(transport: McpServerTransport.Http, names: Set<String>, secrets: Set<String>) {
    transport.headers.keys.firstOrNull { !HEADER_NAME.matches(it) }?.let {
        fail(id, "sends the header '$it', which is not an HTTP header name.")
    }
    transport.url.variableNames.firstOrNull { it in secrets }?.let {
        fail(id, "references the secret variable '$it' in 'url'. A secret may only be a header value; move it into 'headers'.")
    }
    // A url that starts with written text must write its scheme and host there, so 'https://${'$'}{HOST}/mcp' is refused here; only a url that starts with a variable is judged once resolved.
    val written = (transport.url.parts.firstOrNull() as? McpTextPart.Literal)?.text
    val sendsSecret = transport.headers.values.any { it !is McpHeader.Text }
    if (written != null && SCHEME_ONLY.matches(written) && transport.url.parts.getOrNull(1) is McpTextPart.Variable) {
        fail(id, "has a 'url' that writes its scheme but takes its host from a variable. Write the host after 'http://' or 'https://', or start the url with the variable, which is then checked once resolved.")
    }
    written?.urlProblem()?.let { fail(id, "has a 'url' that ${it.description}.${it.remedy?.let { remedy -> " $remedy." }.orEmpty()}") }
    if (sendsSecret && written?.isHttps() != true) fail(id, "sends a secret header, so its 'url' must start with 'https://' as written, never plain http or a variable.")
    for ((header, value) in transport.headers) {
        val secret = (value as? McpHeader.Text)?.text?.variableNames?.firstOrNull { it in secrets } ?: continue
        fail(
            id,
            "references the secret variable '$secret' in the header '$header' together with other text. " +
                "A secret header is either the variable alone, '\${$secret}', or the bearer token of '${McpHeader.AUTHORIZATION}', 'Bearer \${$secret}', which is what Codex can fill from the environment.",
        )
    }
    val referenced = (transport.url.variableNames + transport.headers.values.flatMap { it.variableNames() }).toSet()
    names.firstOrNull { it !in referenced }?.let {
        fail(id, "declares the variable '$it', which neither 'url' nor 'headers' references. An http server receives only the variables it references; reference it or remove it.")
    }
}

/**
 * Why a url may not be written into an MCP config file.
 *
 * @property description what is wrong with the url, as the end of a sentence about it that never repeats the url
 * @property remedy what the author does about it when the url is written in the manifest, or `null` when the description says it
 */
internal enum class UrlProblem(val description: String, val remedy: String?) {
    BACKSLASH("holds a backslash, which some tools read as a slash", null),
    NOT_HTTP("does not start with 'http://' or 'https://' followed by a host", null),
    CREDENTIALS("carries credentials, which every tool would write into its config file and send", "Pass them as a secret header instead"),
}

/**
 * Returns why this url, or the written start of one, may not be written, or `null` when it may: it must start with `http://` or `https://`, in any case, followed by a host that is not empty and carries no user or password, with no backslash anywhere.
 */
// A WHATWG parser, which Claude Code, VS Code and Cursor use, reads 'https:/user:pw@host' and backslashes as the two slashes, so anything but the plain form is refused rather than second-guessed.
internal fun String.urlProblem(): UrlProblem? = when {
    '\\' in this -> UrlProblem.BACKSLASH
    !URL_START.containsMatchIn(this) -> UrlProblem.NOT_HTTP
    '@' in substringAfter("://").takeWhile { it !in "/?#" } -> UrlProblem.CREDENTIALS
    else -> null
}

// The host must start right after the two slashes: a path, query, fragment, port, user or space there means the host is empty or hidden.
private val URL_START = Regex("""^https?://[^/?#:@\s\\]""", RegexOption.IGNORE_CASE)

private val SCHEME_ONLY = Regex("""^https?://$""", RegexOption.IGNORE_CASE)

/**
 * Returns whether this url, or the start of one, uses https.
 */
internal fun String.isHttps(): Boolean = startsWith("https://", ignoreCase = true)

private fun McpHeader.variableNames(): List<String> = when (this) {
    is McpHeader.Text -> text.variableNames
    is McpHeader.Secret -> listOf(variable)
    is McpHeader.BearerSecret -> listOf(variable)
}

private fun fail(serverId: String, problem: String): Nothing =
    throw InvalidMcpServerManifestException("MCP server '$serverId' $problem")

private val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

// The token characters of RFC 9110, which is what a header name may consist of.
private val HEADER_NAME = Regex("[A-Za-z0-9!#$%&'*+.^_`|~-]+")

/**
 * Thrown when an MCP server manifest, or the server a pointer derives, is inconsistent - see [requireInlineShape], [parseText] and [requireValid]. It always travels wrapped in a [ManifestLoadingException], so the author is told which file to fix.
 */
class InvalidMcpServerManifestException(message: String) : RuntimeException(message)
