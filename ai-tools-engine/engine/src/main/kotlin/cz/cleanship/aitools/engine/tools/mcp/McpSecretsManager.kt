package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.models.SecretsManagerKind
import java.io.File

/**
 * A secrets manager of the machine, which hands a stdio MCP server its secrets at the moment a tool starts it, so no MCP config file needs anything but their names.
 */
interface McpSecretsManager {

    /** The manager as a message names it, such as `the keyring`. */
    val label: String

    /**
     * Returns [transport], the stdio server [serverId] as resolved, changed so that a tool starts it through this manager, which supplies [secrets] and then starts the command of [transport] with its arguments unchanged.
     *
     * Every reference [transport] holds stays in the result, so a server still reads a secret the manager does not hold from the environment of the tool.
     *
     * @param secrets the secrets of the server this manager supplies, each an entry of the environment of [transport], in the order the manager reads them
     * @throws McpServerResolvingException naming the server when the manager cannot start it on this machine, such as a launcher that is missing or not executable; a message never names a value
     */
    fun launch(
        serverId: String,
        transport: ResolvedMcpTransport.Stdio,
        secrets: List<McpValue.Secret>,
    ): ResolvedMcpTransport.Stdio

    /**
     * Returns whether this manager holds the secret [name], without ever receiving its value; the answer for a name is the same for the whole run.
     */
    fun presenceOf(name: String): SecretPresence

    /**
     * Returns whether [value], which the environment of the tool holds for the secret [name], counts as set when this manager falls back to that environment.
     */
    fun countsAsSet(name: String, value: String): Boolean

    /**
     * Returns the command a user runs to store the secret [name] in this manager, which asks for the value rather than taking it as an argument.
     */
    fun storeCommand(name: String): String
}

/**
 * Whether a secrets manager holds a secret, as far as can be told without its value.
 */
sealed interface SecretPresence {

    /** The manager holds the secret, locked or not. */
    data object Stored : SecretPresence

    /** The manager holds no secret of that name. */
    data object Absent : SecretPresence

    /**
     * Whether the manager holds the secret cannot be told.
     *
     * @property reason why, as the end of a sentence, naming a program or a variable and never a value
     */
    data class Unknown(val reason: String) : SecretPresence
}

/**
 * Returns the secrets manager this kind names for the repository [repositoryRoot], the directory holding the `config.yml` of the run, or `null` for [SecretsManagerKind.ENVIRONMENT], which reads every secret from the environment of the tool.
 *
 * @param environment the environment of the run, the one its config files were loaded with, where the programs the manager starts, the session bus and the fallback value of each secret are looked up
 */
fun SecretsManagerKind.managerFor(
    repositoryRoot: File,
    environment: EnvironmentSource,
): McpSecretsManager? = when (this) {
    SecretsManagerKind.LIBSECRET -> LibsecretSecretsManager(repositoryRoot, environment)
    SecretsManagerKind.ENVIRONMENT -> null
}
