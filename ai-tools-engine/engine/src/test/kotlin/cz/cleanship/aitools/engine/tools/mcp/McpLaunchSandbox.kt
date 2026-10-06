package cz.cleanship.aitools.engine.tools.mcp

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * The launcher script under test: the path of the system property `aitools.mcpLaunch`, which the engine's test task sets, or else `scripts/mcp-launch` in the nearest directory above the working directory that holds one.
 */
internal val mcpLaunchScript: Path by lazy {
    System.getProperty("aitools.mcpLaunch")?.let { Paths.get(it) } ?: findLauncherAbove(Paths.get("").toAbsolutePath())
}

/**
 * Returns `scripts/mcp-launch` of the nearest directory at or above [start] that holds it as a regular file.
 *
 * @throws IllegalStateException when no directory at or above [start] holds it.
 */
internal fun findLauncherAbove(start: Path): Path = generateSequence(start) { it.parent }
    .map { it.resolve("scripts").resolve("mcp-launch") }
    .firstOrNull { Files.isRegularFile(it) }
    ?: error("no directory at or above $start holds scripts/mcp-launch; set the system property aitools.mcpLaunch to its path")

/**
 * Returns the lines of the block of refused secret names of [script], in the order of the script: every line between the line `mcp_launch_refused_names='` and the next line that is `'` alone.
 *
 * Each line is the class of the names, `A` or `B`, a colon, and a pattern: an uppercase name, or a name with `*` at its start or its end, which the launcher matches against the secret name converted to uppercase.
 *
 * @throws IllegalStateException when [script] holds no such block.
 */
internal fun refusedSecretNameLines(script: Path = mcpLaunchScript): List<String> {
    val lines = script.readLines()
    val start = lines.indexOf(REFUSED_NAMES_START)
    check(start >= 0) { "$script has no line $REFUSED_NAMES_START" }
    val end = lines.subList(start + 1, lines.size).indexOf("'")
    check(end >= 0) { "the block of refused names in $script does not end with a line holding only '" }
    return lines.subList(start + 1, start + 1 + end)
}

/**
 * Returns the patterns of the secret names that [script] refuses, in the order of the script: each line of [refusedSecretNameLines] without its class.
 *
 * @throws IllegalStateException when [script] holds no block of refused names.
 */
internal fun refusedSecretNamePatterns(
    script: Path = mcpLaunchScript,
): List<String> = refusedSecretNameLines(script).map { it.substringAfter(CLASS_SEPARATOR) }

/**
 * Returns the class letter and the pattern of each line of [refusedSecretNameLines], in the order of the script.
 *
 * @throws IllegalStateException when [script] holds no block of refused names, or a line of it is not a class letter, a colon and a pattern.
 */
internal fun refusedSecretNameClasses(
    script: Path = mcpLaunchScript,
): List<Pair<Char, String>> = refusedSecretNameLines(script).map { line ->
    check(CLASSED_PATTERN.matches(line)) { "the line '$line' of the block of refused names in $script is not 'A:' or 'B:' followed by a pattern" }
    line.first() to line.substringAfter(CLASS_SEPARATOR)
}

private const val REFUSED_NAMES_START = "mcp_launch_refused_names='"
private const val CLASS_SEPARATOR = ':'
private val CLASSED_PATTERN = Regex("[AB]:\\*?[A-Z0-9_]+\\*?")

/**
 * Returns, for each of [patterns], written as the block of refused names writes them, a name the pattern matches, in upper case and with its first letter in lower case: `LD_*` gives `LD_X9` and `lD_X9`, `*_PROXY` gives `X9_PROXY` and `x9_PROXY`, `PATH` gives `PATH` and `pATH`.
 */
internal fun refusedNameSamples(patterns: List<String> = refusedSecretNamePatterns()): List<String> = patterns
    .flatMap { pattern ->
        val core = pattern.trim('*')
        val matching = when {
            pattern.startsWith('*') && pattern.endsWith('*') -> "X9${core}X9"
            pattern.endsWith('*') -> "${core}X9"
            pattern.startsWith('*') -> "X9$core"
            else -> core
        }
        listOf(matching, matching.replaceFirstChar { it.lowercaseChar() })
    }.distinct()

/** A shell that the launcher tests run as `/bin/sh` would run: through a link named `sh`, with the options of the launcher's `#!` line. */
enum class LaunchShell(val program: String) {
    DASH("dash"),

    // A link named sh makes bash run as sh, in POSIX mode, exactly as it does when /bin/sh is bash.
    BASH("bash"),

    // BusyBox picks the applet from the name it is started under, so a link named sh runs its ash.
    BUSYBOX("busybox"),
    ;

    /** The installed program, or null when the test machine does not have it. */
    val location: Path? by lazy { locateProgram(program) }
}

/** The search path a launch runs with; none of them holds the real `secret-tool`. */
enum class SearchPath(
    val withFakeSecretTool: Boolean,
    val missingTool: String?,
    val recordingHelpers: Boolean = false,
    val onlyHelpers: Boolean = false,
) {
    WITH_FAKE_SECRET_TOOL(true, null),
    WITHOUT_SECRET_TOOL(false, null),
    WITHOUT_TIMEOUT(true, "timeout"),
    WITHOUT_ENV(true, "env"),

    // env and timeout are stand-ins that record their arguments and environment before they start the real program.
    WITH_RECORDING_HELPERS(true, null, recordingHelpers = true),

    // The fake secret-tool and the recording env and timeout, and no other program: a program the launcher started by name would not be found.
    WITH_ONLY_RECORDING_HELPERS(true, null, recordingHelpers = true, onlyHelpers = true),
}

/** How the fake `secret-tool` answers a lookup. */
enum class FakeKeyringBehaviour(val fileContent: String) {
    ANSWERS("normal"),
    SERVICE_ERROR("service-error"),
    HANGS("hang"),
    IGNORES_STOP_SIGNAL("ignore-term"),
    KILLED_BY_SIGNAL("killed"),
    DRAINS_STDIN("drain-stdin"),
}

/** One start of a recording helper stand-in: the helper's name, its arguments, and its environment exactly as the kernel handed it over. */
internal data class RecordedHelper(
    val name: String,
    val arguments: List<String>,
    val environment: Map<String, String>,
) {

    /** The names of [environment]. */
    val environmentNames: Set<String> get() = environment.keys
}

/** What the probe server reported: its process id, its arguments, and its environment decoded as UTF-8. */
internal data class ServerReport(val pid: Long, val arguments: List<String>, val environment: Map<String, String>)

/** The outcome of one launch. */
internal class LaunchResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String,
    val pid: Long,
    val elapsed: Duration = Duration.ZERO,
) {

    /** The lines the launcher wrote to standard error. */
    val stderrLines: List<String> get() = stderr.lines().dropLastWhile { it.isEmpty() }

    /** Parses the records of the probe server on standard output; fails when standard output holds anything else. */
    fun server(): ServerReport {
        val records = String(stdout, Charsets.UTF_8).split('\u0000')
        check(records.last().isEmpty()) { "standard output does not end with a record: ${String(stdout, Charsets.UTF_8)}" }
        val iterator = records.dropLast(1).iterator()
        check(iterator.hasNext()) { "standard output is empty; standard error: $stderr" }
        val first = iterator.next()
        check(first.startsWith("pid=")) { "standard output does not start with the record of the server: $first" }
        val arguments = mutableListOf<String>()
        val environment = linkedMapOf<String, String>()
        var inEnvironment = false
        for (record in iterator) {
            when {
                inEnvironment -> environment[record.substringBefore('=')] = record.substringAfter('=')
                record == "env" -> inEnvironment = true
                record.startsWith("arg=") -> arguments += record.removePrefix("arg=")
                else -> error("unexpected record on standard output: $record")
            }
        }
        return ServerReport(first.removePrefix("pid=").toLong(), arguments, environment)
    }
}

/**
 * Runs the launcher [script] with a fake `secret-tool` and a probe server inside [root], so no launch ever reaches a real keyring, bus or display.
 *
 * With a [shell], the launcher runs under that shell started as `sh` with the options of its `#!` line; with none, it runs through its `#!` line under the `/bin/sh` of the machine.
 */
internal class McpLaunchSandbox(
    private val root: Path,
    shell: LaunchShell?,
    private val script: Path = mcpLaunchScript,
) {

    private val state = root.resolve("keyring").createDirectories()
    private val entries = state.resolve("entries").createDirectories()
    private val recordedEnvironments = state.resolve("environ").createDirectories()
    private val recordedArguments = state.resolve("arguments").createDirectories()
    private val fakeBin = root.resolve("fake-bin").createDirectories()
    private val resources = root.resolve("resources").createDirectories()
    private val plantedMarkers = root.resolve("planted-ran").createDirectories()
    private val environmentFiles = root.resolve("environment")
    private val callsLog = state.resolve("calls.log")
    private val helpersLog = state.resolve("helpers.log")

    // Every fake refers to the programs it needs by the absolute path of this directory, because the launcher starts some of them without a PATH.
    private val allTools = toolsDirectory("tools-all", SearchPath.WITH_FAKE_SECRET_TOOL)

    // A link named sh to the shell under test; the kernel then starts the launcher and the probe server under it as it would under /bin/sh.
    private val shellLink: Path? = shell?.let { chosen ->
        val link = root.resolve("shell-${chosen.name.lowercase()}").createDirectories().resolve("sh")
        Files.createSymbolicLink(link, checkNotNull(chosen.location) { "${chosen.program} is not installed" })
    }
    private val interpreterLine = (listOf(shellLink?.toString() ?: "/bin/sh") + shebangOptions(script)).joinToString(" ")

    /** The probe server to pass as the real command. */
    val probeServer: Path = install("probe-server", resources.resolve("probe-server"))
    private val envFromFiles = install("env-from-files", resources.resolve("env-from-files"))

    init {
        install("secret-tool", fakeBin.resolve("secret-tool"))
    }

    /** Stores [value] under the variable [name] in the fake keyring, byte for byte. */
    fun storeInKeyring(name: String, value: String) {
        entries.resolve(name).writeBytes(value.toByteArray(Charsets.UTF_8))
    }

    /** Makes the fake `secret-tool` answer every following lookup with [behaviour]. */
    fun keyringBehaves(behaviour: FakeKeyringBehaviour) {
        state.resolve("behaviour").writeText(behaviour.fileContent)
    }

    /** Creates a directory holding a socket named `bus`, as `XDG_RUNTIME_DIR` does when a session bus runs, and returns its path. */
    fun runtimeDirectoryWithBus(): Path = runtimeDirectoryWithBus(root.resolve("run"))

    /** Installs a copy of the probe server as the program [name] in a directory of the `PATH` of every launch, and returns [name]. */
    fun probeServerOnPath(name: String): String {
        install("probe-server", fakeBin.resolve(name))
        return name
    }

    /** Installs the helpers `secret-tool`, `timeout` and `env` as programs that only record that they ran, in [relativeDirectory] below the working directory of the launch. */
    fun plantHelpers(relativeDirectory: String) {
        HELPERS.forEach { helper ->
            val marker = plantedMarkers.resolve("${relativeDirectory.replace('/', '_')}-$helper")
            install("planted", root.resolve(relativeDirectory).resolve(helper).normalize(), mapOf("@MARKER@" to marker.toString()))
        }
    }

    /** Creates the directory [name] below the working directory of the launch holding a link to the real program [helper], and returns its absolute path. */
    fun directoryWithRealHelper(name: String, helper: String): Path {
        val directory = root.resolve(name).createDirectories()
        Files.createSymbolicLink(directory.resolve(helper), checkNotNull(locateProgram(helper)) { "the program $helper is not installed" })
        return directory
    }

    /** Returns the planted helpers that ran, as `<directory>-<helper>`; empty when none did. */
    fun plantedHelpersThatRan(): List<String> = plantedMarkers.listDirectoryEntries().map { it.name }.sorted()

    /** Returns every call of the fake `secret-tool` in order, with its environment exactly as the kernel handed it over; empty when it was never started. */
    fun lookups(): List<RecordedLookup> = readCallLog(callsLog, "call ").map { (header, lines) ->
        RecordedLookup(lines.filter { it.startsWith("arg ") }.map { it.removePrefix("arg ") }, recordedEnvironment("${header.removePrefix("call ")}-secret-tool").keys)
    }

    /** Returns every start of a recording helper stand-in in order; empty when none was started. */
    fun helperStarts(): List<RecordedHelper> = readCallLog(helpersLog, "helper ").map { (header, _) ->
        val (name, pid) = header.removePrefix("helper ").split(' ')
        RecordedHelper(name, nulSeparated(recordedArguments.resolve("$pid-$name")), recordedEnvironment("$pid-$name"))
    }

    /** Returns everything the fake `secret-tool` and the recording helpers recorded, their arguments and environments included, as text. */
    fun recordedText(): String = (listOf(callsLog, helpersLog) + recordedEnvironments.listDirectoryEntries() + recordedArguments.listDirectoryEntries())
        .filter { it.exists() }
        .joinToString("") { it.readText() }

    /**
     * Starts the launcher with [arguments] and exactly the environment [environment] plus `PATH`, writes [stdin] and waits for the end.
     *
     * The `PATH` holds the fake `secret-tool` and the programs of [searchPath]; [path] may rewrite it, for example to add relative entries, or return `null` to start the launcher without any `PATH`.
     */
    fun launch(
        arguments: List<String>,
        environment: Map<String, String> = REACHABLE_BUS,
        searchPath: SearchPath = SearchPath.WITH_FAKE_SECRET_TOOL,
        stdin: ByteArray = ByteArray(0),
        path: (String) -> String? = { it },
    ): LaunchResult {
        val tools = toolsDirectory("tools-${searchPath.name.lowercase()}", searchPath)
        val searchPathValue =
            path(listOfNotNull(fakeBin.takeIf { searchPath.withFakeSecretTool }, tools).joinToString(":"))
        writeEnvironment(environment + listOfNotNull(searchPathValue?.let { "PATH" to it }))
        val stdout = root.resolve("stdout").toFile()
        val stderr = root.resolve("stderr").toFile()
        val builder = ProcessBuilder(listOf("/bin/sh", envFromFiles.toString(), environmentFiles.toString()) + interpreter() + arguments)
            .directory(root.toFile())
            .redirectOutput(stdout)
            .redirectError(stderr)
        // The wrapper refers to every program by an absolute path and hands the launcher exactly the variables written as files.
        builder.environment().clear()
        val started = System.nanoTime()
        val process = builder.start()
        process.outputStream.use { it.write(stdin) }
        check(process.waitFor(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            "the launch did not end within $LAUNCH_TIMEOUT_SECONDS seconds"
        }
        val elapsed = Duration.ofNanos(System.nanoTime() - started)
        return LaunchResult(process.exitValue(), stdout.readBytes(), stderr.readText(), process.pid(), elapsed)
    }

    // The kernel starts a script as "<interpreter> [<option>] <script>"; the chosen shell takes the place of the interpreter of the #! line.
    private fun interpreter(): List<String> =
        if (shellLink == null) listOf(script.toString()) else listOf(shellLink.toString()) + shebangOptions(script) + script.toString()

    private fun writeEnvironment(environment: Map<String, String>) {
        environmentFiles.toFile().deleteRecursively()
        environmentFiles.createDirectories()
        environment.forEach { (name, value) -> environmentFiles.resolve(name).writeBytes(value.toByteArray(Charsets.UTF_8)) }
    }

    // The recorders copy /proc/<pid>/environ, NUL-separated "NAME=value" entries, as the kernel handed them to the process, before its own shell added anything.
    private fun recordedEnvironment(
        key: String,
    ): Map<String, String> = nulSeparated(recordedEnvironments.resolve(key)).associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun nulSeparated(file: Path): List<String> = if (file.exists()) String(Files.readAllBytes(file), Charsets.UTF_8).split('\u0000').dropLast(1) else emptyList()

    // Links only the programs the launcher, the fakes and the probe need, so that the real secret-tool in /usr/bin is never on the PATH.
    private fun toolsDirectory(name: String, searchPath: SearchPath): Path {
        val directory = root.resolve(name)
        if (!directory.exists()) {
            directory.createDirectories()
            ORDINARY_PROGRAMS
                .filter { it != searchPath.missingTool && (!searchPath.onlyHelpers || it in RECORDED_HELPERS) }
                .forEach { tool ->
                    val real = checkNotNull(locateProgram(tool)) { "the program $tool is not installed" }
                    if (searchPath.recordingHelpers && tool in RECORDED_HELPERS) {
                        install("helper-recorder", directory.resolve(tool), mapOf("@NAME@" to tool, "@REAL@" to real.toString()))
                    } else {
                        Files.createSymbolicLink(directory.resolve(tool), real)
                    }
                }
        }
        return directory
    }

    private fun install(resource: String, target: Path, replacements: Map<String, String> = emptyMap()): Path =
        installResource("mcp-launch/$resource", target, replacements + mapOf("@STATE@" to state.toString(), "@TOOLS@" to allTools.toString(), "@INTERPRETER@" to interpreterLine))

    companion object {
        /** An environment in which the launcher considers a session bus known; the address points nowhere, so even a real `secret-tool` could not connect. */
        val REACHABLE_BUS =
            mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=/nonexistent/aitools-test-bus", "DISPLAY" to ":99")

        /** The helpers the launcher may start before the server. */
        val HELPERS = listOf("secret-tool", "timeout", "env")

        private const val LAUNCH_TIMEOUT_SECONDS = 30L
        private val RECORDED_HELPERS = setOf("env", "timeout")

        /**
         * The reason the launcher tests cannot run on this machine, or null when every program they need is installed: a `timeout` that takes `-v` and `-k` (GNU coreutils), `awk`, `cat`, `env` and `sleep`, and `/proc/self/environ`, where the fakes read the environment they received.
         */
        val missingPrerequisite: String? by lazy {
            when {
                missingProgramsReason() != null -> "the launcher tests cannot run: ${missingProgramsReason()}"
                !timeoutKillsAfterGracePeriod() -> "the launcher tests need a timeout that takes -v and -k, such as the one of GNU coreutils"
                !Files.isReadable(Paths.get("/proc/self/environ")) -> "the launcher tests read the environment a helper received from /proc/<pid>/environ, which this machine does not have"
                else -> null
            }
        }

        /** Returns the options of the `#!` line of [script], which the kernel passes to the interpreter. */
        fun shebangOptions(script: Path = mcpLaunchScript): List<String> {
            val line = script.readLines().first()
            check(line.startsWith("#!")) { "the launcher does not start with a #! line: $line" }
            return line
                .removePrefix("#!")
                .trim()
                .split(' ')
                .filter { it.isNotEmpty() }
                .drop(1)
        }

        private fun timeoutKillsAfterGracePeriod(): Boolean {
            val process = ProcessBuilder(locateProgram("timeout").toString(), "-v", "-k", "1", "1", locateProgram("sleep").toString(), "0")
                .redirectErrorStream(true)
                .start()
            process.inputStream.use { it.readBytes() }
            return process.waitFor(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0
        }
    }
}
