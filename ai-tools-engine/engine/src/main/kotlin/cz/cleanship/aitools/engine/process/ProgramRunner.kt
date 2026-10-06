package cz.cleanship.aitools.engine.process

import java.nio.file.Path
import java.time.Duration

/**
 * Finds and starts the external programs the engine asks about the machine, such as `busctl`, with every safeguard applied, so that no caller can leave one out.
 *
 * A program is started without a shell, from its absolute path, with the fixed argument list and exactly the environment of its [ProgramInvocation], its standard input closed and its standard error discarded. When it is still running once the time limit of the invocation has passed, it is killed, together with every process it started that is still among its descendants then. Its output is handed only to the caller and never logged; [ProgramOutcome] never shows it in a text.
 */
interface ProgramRunner {

    /**
     * Returns the executable regular file [program] of the first absolute directory of [searchPath], a `PATH` value, that holds one, or `null` when none does; an empty or relative entry, which a shell reads as the working directory or a directory below it, is skipped.
     *
     * @param searchPath the `PATH` of the run, or `null` when the run has none
     */
    fun find(program: String, searchPath: String?): Path?

    /**
     * Starts [invocation] and returns how it ended; it never throws for a program that cannot be started, fails or does not end in time.
     */
    fun run(invocation: ProgramInvocation): ProgramOutcome
}

/**
 * One start of an external program.
 *
 * @property program the absolute path of the program, as [ProgramRunner.find] returns it
 * @property arguments the arguments after the program, each passed on as one argument without any expansion
 * @property environment the whole environment of the program; nothing of the environment of the engine is added
 * @property timeLimit how long the program may run before it is killed
 * @property maxOutputBytes how much of its standard output is kept; the rest is read and dropped, so a long output never makes the program wait
 */
data class ProgramInvocation(
    val program: Path,
    val arguments: List<String>,
    val environment: Map<String, String>,
    val timeLimit: Duration,
    val maxOutputBytes: Int,
) {
    init {
        require(program.isAbsolute) { "A program is started only by its absolute path." }
        require(!timeLimit.isNegative && !timeLimit.isZero) { "A program is started only with a time limit." }
        require(maxOutputBytes >= 0) { "The output limit of a program cannot be negative." }
    }

    // The environment may hold a value the caller must not show; a text of the invocation names the variables only.
    override fun toString(): String = "ProgramInvocation(program=$program, arguments=$arguments, environment=${environment.keys}, timeLimit=$timeLimit, maxOutputBytes=$maxOutputBytes)"
}

/**
 * How a started program ended.
 */
sealed interface ProgramOutcome {

    /**
     * The program ended within its time limit.
     *
     * @property exitCode its exit code
     * @property output the first [ProgramInvocation.maxOutputBytes] bytes of its standard output
     */
    class Finished(val exitCode: Int, output: ByteArray) : ProgramOutcome {
        private val bytes = output.copyOf()

        /** The kept standard output, as a copy. */
        val output: ByteArray get() = bytes.copyOf()

        override fun equals(other: Any?): Boolean = other is Finished && exitCode == other.exitCode && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = 31 * exitCode + bytes.contentHashCode()

        // The output is never part of a text, so a log line or a message built from an outcome cannot carry it.
        override fun toString(): String = "Finished(exitCode=$exitCode, output=${bytes.size} bytes)"
    }

    /**
     * The program did not end within its time limit and was killed, or it ended but a process it started still held its standard output open at the time limit; such a process is not killed.
     */
    data object TimedOut : ProgramOutcome

    /**
     * The program could not be started.
     *
     * @property failure the simple name of the class of the failure, which never holds a path or a value
     */
    data class NotStarted(val failure: String) : ProgramOutcome
}
