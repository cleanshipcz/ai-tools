package cz.cleanship.aitools.engine.tools.mcp

import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The ordinary programs the fakes, the launcher and the probe server need, linked into every search path a test builds, so that `/usr/bin`, which holds the real `busctl` and `secret-tool`, is never on it.
 */
internal val ORDINARY_PROGRAMS = listOf("timeout", "awk", "cat", "env", "sleep")

/**
 * Returns the program [name] from the first absolute directory of the `PATH` of the test run that holds it as an executable regular file, or `null` when none does.
 */
internal fun locateProgram(name: String): Path? = System
    .getenv("PATH")
    .orEmpty()
    .split(File.pathSeparator)
    .filter { it.startsWith("/") }
    .map { Paths.get(it, name) }
    .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }

/**
 * Returns why a test that starts fake programs cannot run on this machine - `/bin/sh`, or one of [programs], is missing - or `null` when it can; a test passes it to an assumption, so it is skipped with that reason.
 */
internal fun missingProgramsReason(programs: List<String> = ORDINARY_PROGRAMS): String? {
    val missing = programs.filter { locateProgram(it) == null }
    return when {
        !Files.isExecutable(Paths.get("/bin/sh")) -> "the fake programs are shell scripts, and this machine has no /bin/sh"
        missing.isNotEmpty() -> "the fake programs need ${missing.joinToString(", ")} in an absolute directory of the PATH"
        else -> null
    }
}

/**
 * Writes [content] to [file], creating its directory, gives it and every directory this creates the mode `rwxr-xr-x`, and returns it.
 */
// The mode is set whole rather than added to, so a machine whose umask leaves files or directories group-writable builds the same tree; the engine refuses a launcher others may write, or one in a directory others may write.
internal fun installExecutable(file: Path, content: String): Path {
    val created = generateSequence(file.parent) { it.parent }.takeWhile { !it.exists() }.toList()
    file.parent.createDirectories()
    created.forEach { Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rwxr-xr-x")) }
    file.writeText(content)
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"))
    return file
}

/**
 * Installs the test resource `/[resource]` as the executable [file], with every key of [replacements] replaced by its value, and returns [file].
 */
internal fun installResource(resource: String, file: Path, replacements: Map<String, String> = emptyMap()): Path {
    val template = checkNotNull(FakePrograms::class.java.getResourceAsStream("/$resource")) { "missing test resource $resource" }.use { it.readBytes().decodeToString() }
    return installExecutable(file, replacements.entries.fold(template) { text, (key, value) -> text.replace(key, value) })
}

/**
 * Returns the records of the call log [log] that the fakes write: a record starts at a line beginning with [header] and holds the lines after it; empty when [log] does not exist.
 */
internal fun readCallLog(log: Path, header: String): List<Pair<String, List<String>>> {
    if (!log.exists()) return emptyList()
    val records = mutableListOf<Pair<String, MutableList<String>>>()
    log.readText().lines().filter { it.isNotEmpty() }.forEach { line ->
        if (line.startsWith(header)) records += line to mutableListOf() else records.last().second += line
    }
    return records
}

/**
 * Returns the recorded call of a fake that [lines], the lines of one record of a call log, describe: its `arg` lines and its `env` lines.
 */
internal fun recordedLookup(lines: List<String>) = RecordedLookup(
    arguments = lines.filter { it.startsWith("arg ") }.map { it.removePrefix("arg ") },
    environmentNames = lines.environmentNames(),
)

/**
 * Returns the names of the `env` lines among these lines of a call log.
 */
internal fun List<String>.environmentNames(): Set<String> = filter { it.startsWith("env ") }.map { it.removePrefix("env ") }.toSet()

/**
 * Creates the directory [directory] holding a socket named `bus`, as `XDG_RUNTIME_DIR` does while a session bus runs, and returns it.
 */
internal fun runtimeDirectoryWithBus(directory: Path): Path {
    directory.createDirectories()
    // Closing the channel leaves the socket file in place, which is all the engine and the launcher check.
    ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { it.bind(UnixDomainSocketAddress.of(directory.resolve("bus"))) }
    return directory
}

/** One call of a fake program: its arguments and the names of its environment variables. */
internal data class RecordedLookup(val arguments: List<String>, val environmentNames: Set<String>)

/**
 * A search path of fake programs inside [root], followed by links to the [ORDINARY_PROGRAMS], so that no test ever reaches the real `busctl`, `secret-tool`, keyring, bus or display.
 *
 * A test that builds one first skips itself with [missingProgramsReason] when this machine lacks what the fakes need.
 */
internal class FakePrograms(private val root: Path) {

    private val bin = root.resolve("fake-bin").createDirectories()
    private val tools = root.resolve("tools").createDirectories()

    init {
        ORDINARY_PROGRAMS.forEach { tool -> Files.createSymbolicLink(tools.resolve(tool), checkNotNull(locateProgram(tool)) { "the program $tool is not installed; check missingProgramsReason() first" }) }
    }

    /** The search path: the fakes first, then the ordinary programs. */
    val path: String get() = "$bin${File.pathSeparator}$tools"

    /** The search path without any fake, holding only the ordinary programs. */
    val pathWithoutFakes: String get() = tools.toString()

    /**
     * Installs the test resource `/[resource]` as the executable [name] among the fakes, with every key of [replacements] replaced by its value, and returns its path.
     */
    fun install(
        resource: String,
        name: String = resource.substringAfterLast('/'),
        replacements: Map<String, String> = emptyMap(),
    ): Path = installResource(resource, bin.resolve(name), replacements)

    /**
     * Installs [content] as the executable [name] among the fakes, and returns its path.
     */
    fun installScript(name: String, content: String): Path = installExecutable(bin.resolve(name), content)

    companion object {
        /** Writes [content] to [file] and makes it executable. */
        fun executable(file: Path, content: String): Path = installExecutable(file, content)
    }
}

/**
 * The state of the fake `busctl` of [programs]: which secrets it holds, how it behaves, and every call it received.
 */
internal class FakeSecretService(root: Path, programs: FakePrograms) {

    private val state = root.resolve("secret-service").createDirectories()
    private val callsLog = state.resolve("calls.log")

    init {
        programs.install("mcp-secrets/busctl", replacements = mapOf("@STATE@" to state.toString()))
    }

    /** Stores the secret [name], locked or not; the fake holds no value at all, only the fact that an item exists. */
    fun store(name: String, locked: Boolean = false) {
        state
            .resolve(if (locked) "locked" else "unlocked")
            .createDirectories()
            .resolve(name)
            .writeText("")
    }

    /** Makes the fake answer every following call with [behaviour]: `hang`, `fail` or `garbage`. */
    fun behaves(behaviour: String) {
        state.resolve("behaviour").writeText(behaviour)
    }

    /** Returns every call of the fake in order; empty when it was never started. */
    fun calls(): List<RecordedLookup> = readCallLog(callsLog, "call").map { (_, lines) -> recordedLookup(lines) }

    /** Returns everything the fake recorded, as text. */
    fun recordedText(): String = if (callsLog.exists()) callsLog.readText() else ""
}
