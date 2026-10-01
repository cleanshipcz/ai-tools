package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.env.ENVIRONMENT_VARIABLE_NAME
import cz.cleanship.aitools.engine.env.UnexpandedReferenceException
import cz.cleanship.aitools.engine.env.UnresolvedVariableException
import cz.cleanship.aitools.engine.env.VARIABLE_REFERENCE
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.env.VariableSubstitutionException
import cz.cleanship.aitools.engine.io.resolveDeclaredPath
import cz.cleanship.aitools.engine.launcher.McpLauncherContract
import cz.cleanship.aitools.engine.models.McpHeader
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerManifest
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpSourceSelection
import cz.cleanship.aitools.engine.models.McpText
import cz.cleanship.aitools.engine.models.McpTextPart
import cz.cleanship.aitools.engine.models.McpTransport
import cz.cleanship.aitools.engine.models.McpVariable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Turns an [McpServerManifest] into the [McpServer] every later step of a run works with: an inline server from its own transport, a pointer server from the `server.json` its [McpServerManifest.source] names.
 *
 * @param variables the variables of the run a `source` and a stdio `command` are substituted with, as the `source` of a pointer skill is - see [VariableResolver]. The default declares none and falls back to the environment of the process.
 * @param userHome the directory a leading `~` of a substituted `source` or `command` stands for. The default, and the only value a run uses, is the home directory of the user running the engine, whatever `--user-home` the run deploys the user scope to.
 */
class McpServerReader(
    private val variables: VariableResolver = VariableResolver(),
    private val userHome: File = File(System.getProperty("user.home")),
) {

    /**
     * Returns the server [manifest] describes.
     *
     * For an inline server, every `${NAME}` in `args`, `env`, `url` and `headers` must name a variable the manifest declares, and becomes a reference to it; any other `$` fails. The `command` is a path: it may reference only variables of the run, and after substitution a `~` or leading `~/` stands for the home directory of this reader; a relative command is kept as written, for the tool to look up, and it fails when it then holds a `$`.
     *
     * A pointer server is derived from its `server.json`, which is read once: when the manifest declares a `pin`, the SHA-256 hash of the bytes read must equal it, and when it declares none, a warning prints the value to pin. The names of the variables it passes and the environment variables it sets are logged, never their values. Its text is data: a `{name}` placeholder the file defines becomes a variable named after the manifest id and the placeholder, such as `GITHUB_TOKEN`; an environment variable of a package keeps its own name, and so does one whose value is a single placeholder; a header without a value becomes a variable named after the id and the header, such as `GITHUB_AUTHORIZATION`; a positional argument with only a `valueHint` becomes a variable named after the id and the hint. A variable is secret when its input or any input around it is marked `isSecret`. An `npm` package starts with `npx -y`, a `pypi` package with `uvx`, and an `oci` package with `docker run -i --rm`, naming every environment variable with `-e`.
     *
     * @param manifestFile the file [manifest] was read from, whose directory a relative `source` resolves against
     * @throws InvalidMcpServerManifestException if a pointer server declares a `pin` not of the form [McpServerManifest.pin] describes; if an inline server lacks a transport or a description, declares a `select` or a `pin`, or references anything but a declared variable; if its `command` references a declared variable or a variable of the run nothing declares; or if the server, inline or derived, breaks a rule of [requireValid]
     * @throws InvalidMcpServerSourceException if a pointer server also declares a `description`, `transport` or `variables`; if its `source` cannot be substituted or leads to no readable `server.json`; if the hash of that file is not its `pin`, naming the file, the pinned and the actual hash; if that file is not valid JSON, is not of the schema [SUPPORTED_SCHEMA], has a blank `description`, or declares no package and no remote; if `select` names both, names one the file does not declare, or is missing while the file declares more than one; if the selected remote is not Streamable HTTP, or the selected package is not a stdio package of `npm`, `pypi` or `oci`; if its `runtimeHint` is not the runner of its registry; if its identifier or version does not follow the grammar of its registry - see [PackageRegistry]; if it has a runtime argument other than `-e NAME` of an `oci` package; if it sets or forwards an environment variable, derives a variable, or sends a header, of the names the engine refuses; if a named argument has no value or a positional one has neither a value nor a value hint; if its text holds `$`; or if it derives one variable both as secret and as not secret
     */
    fun read(manifest: McpServerManifest, manifestFile: File): McpServer {
        val server = if (manifest.source == null) readInline(manifest) else readPointer(manifest, manifestFile)
        server.requireValid()
        return server
    }

    private fun readInline(manifest: McpServerManifest): McpServer {
        manifest.requireInlineShape()
        requireVariableNames(manifest.id, manifest.variables)
        val declared = manifest.variables.mapTo(mutableSetOf()) { it.name }
        val secrets = manifest.variables.filter { it.secret }.mapTo(mutableSetOf()) { it.name }

        fun text(value: String, place: String) = parseText(manifest.id, value, place, declared)
        val transport = when (val declaredTransport = requireNotNull(manifest.transport)) {
            is McpTransport.Stdio -> McpServerTransport.Stdio(
                command = resolveCommand(manifest.id, declaredTransport.command, declared),
                args = declaredTransport.args.map { text(it, "'args'") },
                env = declaredTransport.env.mapValues { (key, value) -> text(value, "'env.$key'") },
            )
            is McpTransport.Http -> McpServerTransport.Http(
                url = text(declaredTransport.url, "'url'"),
                headers = declaredTransport.headers.mapValues { (name, value) -> McpHeader.classify(name, text(value, "the header '$name'"), secrets) },
            )
        }
        return McpServer(manifest.id, manifest.description, manifest.metadata, transport, manifest.variables)
    }

    /**
     * Resolves a stdio command the way the `source` of a pointer skill is resolved, without making a relative command absolute.
     */
    private fun resolveCommand(serverId: String, command: String, declared: Set<String>): String {
        val ownVariable = VARIABLE_REFERENCE.findAll(command).map { it.groupValues[1] }.firstOrNull { it in declared }
        val substituted = if (ownVariable == null) substituteCommand(serverId, command) else null
        val problem = when {
            ownVariable != null ->
                "MCP server '$serverId' references its declared variable '$ownVariable' in 'command'. The command is a path, resolved like the 'source' of a pointer: it may reference only variables of the run, from 'env_vars:' or the environment of the run."
            substituted == null || McpText.REFERENCE_OPENER in substituted ->
                "MCP server '$serverId' holds a '${McpText.REFERENCE_OPENER}' in 'command' that is not a variable of the run in the form '${McpText.REFERENCE_OPENER}NAME}', which a tool would expand."
            else -> return requireNoExpansionSign(serverId, if (substituted == HOME || substituted.startsWith("$HOME/")) File(".").resolveDeclaredPath(substituted, userHome).absolutePath else substituted)
        }
        throw InvalidMcpServerManifestException(problem)
    }

    // Checked once a leading '~' is resolved too, since the home directory is part of the command as written.
    private fun requireNoExpansionSign(serverId: String, command: String): String {
        if (McpText.EXPANSION_SIGN in command) {
            throw InvalidMcpServerManifestException(
                "MCP server '$serverId' holds a '${McpText.EXPANSION_SIGN}' in 'command' once the variables of the run and a leading '~' are resolved, which Copilot CLI would expand as it expands '${McpText.EXPANSION_SIGN}NAME'. Use a command whose path holds no '${McpText.EXPANSION_SIGN}'.",
            )
        }
        return command
    }

    private fun substituteCommand(serverId: String, command: String): String = try {
        variables.substitute(command, origin = "'command' of MCP server '$serverId'")
    } catch (ex: VariableSubstitutionException) {
        throw InvalidMcpServerManifestException(substitutionProblem(serverId, "'command'", ex))
    }

    private fun readPointer(manifest: McpServerManifest, manifestFile: File): McpServer {
        requireNoInlineContent(manifest)
        manifest.pin?.let { requirePinForm(manifest.id, it) }
        val serverFile = resolveServerFile(manifest, requireNotNull(manifest.source), manifestFile)
        val serverJson = read(serverFile, manifest)
        if (serverJson.description.isBlank()) {
            throw InvalidMcpServerSourceException("'${serverFile.absolutePath}' provides no 'description', and a pointer server takes its description from there. Point 'source' at a $SERVER_FILE that describes the server.")
        }
        val derivation = Derivation(manifest.id, serverFile)
        val transport = when (val candidate = select(manifest, serverJson, serverFile)) {
            is Candidate.Package -> derivation.stdio(candidate.value)
            is Candidate.Remote -> derivation.http(candidate.value, candidate.label)
        }
        val server =
            McpServer(manifest.id, serverJson.description, manifest.metadata, transport, derivation.variables.values.toList(), pointer = true)
        logForwardedNames(server, serverFile)
        return server
    }

    /**
     * Names every variable a pointer server passes to its server and every environment variable its `server.json` sets, so the user sees what the external file asks of the tool; values are never logged.
     */
    private fun logForwardedNames(server: McpServer, serverFile: File) {
        val fixed = (server.transport as? McpServerTransport.Stdio)?.env?.keys.orEmpty()
        LOG.info(
            "MCP server '{}' is read from '{}': it passes the variables {} to the server and sets the environment variables {}.",
            server.id,
            serverFile.absolutePath,
            server.variables.map { it.name },
            fixed,
        )
    }

    private fun requireNoInlineContent(manifest: McpServerManifest) {
        val inline = listOfNotNull(
            "description".takeIf { manifest.description.isNotEmpty() },
            "transport".takeIf { manifest.transport != null },
            "variables".takeIf { manifest.variables.isNotEmpty() },
        )
        if (inline.isNotEmpty()) {
            throw InvalidMcpServerSourceException(
                "An MCP server declaring 'source' takes its description, transport and variables from its $SERVER_FILE, but this one also declares ${inline.joinToString { "'$it'" }}. Remove ${if (inline.size == 1) "it" else "them"}, or remove 'source'.",
            )
        }
    }

    private fun resolveServerFile(manifest: McpServerManifest, declared: String, manifestFile: File): File {
        val substituted = try {
            variables.substitute(declared, origin = "'source' of MCP server '${manifest.id}'")
        } catch (ex: VariableSubstitutionException) {
            throw InvalidMcpServerSourceException(substitutionProblem(manifest.id, "'source'", ex))
        }
        // The manifest's own directory is the base, as for a pointer skill: the pointer keeps naming the same server wherever the engine is started from.
        val resolved = manifestFile.absoluteFile.parentFile.resolveDeclaredPath(substituted, userHome)
        val serverFile = if (resolved.isDirectory) resolved.resolve(SERVER_FILE) else resolved
        val problem = when {
            !resolved.exists() -> "The 'source' '$declared' resolves to '${resolved.absolutePath}', which does not exist."
            !serverFile.isFile -> "The 'source' '$declared' holds no $SERVER_FILE: '${serverFile.absolutePath}' does not exist."
            else -> return serverFile
        }
        throw InvalidMcpServerSourceException(problem)
    }

    /**
     * Reads [serverFile] once, so the hash compared with the pin of [manifest] and the content decoded are the same bytes.
     */
    private fun read(serverFile: File, manifest: McpServerManifest): ServerJson {
        val bytes = try {
            serverFile.readBytes()
        } catch (ex: IOException) {
            throw InvalidMcpServerSourceException("'${serverFile.absolutePath}' cannot be read (${ex.javaClass.simpleName}).", ex)
        }
        requirePinned(manifest, serverFile, sha256(bytes))
        val serverJson = try {
            JSON.decodeFromString<ServerJson>(bytes.decodeToString())
        } catch (ex: IllegalArgumentException) {
            // A SerializationException is one. Its message quotes the file, which may hold a token, so only the offset it names is repeated.
            throw InvalidMcpServerSourceException("'${serverFile.absolutePath}' is not a valid $SERVER_FILE${ex.jsonOffset()}.", MessageWithheldException(ex))
        }
        return serverJson.also { requireSupportedSchema(it, serverFile) }
    }

    /**
     * Fails when [manifest] pins another hash than [actual], the hash of [serverFile]; warns printing [actual] when it pins none. A hash is not a secret, so both are named.
     */
    private fun requirePinned(manifest: McpServerManifest, serverFile: File, actual: String) {
        val pin = manifest.pin
        if (pin == null) {
            LOG.warn(
                "MCP server '{}' reads '{}' without a pin, so any change to that file changes what every tool starts. Review the file, then add 'pin: {}' to the manifest to refuse any other content.",
                manifest.id,
                serverFile.absolutePath,
                actual,
            )
            return
        }
        if (pin != actual) {
            throw InvalidMcpServerSourceException(
                "'${serverFile.absolutePath}' has the hash '$actual', but the manifest pins '$pin'. The file changed since it was pinned: review the change, then set 'pin' to the new hash.",
            )
        }
    }

    private fun requireSupportedSchema(serverJson: ServerJson, serverFile: File) {
        val problem = when (serverJson.schema) {
            SUPPORTED_SCHEMA -> return
            null -> "'${serverFile.absolutePath}' declares no '\$schema'. This engine reads only $SERVER_FILE files of the schema '$SUPPORTED_SCHEMA'."
            else -> "'${serverFile.absolutePath}' declares the schema '${serverJson.schema}', but this engine reads only '$SUPPORTED_SCHEMA'. Point 'source' at a $SERVER_FILE of that schema."
        }
        throw InvalidMcpServerSourceException(problem)
    }

    private fun select(manifest: McpServerManifest, serverJson: ServerJson, serverFile: File): Candidate {
        val candidates = serverJson.packages.map { Candidate.Package(it) } + serverJson.remotes.mapIndexed { index, remote -> Candidate.Remote(remote, index + 1) }
        val selection = manifest.select
        val selected = when {
            selection == null -> candidates.singleOrNull()
            selection.packageIdentifier != null && selection.remote != null -> null
            selection.packageIdentifier != null -> candidates.filterIsInstance<Candidate.Package>().firstOrNull { it.value.identifier == selection.packageIdentifier }
            else -> candidates.filterIsInstance<Candidate.Remote>().firstOrNull { it.value.url == selection.remote }
        }
        return selected ?: throw InvalidMcpServerSourceException(selectionProblem(selection, candidates, serverFile.absolutePath))
    }

    private fun selectionProblem(selection: McpSourceSelection?, candidates: List<Candidate>, path: String): String {
        val declared = candidates.joinToString { it.label }
        return when {
            candidates.isEmpty() -> "'$path' declares no package and no remote, so there is no server to start or reach."
            selection == null -> "'$path' declares ${candidates.size} ways to run the server: $declared. Pick one with 'select:' and 'package: <identifier>' or 'remote: <url>'."
            selection.packageIdentifier != null && selection.remote != null -> "'select' names both a package and a remote. Name either 'package' or 'remote'."
            selection.packageIdentifier != null -> "'select' names the package '${selection.packageIdentifier}', which '$path' does not declare. It declares: $declared."
            // The url is not repeated: a remote url may carry credentials.
            selection.remote != null -> "'select' names a remote url that '$path' does not declare. It declares: $declared."
            else -> "'select' names neither a package nor a remote. Name either 'package' or 'remote'."
        }
    }

    /**
     * Builds the transport and variables of one pointer server from its `server.json`, naming the variables it derives after [serverId].
     */
    private class Derivation(private val serverId: String, private val serverFile: File) {

        val variables = LinkedHashMap<String, McpVariable>()

        fun stdio(pkg: PackageJson): McpServerTransport.Stdio {
            val registry = PackageRegistry.of(pkg.registryType) ?: fail(
                "The package '${pkg.identifier}' comes from the registry '${pkg.registryType}', which this engine cannot start. Select an npm, pypi or oci package, or a remote.",
            )
            val runner = registry.runner
            if (pkg.transport.type != STDIO) {
                fail("The package '${pkg.identifier}' is a '${pkg.transport.type}' server started locally, which this engine does not support. Select a stdio package or a remote.")
            }
            if (pkg.runtimeHint != null && pkg.runtimeHint != runner) {
                fail("The package '${pkg.identifier}' names the runtime '${pkg.runtimeHint}', but a ${pkg.registryType} package is started only with '$runner'.")
            }
            val runtimeArgs = pkg.runtimeArguments.flatMap { runtimeArgument(pkg, it) }
            val packageArgs = pkg.packageArguments.flatMap(::packageArgument)
            val env = LinkedHashMap<String, McpText>()
            pkg.environmentVariables.forEach { environmentVariable(it, env) }
            val identifier = McpText.literal(reference(registry, pkg))
            val args = when (registry) {
                PackageRegistry.NPM -> listOf(McpText.literal("-y"), identifier)
                PackageRegistry.PYPI -> listOf(identifier)
                // docker hands the container only the variables named with -e, reading each value from its own environment.
                else -> listOf("run", "-i", "--rm").map { McpText.literal(it) } + runtimeArgs +
                    pkg.environmentVariables.flatMap { listOf(McpText.literal("-e"), McpText.literal(it.name)) } + identifier
            }
            return McpServerTransport.Stdio(command = runner, args = args + packageArgs, env = env)
        }

        fun http(remote: RemoteJson, label: String): McpServerTransport.Http {
            if (remote.type != STREAMABLE_HTTP) {
                fail("The $label is a '${remote.type}' endpoint, but the engine renders only Streamable HTTP remotes, the one kind every supported tool reads. Select another remote or a package.")
            }
            remote.headers.firstOrNull { header -> DENIED_HEADERS.any { it.matches(header.name) } }?.let {
                fail("The $label sends the header '${it.name}', which controls the connection or overrides authentication; the engine sends only 'Authorization' and headers of the server itself.")
            }
            remote.headers.forEach { requireNoExpansionSign(it.name, "a header of the remote") }
            val url = template(remote.url, remote.variables, outerSecret = false, place = "the url of the remote")
            val headers = remote.headers.associate { header ->
                header.name to if (header.value == null) {
                    val name = prefixed(header.name)
                    add(McpVariable(name, header.description.orEmpty(), header.isSecret, header.isRequired))
                    McpText(listOf(McpTextPart.Variable(name)))
                } else {
                    template(header.value, header.variables, outerSecret = header.isSecret, place = "the header '${header.name}'")
                }
            }
            // Classified once every variable is known, so a header is never judged by a secrecy a later derivation would have changed.
            val secrets = variables.values.filter { it.secret }.mapTo(mutableSetOf()) { it.name }
            return McpServerTransport.Http(url = url, headers = headers.mapValues { (name, text) -> McpHeader.classify(name, text, secrets) })
        }

        private fun runtimeArgument(pkg: PackageJson, argument: ArgumentJson): List<McpText> {
            val forwarded = argument.value?.takeIf { pkg.registryType == PackageRegistry.OCI.registryType && argument.type == NAMED && argument.name == "-e" && ENVIRONMENT_VARIABLE_NAME.matches(it) }
                ?: fail(
                    "The package '${pkg.identifier}' declares the runtime argument '${argument.name ?: argument.valueHint ?: argument.type}'. The engine accepts runtime arguments only for an oci package, and only '-e NAME', which forwards one environment variable into the container.",
                )
            requireAllowedName(forwarded, derivedFromId = false)
            add(McpVariable(forwarded, argument.description.orEmpty(), argument.isSecret, argument.isRequired))
            return listOf(McpText.literal("-e"), McpText.literal(forwarded))
        }

        private fun packageArgument(argument: ArgumentJson): List<McpText> = when (argument.type) {
            NAMED -> {
                val name = argument.name ?: fail("A named package argument declares no 'name'.")
                val value = argument.value ?: fail(
                    "The named package argument '$name' has no 'value'. It would be written as a bare flag, and the value meant for it would land in the next slot of the command line.",
                )
                listOf(McpText.literal(literal(name, "the package argument '$name'")), template(value, argument.variables, argument.isSecret, "the package argument '$name'"))
            }
            POSITIONAL -> listOf(
                when {
                    argument.value != null -> template(argument.value, argument.variables, argument.isSecret, "a positional package argument")
                    argument.valueHint != null -> {
                        // A positional argument without a value is one the user provides, identified by its hint.
                        val name = prefixed(argument.valueHint)
                        add(McpVariable(name, argument.description.orEmpty(), argument.isSecret, argument.isRequired))
                        McpText(listOf(McpTextPart.Variable(name)))
                    }
                    else -> fail("A positional package argument has neither 'value' nor 'valueHint', so there is nothing to put into its slot of the command line.")
                },
            )
            else -> fail("A package argument has the type '${argument.type}', which is neither 'named' nor 'positional'.")
        }

        private fun environmentVariable(input: KeyValueInputJson, env: MutableMap<String, McpText>) {
            requireNoExpansionSign(input.name, "an environment variable of the package")
            requireAllowedName(input.name, derivedFromId = false)
            val single = input.value
                ?.let { PLACEHOLDER.matchEntire(it) }
                ?.groupValues
                ?.get(1)
                ?.let { input.variables[it] }
            when {
                input.value == null -> add(McpVariable(input.name, input.description.orEmpty(), input.isSecret, input.isRequired))
                // A value that is one variable and nothing else is that variable, so it keeps the name of the environment variable, which is what lets every tool pass a secret one by name.
                single != null -> add(McpVariable(input.name, single.description ?: input.description.orEmpty(), single.isSecret || input.isSecret, single.isRequired))
                else -> env[input.name] = template(input.value, input.variables, input.isSecret, "the environment variable '${input.name}'")
            }
        }

        /**
         * Splits [value] into literal text and references to the variables [inputs] defines, each secret when it or the input around it is marked secret.
         */
        private fun template(
            value: String,
            inputs: Map<String, InputJson>,
            outerSecret: Boolean,
            place: String,
        ): McpText {
            val parts = mutableListOf<McpTextPart>()
            var index = 0
            for (match in PLACEHOLDER.findAll(value)) {
                val input = inputs[match.groupValues[1]] ?: continue
                if (match.range.first > index) parts += McpTextPart.Literal(literal(value.substring(index, match.range.first), place))
                val name = prefixed(match.groupValues[1])
                add(McpVariable(name, input.description.orEmpty(), input.isSecret || outerSecret, input.isRequired))
                parts += McpTextPart.Variable(name)
                index = match.range.last + 1
            }
            if (index < value.length) parts += McpTextPart.Literal(literal(value.substring(index), place))
            return McpText(parts)
        }

        /**
         * Returns [text] of the file, refusing text a tool would expand as a reference to a variable of its own environment: `${` in every tool, and any `$` in Copilot CLI.
         */
        private fun literal(text: String, place: String): String {
            McpText.expansionIn(text)?.let {
                fail("It holds '$it' in $place, which a tool would expand as a reference to a variable of its own environment, so the engine refuses to write it.")
            }
            return text
        }

        /**
         * Fails when [name], a name the file gives a header or an environment variable, holds a `$`, which Copilot CLI may expand; the name is never repeated.
         */
        private fun requireNoExpansionSign(name: String, place: String) {
            if (McpText.EXPANSION_SIGN in name) {
                fail("The name of $place holds '${McpText.EXPANSION_SIGN}', which Copilot CLI may expand as a reference to a variable of its own environment, so the engine refuses to write it.")
            }
        }

        private fun add(variable: McpVariable) {
            val existing = variables[variable.name]
            when {
                existing == null -> variables[variable.name] = variable
                existing.secret != variable.secret -> fail("It derives the variable '${variable.name}' twice, once as a secret and once not, so the engine cannot tell whether its value may be written.")
                else -> variables[variable.name] = existing.copy(required = existing.required || variable.required)
            }
        }

        /**
         * Returns the argument the runner installs [pkg] from, having checked its identifier and version against the grammar of [registry]; neither is repeated in a failure, since either may be crafted.
         */
        private fun reference(registry: PackageRegistry, pkg: PackageJson): String {
            val version = pkg.version?.takeIf { it.isNotBlank() }
            if (!registry.accepts(pkg.identifier)) {
                fail("The identifier of the ${registry.registryType} package is not a ${registry.grammar.substringBefore(" and ")}, or starts with '-', so the engine refuses to put it on the command line of '${registry.runner}'.")
            }
            if (version != null && !registry.acceptsVersion(version)) {
                fail("The version of the ${registry.registryType} package '${pkg.identifier}' does not follow the ${registry.grammar}, so the engine refuses to put it on the command line of '${registry.runner}'.")
            }
            return registry.reference(pkg.identifier, version)
        }

        /**
         * Refuses an environment variable name the launcher refuses as a secret name, of either class - see [McpLauncherContract.refusedNameOf] - which covers every name that configures a shell, the launcher and its helpers, the runner, the loader, an interpreter or the connection of the process a tool starts.
         *
         * @param derivedFromId whether [name] was built from the manifest id, which renaming the id changes
         */
        // Both classes, for every name a server.json contributes, secret or not: the file is not written by the owner of the repository, and a name of class B still lets it change what the runtime of the server loads or where it connects, such as NODE_OPTIONS or HTTPS_PROXY.
        private fun requireAllowedName(name: String, derivedFromId: Boolean) {
            val pattern = McpLauncherContract.refusedPatternOf(name) ?: return
            fail(
                "It sets, forwards or derives the environment variable '$name', which matches '$pattern' of the names the engine refuses in a $SERVER_FILE without regard to case: ${McpLauncherContract.REFUSAL_REASON}. " +
                    if (derivedFromId) {
                        "Select another package or remote of that file with 'select:', if it declares one, point 'source' at another $SERVER_FILE, or rename the manifest id, which the name is derived from."
                    } else {
                        "Select another package or remote of that file with 'select:', if it declares one, or point 'source' at another $SERVER_FILE."
                    },
            )
        }

        /**
         * Returns the name of the variable derived from [key] of the file, named after [serverId], having checked it like every other environment variable name the file contributes.
         */
        private fun prefixed(key: String): String {
            val name = "${serverId}_$key".uppercase().replace(NOT_IN_NAME, "_")
            // The id alone is the repository's, but together with a key of the file it can spell a name such as GIT_SSH_COMMAND, and every variable of a stdio server reaches its environment.
            return (if (name.first().isDigit()) "_$name" else name).also { requireAllowedName(it, derivedFromId = true) }
        }

        private fun fail(problem: String): Nothing = throw InvalidMcpServerSourceException("'${serverFile.absolutePath}': $problem")
    }

    private sealed interface Candidate {
        val label: String

        data class Package(val value: PackageJson) : Candidate {
            override val label: String get() = "package '${value.identifier}'"
        }

        // Named by its position in the file, never by its url, which may carry credentials.
        data class Remote(val value: RemoteJson, val position: Int) : Candidate {
            override val label: String get() = "remote $position"
        }
    }

    companion object {
        /** The only version of the MCP Registry `server.json` schema this engine reads. */
        const val SUPPORTED_SCHEMA = "https://static.modelcontextprotocol.io/schemas/2025-12-11/server.schema.json"

        private const val SERVER_FILE = "server.json"
        private const val HOME = "~"
        private const val STDIO = "stdio"
        private const val STREAMABLE_HTTP = "streamable-http"
        private const val NAMED = "named"
        private const val POSITIONAL = "positional"

        /**
         * Header names a `server.json` may not send, matched without regard to case: hop-by-hop headers, and headers that override the host, the client address, the method or authentication other than `Authorization`.
         */
        private val DENIED_HEADERS = listOf(
            "Host",
            "Connection",
            "Keep-Alive",
            "Proxy-.*",
            "TE",
            "Trailer",
            "Transfer-Encoding",
            "Upgrade",
            "Content-Length",
            "Cookie",
            "Forwarded",
            "X-Forwarded-.*",
            "X-Real-IP",
            "X-HTTP-Method-Override",
            "X-Method-Override",
            "X-Original-URL",
            "X-Rewrite-URL",
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
        private val PLACEHOLDER = Regex("""\{([^{}]+)}""")
        private val NOT_IN_NAME = Regex("[^A-Z0-9_]")

        private val LOG = LoggerFactory.getLogger(McpServerReader::class.java)

        // Only the fields the engine renders are modelled; the registry schema has many more, and a newer file adding some must still load.
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Fails when [pin], the pin of the MCP server [serverId], is not of the form [McpServerManifest.pin] describes.
 *
 * @throws InvalidMcpServerManifestException naming [serverId], never repeating [pin]
 */
private fun requirePinForm(serverId: String, pin: String) {
    if (!PIN.matches(pin)) {
        throw InvalidMcpServerManifestException("MCP server '$serverId' declares a 'pin' that is not 'sha256:' followed by 64 lowercase hexadecimal digits, the form a pin is written in.")
    }
}

/**
 * Returns the pin of [bytes], the SHA-256 hash of them in the form [McpServerManifest.pin] describes.
 */
private fun sha256(bytes: ByteArray): String =
    PIN_PREFIX + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private const val PIN_PREFIX = "sha256:"

private val PIN = Regex("sha256:[0-9a-f]{64}")

/**
 * Returns the problem of a `source` or `command` whose variables cannot be substituted, naming the server, the field and the variable, never the value that came out.
 */
private fun substitutionProblem(
    serverId: String,
    field: String,
    ex: VariableSubstitutionException,
): String = when (ex) {
    is UnresolvedVariableException ->
        "MCP server '$serverId' references the variable '${ex.variable}' in $field, which neither 'env_vars:' of config.yml or config.local.yml nor the environment of the run declares."
    is UnexpandedReferenceException ->
        "MCP server '$serverId' leaves the reference '${ex.variable}' in $field after substituting its variables once. Declare every variable with a value that is already expanded."
}

/**
 * Returns ` at offset N` for the offset a JSON parse failure names, or nothing; the rest of its message quotes the input, which may hold a secret.
 */
internal fun Throwable.jsonOffset(): String = message?.let { OFFSET.find(it) }?.let { " at offset ${it.groupValues[1]}" }.orEmpty()

private val OFFSET = Regex("""offset (\d+)""")

/**
 * Stands in for the failure [original], whose message quotes input that may hold a secret: it names the class of [original] and keeps its stack trace, but neither its message nor [original] itself travel on.
 */
class MessageWithheldException(
    original: Throwable,
) : RuntimeException("${original.javaClass.name} (message withheld: it quotes the input)") {
    init {
        stackTrace = original.stackTrace
    }
}

/**
 * Thrown when the [McpServerManifest.source] of an MCP server cannot be turned into the server it points at. It travels wrapped in a [ManifestLoadingException], so the author is told which manifest to fix.
 */
class InvalidMcpServerSourceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
