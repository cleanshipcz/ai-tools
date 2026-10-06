package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.launcher.McpLauncherContract
import cz.cleanship.aitools.engine.process.ProgramInvocation
import cz.cleanship.aitools.engine.process.ProgramOutcome
import cz.cleanship.aitools.engine.process.ProgramRunner
import cz.cleanship.aitools.engine.process.SystemProgramRunner
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.time.Duration

/**
 * Tells whether the libsecret keyring holds an item for a secret, through the `SearchItems` call of the freedesktop secret service, which answers item paths and never a value.
 *
 * Once an answer cannot be told, every later one of this instance is that same answer, without starting anything again. Not thread-safe.
 *
 * @param environment the environment of the run: its `PATH`, `DBUS_SESSION_BUS_ADDRESS` and `XDG_RUNTIME_DIR`
 * @param timeout how long one call may take before it counts as not answered
 * @param runner finds and starts `busctl`
 */
class SecretServiceProbe(
    private val environment: EnvironmentSource,
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val runner: ProgramRunner = SystemProgramRunner,
) {
    private var unavailable: SecretPresence.Unknown? = null

    /**
     * Returns whether the keyring holds an item with the attributes the launcher looks the secret [name] up with: [SecretPresence.Stored] for an unlocked or a locked one, [SecretPresence.Absent] for none, and [SecretPresence.Unknown] naming the reason when no session bus is known, `busctl` is not on the `PATH`, cannot be started, fails, answers in another form, or does not answer within [timeout].
     *
     * The call is made by `busctl`, found by [runner] in an absolute directory of the `PATH` of [environment] and started by it with a fixed argument list, only `PATH` and the bus variables in its environment, never `DISPLAY`, and the time limit [timeout]. Nothing is started while no session bus is known. Its output is parsed and never logged.
     *
     * @param name an environment variable name, which is all of the secret that is ever passed on
     */
    fun search(name: String): SecretPresence {
        unavailable?.let { return it }
        val presence = call(name)
        if (presence is SecretPresence.Unknown) unavailable = presence
        return presence
    }

    private fun call(name: String): SecretPresence {
        val bus = busEnvironment() ?: return SecretPresence.Unknown(NO_BUS)
        val path = environment.read(PATH)
        val busctl = runner.find(BUSCTL, path) ?: return SecretPresence.Unknown("$BUSCTL is not on the PATH")
        // Neither DISPLAY, which would let a client start a bus of its own, nor any secret of the run is named, so neither reaches the program.
        val invocation =
            ProgramInvocation(busctl, arguments(name), bus + listOfNotNull(path?.let { PATH to it }), timeout, MAX_OUTPUT_BYTES)
        return when (val outcome = runner.run(invocation)) {
            is ProgramOutcome.NotStarted -> SecretPresence.Unknown("$BUSCTL could not be started (${outcome.failure})")
            ProgramOutcome.TimedOut -> SecretPresence.Unknown("$BUSCTL did not answer within ${timeout.seconds} seconds")
            is ProgramOutcome.Finished ->
                if (outcome.exitCode != 0) {
                    SecretPresence.Unknown("$BUSCTL failed with exit code ${outcome.exitCode}")
                } else {
                    presenceIn(outcome.output) ?: SecretPresence.Unknown("$BUSCTL answered in a form the engine does not read")
                }
        }
    }

    private fun arguments(name: String): List<String> = listOf(
        "--user",
        "--no-pager",
        // An auto-start would start the keyring daemon for a dry run; a service that is not running is reported instead.
        "--auto-start=no",
        "--timeout=${timeout.seconds.coerceAtLeast(1)}",
        "--json=short",
        "call",
        "org.freedesktop.secrets",
        "/org/freedesktop/secrets",
        "org.freedesktop.Secret.Service",
        "SearchItems",
        "a{ss}",
        "2",
    ) + McpLauncherContract.keyringAttributes(name)

    /**
     * Returns the bus variables `busctl` is started with, or `null` when no session bus is known: `DBUS_SESSION_BUS_ADDRESS` is unset or empty and `XDG_RUNTIME_DIR/bus` is not a socket - the rule the launcher follows.
     */
    private fun busEnvironment(): Map<String, String>? {
        val address = environment.read(BUS_ADDRESS)?.takeIf { it.isNotEmpty() }
        val runtimeDirectory = environment.read(RUNTIME_DIRECTORY)?.takeIf { it.isNotEmpty() }
        if (address == null && runtimeDirectory?.let { isSocket(it) } != true) return null
        return buildMap {
            address?.let { put(BUS_ADDRESS, it) }
            runtimeDirectory?.let { put(RUNTIME_DIRECTORY, it) }
        }
    }

    // A bus that cannot be looked at - no such file, a path the file system cannot hold, a file system without unix attributes - is a bus that is not known, which is the whole answer; the launcher decides the same way.
    private fun isSocket(runtimeDirectory: String): Boolean = try {
        val mode = Files.getAttribute(Paths.get(runtimeDirectory, "bus"), "unix:mode") as Int
        mode and FILE_TYPE_MASK == SOCKET_TYPE
    } catch (ignored: IOException) {
        false
    } catch (ignored: InvalidPathException) {
        false
    } catch (ignored: UnsupportedOperationException) {
        false
    }

    /**
     * Returns the presence the JSON answer [output] of `SearchItems` states - two lists of item paths, unlocked and locked - or `null` when it is not such an answer.
     */
    private fun presenceIn(output: ByteArray): SecretPresence? {
        // Output that is not JSON is an answer in another form, which the caller reports; its text is never repeated, so the failure has nothing to carry.
        val answer = try {
            Json.parseToJsonElement(output.decodeToString()) as? JsonObject
        } catch (ignored: SerializationException) {
            null
        } ?: return null
        val type = (answer["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val lists = (answer["data"] as? JsonArray)?.map { it as? JsonArray ?: return null }
        if (type != SEARCH_ITEMS_TYPE || lists?.size != 2) return null
        return if (lists.any { it.isNotEmpty() }) SecretPresence.Stored else SecretPresence.Absent
    }

    companion object {
        /** The time limit of one call. */
        // Below the ten seconds Codex gives a server to start, so the check never waits longer than a start may take.
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(5)

        private const val BUSCTL = "busctl"
        private const val PATH = "PATH"
        private const val BUS_ADDRESS = "DBUS_SESSION_BUS_ADDRESS"
        private const val RUNTIME_DIRECTORY = "XDG_RUNTIME_DIR"
        private const val SEARCH_ITEMS_TYPE = "aoao"
        private const val MAX_OUTPUT_BYTES = 64 * 1024
        private const val FILE_TYPE_MASK = 0xF000
        private const val SOCKET_TYPE = 0xC000
        private const val NO_BUS = "no D-Bus session bus is known ($BUS_ADDRESS is not set and $RUNTIME_DIRECTORY/bus is not a socket)"
    }
}
