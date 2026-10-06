package cz.cleanship.aitools.engine.process

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The [ProgramRunner] of the operating system, which starts real processes. Thread-safe.
 */
object SystemProgramRunner : ProgramRunner {

    override fun find(program: String, searchPath: String?): Path? = searchPath
        .orEmpty()
        .split(File.pathSeparator)
        .filter { it.startsWith(File.separator) }
        .mapNotNull { directory ->
            // An entry the file system cannot hold names no directory, so no program is found there.
            try {
                Paths.get(directory, program)
            } catch (ignored: InvalidPathException) {
                null
            }
        }.firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }

    override fun run(invocation: ProgramInvocation): ProgramOutcome {
        // A list of arguments is handed to the operating system as it is: no shell ever reads it, so nothing in it is split, expanded or run.
        val builder = ProcessBuilder(listOf(invocation.program.toString()) + invocation.arguments).redirectError(ProcessBuilder.Redirect.DISCARD)
        // Built from nothing, so no variable of the engine, a secret of the run or DISPLAY included, reaches the program unless the invocation names it.
        builder.environment().clear()
        builder.environment().putAll(invocation.environment)
        val process = try {
            builder.start()
        } catch (ex: IOException) {
            return ProgramOutcome.NotStarted(ex.javaClass.simpleName)
        }
        val deadline = System.nanoTime() + invocation.timeLimit.toNanos()
        process.outputStream.close()
        // The output is read while the program runs, and whatever exceeds the limit is read and dropped, so a long output never blocks the program until its time limit.
        val output = FutureTask { process.inputStream.use { it.readBounded(invocation.maxOutputBytes) } }
        Thread(output, "program-output-${process.pid()}").apply { isDaemon = true }.start()
        if (!process.waitFor(invocation.timeLimit.toMillis(), TimeUnit.MILLISECONDS)) {
            kill(process)
            return ProgramOutcome.TimedOut
        }
        return try {
            ProgramOutcome.Finished(process.exitValue(), output.get((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS))
        } catch (ignored: TimeoutException) {
            // The program ended, but a process it left running still holds its output open; nothing more is waited for.
            ProgramOutcome.TimedOut
        } catch (ignored: ExecutionException) {
            // The output could not be read, so what the program answered is unknown; it counts as an empty answer, which no caller reads as one it knows.
            ProgramOutcome.Finished(process.exitValue(), ByteArray(0))
        }
    }

    // The descendants are collected first: once the program is dead, a process it started is no longer found among them.
    private fun kill(process: Process) {
        val descendants = process.descendants().toList()
        process.destroyForcibly()
        descendants.forEach { it.destroyForcibly() }
        process.waitFor()
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val kept = readNBytes(limit)
        transferTo(OutputStream.nullOutputStream())
        return kept
    }
}
