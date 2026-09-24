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
 * Fails when this MCP server, which declares no `source`, lacks what an inline server needs: a transport and a description, and no selection.
 *
 * @throws InvalidMcpServerManifestException naming what is missing or misplaced
 */
internal fun McpServerManifest.requireInlineShape() {
    val problem = when {
        transport == null -> "declares neither 'source' nor 'transport'. Declare a 'transport', or a 'source' naming a server.json."
        select != null -> "declares 'select' but no 'source'. Only a server read from a server.json selects a package or remote; remove 'select'."
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
 * A remote url must not carry a user or password, and must start with `https://` when a header is secret; the url is never repeated in a failure.
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
    // Checked on the literal start of the url; a url whose scheme or host comes from a variable is checked again once resolved.
    val written = (transport.url.parts.firstOrNull() as? McpTextPart.Literal)?.text.orEmpty()
    val sendsSecret = transport.headers.values.any { it !is McpHeader.Text }
    written.urlProblem()?.let { fail(id, "has a 'url' that $it.") }
    if (sendsSecret && !written.isHttps()) fail(id, "sends a secret header, so its 'url' must start with 'https://' as written, never plain http or a variable.")
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
 * Returns why this url, or the start of one, may not be written, or `null` when it may: it must be `scheme://` followed by a host without a user or password, with no backslash anywhere. A start that holds no `:` yet, because a variable follows, is judged once resolved.
 */
// A WHATWG parser, which Claude Code, VS Code and Cursor use, reads 'https:/user:pw@host' and backslashes as the two slashes, so anything but the plain form is refused rather than second-guessed.
internal fun String.urlProblem(): String? = when {
    '\\' in this -> "holds a backslash, which some tools read as a slash"
    ':' !in this -> null
    !URL_START.containsMatchIn(this) -> "is not a scheme followed by '://' and a host"
    '@' in substringAfter("://").takeWhile { it !in "/?#" } -> "carries credentials, which every tool would write into its config file and send. Pass them as a secret header instead"
    else -> null
}

private val URL_START = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://[^/]""")

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
