package cz.cleanship.aitools.engine.models

/**
 * An MCP server as the loader produces it from an [McpServerManifest]: every text field split into literal text and references to declared variables, and every header already classified as text, a whole secret, or a bearer secret.
 *
 * It is built once, by [cz.cleanship.aitools.engine.services.McpServerReader], and nothing downstream parses `${NAME}` text again, so text taken from a `server.json` can never turn into a reference. Immutable.
 *
 * @property transport how a tool starts or reaches the server
 * @property variables the variables the server needs, in the order they are rendered; a stdio server receives every one of them in its environment under its own name
 * @property pointer whether the server is derived from the `server.json` of a pointer manifest, whose author can change only which package or remote `select` picks, which file `source` names, and the `env_vars` of the config files
 */
data class McpServer(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val transport: McpServerTransport,
    val variables: List<McpVariable>,
    val pointer: Boolean = false,
) : VersionedManifest

/**
 * How a tool starts or reaches an [McpServer].
 */
sealed interface McpServerTransport {

    /**
     * A server a tool starts as a local process.
     *
     * @property command the executable, with every variable of the run and a leading `~` already resolved, the way the `source` of a pointer skill is
     * @property args the arguments passed to [command]
     * @property env the fixed environment entries, by variable name, passed besides the declared variables of the server
     */
    data class Stdio(
        val command: String,
        val args: List<McpText>,
        val env: Map<String, McpText>,
    ) : McpServerTransport

    /**
     * A remote server a tool reaches over Streamable HTTP.
     *
     * @property url the endpoint
     * @property headers the headers by name, in the order they are rendered
     */
    data class Http(
        val url: McpText,
        val headers: Map<String, McpHeader>,
    ) : McpServerTransport
}

/**
 * A text value made of literal text and references to declared variables.
 *
 * No [McpTextPart.Literal] holds `$`, because every tool the engine renders for would expand `${`, and Copilot CLI expands a bare `$NAME` as well.
 */
data class McpText(val parts: List<McpTextPart>) {
    init {
        require(parts.none { it is McpTextPart.Literal && EXPANSION_SIGN in it.text }) { "A literal part of an MCP text holds '$EXPANSION_SIGN'." }
    }

    /** The name of every variable this text references, in order. */
    val variableNames: List<String> get() = parts.filterIsInstance<McpTextPart.Variable>().map { it.name }

    companion object {
        /** The text that opens a variable reference in every tool the engine renders for. */
        const val REFERENCE_OPENER = "\${"

        /** The sign that opens a variable reference in Copilot CLI, which expands `$NAME` as well as `${NAME}`; a literal part may therefore not hold it. */
        const val EXPANSION_SIGN = "$"

        /**
         * Returns the text in [text] that some tool the engine renders for would expand as the start of a reference to a variable of its own environment: [REFERENCE_OPENER] when [text] holds it, otherwise [EXPANSION_SIGN] when it holds that, otherwise `null`.
         */
        fun expansionIn(text: String): String? = when {
            REFERENCE_OPENER in text -> REFERENCE_OPENER
            EXPANSION_SIGN in text -> EXPANSION_SIGN
            else -> null
        }

        /** Returns a text holding only [text]. */
        fun literal(text: String) = McpText(if (text.isEmpty()) emptyList() else listOf(McpTextPart.Literal(text)))
    }
}

/**
 * One part of an [McpText].
 */
sealed interface McpTextPart {

    /** Text written as it is. */
    data class Literal(val text: String) : McpTextPart

    /** A reference to the declared variable [name]. */
    data class Variable(val name: String) : McpTextPart
}

/**
 * The value of one header of an http MCP server.
 */
sealed interface McpHeader {

    /** A header whose value is text; it references no secret variable. */
    data class Text(val text: McpText) : McpHeader

    /** A header whose whole value is the secret variable [variable]. */
    data class Secret(val variable: String) : McpHeader

    /** An `Authorization` header whose value is `Bearer ` followed by the secret variable [variable]. */
    data class BearerSecret(val variable: String) : McpHeader

    companion object {
        /** The only header a [BearerSecret] may be sent as. */
        const val AUTHORIZATION = "Authorization"

        private const val BEARER_PREFIX = "Bearer "

        /**
         * Returns the header [name] with the value [text], classified by the secret variables [secrets]: a whole secret, a bearer secret of `Authorization`, or text.
         *
         * The result is a [Text] when [text] references a secret variable in any other way; the caller rejects such a header, because no tool other than those expanding `${NAME}` could pass that secret by name.
         */
        fun classify(name: String, text: McpText, secrets: Set<String>): McpHeader {
            val parts = text.parts
            val whole = (parts.singleOrNull() as? McpTextPart.Variable)?.name?.takeIf { it in secrets }
            val bearer = parts
                .takeIf { name.equals(AUTHORIZATION, ignoreCase = true) && it.size == 2 && it.first() == McpTextPart.Literal(BEARER_PREFIX) }
                ?.let { (it.last() as? McpTextPart.Variable)?.name }
                ?.takeIf { it in secrets }
            return when {
                whole != null -> Secret(whole)
                bearer != null -> BearerSecret(bearer)
                else -> Text(text)
            }
        }
    }
}
