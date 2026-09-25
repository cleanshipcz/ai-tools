package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An MCP server: how a tool starts or reaches it and which variables it needs, either declared in this manifest or, for a pointer server, derived from the `server.json` that [source] names.
 *
 * The id is the name of the server entry in every MCP config file the engine writes, so the tools of the server are named `mcp__<id>__<tool>` in Claude Code.
 */
@Serializable
data class McpServerManifest(
    override val id: String,
    /**
     * What the server provides.
     *
     * For a pointer server, it is the `description` of its `server.json`. A server returned by [cz.cleanship.aitools.engine.services.LoaderService.loadMcpServer] always has a description that is not blank.
     */
    override val description: String = "", // Optional in YAML only because a pointer server takes its description from server.json; the loader still rejects an inline server that declares none.
    override val metadata: ManifestMetadata,
    /**
     * How a tool starts or reaches the server.
     *
     * An inline server declares it; for a pointer server, the loader derives it from the package or remote [select] picks. A server returned by [cz.cleanship.aitools.engine.services.LoaderService.loadMcpServer] always has one.
     */
    val transport: McpTransport? = null,
    /**
     * The variables the server needs, in the order they are rendered.
     *
     * A stdio server receives every one of them in its environment under its own name; an http server receives those its `url` and `headers` reference as `${NAME}`. For a pointer server, the loader derives them from the selected package or remote.
     */
    val variables: List<McpVariable> = emptyList(),
    /**
     * The `server.json` in the MCP Registry schema this server is derived from, as the file itself or the folder holding it, or `null` for a server declared entirely in this manifest.
     *
     * It may reference the variables of the run, such as `${PROJECTS_FOLDER}`. After substitution, a path that is `~` or starts with `~/` resolves against the home directory of the user running the engine, and any other relative path against the directory of the manifest file. The loader rejects a manifest that declares it together with `description`, `transport` or `variables`.
     */
    val source: String? = null,
    /**
     * Which package or remote of the `server.json` of [source] to use, or `null` to use the only one it declares.
     *
     * The loader rejects it on a server without a [source].
     */
    val select: McpSourceSelection? = null,
) : VersionedManifest

/**
 * How a tool starts or reaches an MCP server.
 *
 * The YAML key `type` picks the kind: `stdio` for [Stdio] or `http` for [Http]. Any other value, such as `sse`, fails the load with a [cz.cleanship.aitools.engine.services.ManifestLoadingException] naming the file.
 *
 * Every text field other than a stdio `command` may reference only declared [McpVariable]s, as `${NAME}`; any other `${`, tool syntax such as `${NAME:-default}` or `${env:NAME}` included, fails the load. A stdio `command` is a path and may reference only variables of the run.
 */
@Serializable
sealed class McpTransport {

    /**
     * A server the tool starts as a local process and talks to over its standard input and output.
     *
     * @property command the executable: `${NAME}` is a variable of the run, from `env_vars` of the config files or the environment of the run, never a declared variable; a bare `~` or a leading `~/` is expanded against the home directory of the user running the engine, whatever `--user-home` the run uses; and a relative command, such as `uvx`, is kept as written, for the tool to find on its `PATH`
     * @property args the arguments passed to [command]; none may reference a secret variable
     * @property env fixed environment variables passed to the server besides its declared variables; no key may be the name of a declared variable, and no value may reference a secret one
     */
    @Serializable
    @SerialName("stdio")
    data class Stdio(
        val command: String,
        val args: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
    ) : McpTransport()

    /**
     * A remote server the tool reaches over Streamable HTTP.
     *
     * @property url the endpoint, which starts with `http://` or `https://`, in any case, and a host; a url that starts with a variable must resolve to one that does. It may not reference a secret variable
     * @property headers the headers sent with every request; a secret variable may be referenced only as a whole value, `${NAME}`, or as the bearer token of `Authorization`, `Bearer ${NAME}`
     */
    @Serializable
    @SerialName("http")
    data class Http(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : McpTransport()
}

/**
 * A variable an MCP server needs, which a generated MCP config file either holds resolved or, for a secret one, references by name only.
 *
 * @property name the name of the environment variable, matching `[A-Za-z_][A-Za-z0-9_]*`
 * @property description what the variable holds
 * @property secret whether the value is a secret: a secret variable is never read by the engine and is rendered as a reference each tool resolves from its own environment; any other variable is read from `env_vars` of the config files only, never from the environment of the run, and written as its value
 * @property required whether a server cannot run without it: a required variable that is not secret and that `env_vars` does not declare fails the MCP config files of every deployment that selects the server, while an optional one is left out, unless it is used in `args` or `url`, which fails the same way. It defaults to `true`
 */
@Serializable
data class McpVariable(
    val name: String,
    val description: String,
    val secret: Boolean,
    val required: Boolean = true,
)

/**
 * The package or remote of a `server.json` a pointer MCP server uses; exactly one of the two is set.
 *
 * @property packageIdentifier the `identifier` of the package, written `package` in YAML
 * @property remote the `url` of the remote
 */
@Serializable
data class McpSourceSelection(
    @SerialName("package")
    val packageIdentifier: String? = null,
    val remote: String? = null,
)
