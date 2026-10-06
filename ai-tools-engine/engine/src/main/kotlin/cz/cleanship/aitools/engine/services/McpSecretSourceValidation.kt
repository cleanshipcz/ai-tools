package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.launcher.McpLauncherContract
import cz.cleanship.aitools.engine.launcher.RefusedNameClass
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpVariable
import cz.cleanship.aitools.engine.models.SecretSource

/**
 * Fails when a variable of this server declares where it is read from although it is not secret, or when this stdio server has a secret not declared `from: environment`, and so starts through the launcher on a machine with a secrets manager, and one of these holds: the launcher refuses the name of such a secret; a name of the environment of its entry is of [RefusedNameClass.LAUNCHER]; or the launcher refuses the id of the server.
 *
 * The environment of the entry is every variable of the server, secret or not, `from: environment` or not, and every key of `env`: the entry holds a reference to each secret and the value of each plain entry, and a tool puts them into the environment of the launcher, whose shell reads it before its first line runs.
 *
 * @throws InvalidMcpServerManifestException naming the server and the variable or `env` key and the pattern it matches, or the server alone for its id
 */
// Checked whatever secrets manager the machine uses, so a manifest that loads on one machine loads on every other.
internal fun McpServer.requireSecretSources() {
    val problem = variables.firstOrNull { !it.secret && it.from != null }?.let {
        "declares 'from' on the variable '${it.name}', which is not secret. Only a secret variable is read from the secrets manager or the environment of the tool; remove 'from', or mark the variable 'secret: true'."
    } ?: launcherProblem() ?: return
    throw InvalidMcpServerManifestException("MCP server '$id' $problem")
}

private fun McpServer.launcherProblem(): String? {
    val stdio = transport as? McpServerTransport.Stdio ?: return null
    val supplied = variables.filter { it.secret && it.source == SecretSource.MANAGER }
    if (supplied.isEmpty()) return null
    // On a machine without a secrets manager nothing reaches the launcher, so every message says what happens where one is used, and that the rule holds everywhere.
    val declares = if (pointer) "takes a secret variable from its server.json" else "declares a secret variable without 'from: environment'"
    return refusedSecretProblem(supplied) ?: refusedEntryProblem(stdio, declares) ?: refusedIdProblem(declares)
}

private fun McpServer.refusedIdProblem(declares: String): String? {
    if (McpLauncherContract.acceptsServerId(id)) return null
    return "$declares, so on a machine with a secrets manager the launcher is given the id of the server, and it accepts only an id that ${McpLauncherContract.SERVER_ID_TEXT}. " +
        EVERY_MACHINE + if (pointer) "Rename the manifest id." else "Rename the manifest id, or declare every secret variable 'from: environment'."
}

private fun McpServer.refusedSecretProblem(supplied: List<McpVariable>): String? {
    val (name, refused) = supplied.firstNotNullOfOrNull { variable -> McpLauncherContract.refusedNameOf(variable.name)?.let { variable.name to it } } ?: return null
    val declares = if (pointer) "takes the secret variable '$name' from its server.json" else "declares the secret variable '$name' without 'from: environment'"
    // Declaring only this secret 'from: environment' leaves a name of class A in the entry of a server that still starts through the launcher whenever another secret does.
    val remedy = when {
        pointer -> POINTER_REMEDY
        refused.nameClass == RefusedNameClass.SERVER || supplied.size == 1 -> "Rename the variable, or declare it 'from: environment'."
        else -> "Rename the variable, or declare every secret variable 'from: environment'."
    }
    return "$declares, so on a machine with a secrets manager the launcher reads it, and the launcher refuses every secret name matching '${refused.pattern}' without regard to case: ${McpLauncherContract.REFUSAL_REASON}. " +
        EVERY_MACHINE + remedy
}

private fun McpServer.refusedEntryProblem(stdio: McpServerTransport.Stdio, declares: String): String? {
    val entry = stdio.env.keys.map { it to "sets '$it' under 'env'" } + variables.map { it.name to it.declaration() }
    // A name of class B reaches the launcher's shell too, but neither that shell nor its helpers env, timeout and secret-tool read it; the server reads it, and the tool would give it to the server without the launcher as well.
    val (declaration, pattern) = entry.firstNotNullOfOrNull { (name, declaration) ->
        McpLauncherContract.refusedNameOf(name)?.takeIf { it.nameClass == RefusedNameClass.LAUNCHER }?.let { declaration to it.pattern }
    } ?: return null
    return "$declares, so on a machine with a secrets manager a tool starts it through the launcher, which receives the environment of its entry, and it $declaration, " +
        "which matches '$pattern' without regard to case: ${McpLauncherContract.LAUNCHER_ENVIRONMENT_REASON}. " +
        EVERY_MACHINE + if (pointer) POINTER_REMEDY else "Rename or remove it, or declare every secret variable 'from: environment'."
}

private fun McpVariable.declaration(): String = when {
    !secret -> "declares the plain variable '$name'"
    source == SecretSource.ENVIRONMENT -> "declares the secret variable '$name' 'from: environment'"
    else -> "declares the secret variable '$name'"
}

private const val EVERY_MACHINE = "This is checked on every machine, whatever secrets manager it uses. "
