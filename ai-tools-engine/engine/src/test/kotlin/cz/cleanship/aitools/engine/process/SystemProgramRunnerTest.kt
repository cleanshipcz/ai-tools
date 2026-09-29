package cz.cleanship.aitools.engine.process

import cz.cleanship.aitools.engine.tools.mcp.installExecutable
import cz.cleanship.aitools.engine.tools.mcp.locateProgram
import cz.cleanship.aitools.engine.tools.mcp.missingProgramsReason
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The runner of real processes: what a started program receives, and how its end is reported. Every program here is a test script or an ordinary program of the machine; none of them is a real `busctl` or `secret-tool`.
 */
class SystemProgramRunnerTest {

    @TempDir
    lateinit var root: Path

    @BeforeEach
    fun setUp() {
        assumeTrue(missingProgramsReason() == null) { missingProgramsReason() }
    }

    @Nested
    inner class Find {

        @Test
        fun `should find the program in the first absolute directory of the search path that holds it as an executable file`() {
            // given
            val first = installExecutable(root.resolve("first/busctl"), "#!/bin/sh\n")
            installExecutable(root.resolve("second/busctl"), "#!/bin/sh\n")

            // when
            val found = SystemProgramRunner.find("busctl", "${root.resolve("empty")}:${root.resolve("first")}:${root.resolve("second")}")

            // then
            assertThat(found).isEqualTo(first)
        }

        @ParameterizedTest
        @ValueSource(strings = ["{}", "./{}", "{}:", ":{}", "{}::{}"])
        fun `should never find a program through a relative entry, which a shell reads from the working directory`(
            form: String,
        ) {
            // given
            // - a relative entry that, read from the working directory of the test, names a directory holding the program
            installExecutable(root.resolve("bin/busctl"), "#!/bin/sh\n")
            val relative = Path
                .of("")
                .toAbsolutePath()
                .relativize(root.resolve("bin"))
                .toString()

            // when
            val found = SystemProgramRunner.find("busctl", form.replace("{}", relative))

            // then
            assertThat(found).isNull()
        }

        @ParameterizedTest
        @ValueSource(strings = ["", ".", ":", "::"])
        fun `should find nothing through empty entries and the working directory alone`(searchPath: String) {
            // when
            val found = SystemProgramRunner.find("sh", searchPath)

            // then
            assertThat(found).isNull()
        }

        @Test
        fun `should skip a file that is not executable and a directory of the same name`() {
            // given
            root.resolve("directory/busctl").createDirectories()
            root
                .resolve("plain")
                .createDirectories()
                .resolve("busctl")
                .writeText("#!/bin/sh\n")

            // when
            val found = SystemProgramRunner.find("busctl", "${root.resolve("directory")}:${root.resolve("plain")}")

            // then
            assertThat(found).isNull()
        }

        @Test
        fun `should find nothing when the run has no search path`() {
            // when
            val found = SystemProgramRunner.find("sh", null)

            // then
            assertThat(found).isNull()
        }
    }

    @Nested
    inner class Run {

        @Test
        fun `should pass every argument unchanged as one argument, never through a shell`() {
            // given
            // - arguments a shell would split, expand or run; the program writes each one, followed by a NUL
            val program =
                installExecutable(root.resolve("print-arguments"), "#!/bin/sh\nfor argument in \"\$@\"; do printf '%s\\0' \"\$argument\"; done\n")
            val arguments =
                listOf("a b", "*", "\$HOME", "\$(: >${root.resolve("pwned")})", "; : >${root.resolve("pwned")}", "", "'quoted'", "line\nbreak")

            // when
            val outcome = SystemProgramRunner.run(invocation(program, arguments))

            // then
            assertThat(outcome).isInstanceOf(ProgramOutcome.Finished::class.java)
            val output = (outcome as ProgramOutcome.Finished).output.decodeToString()
            assertThat(output.split('\u0000').dropLast(1)).containsExactlyElementsOf(arguments)
            assertThat(root.resolve("pwned")).doesNotExist()
        }

        @Test
        fun `should give the program exactly the environment of the invocation and nothing of the engine`() {
            // given
            val env = checkNotNull(locateProgram("env"))

            // when
            val outcome = SystemProgramRunner.run(invocation(env, emptyList(), environment = mapOf("ONLY" to "this", "SECOND" to "value two")))

            // then
            assertThat(
                (outcome as ProgramOutcome.Finished)
                    .output
                    .decodeToString()
                    .lines()
                    .filter { it.isNotEmpty() },
            ).containsExactlyInAnyOrder("ONLY=this", "SECOND=value two")
        }

        @Test
        fun `should close the standard input of the program`() {
            // given
            // - cat copies its standard input until it ends, so it ends only once that input is closed
            val cat = checkNotNull(locateProgram("cat"))

            // when
            val outcome = SystemProgramRunner.run(invocation(cat, emptyList()))

            // then
            assertThat(outcome).isEqualTo(ProgramOutcome.Finished(0, ByteArray(0)))
        }

        @Test
        fun `should report the exit code and the output of a program that fails`() {
            // given
            val program =
                installExecutable(root.resolve("fail"), "#!/bin/sh\nprintf 'partial'\nprintf 'on standard error' >&2\nexit 3\n")

            // when
            val outcome = SystemProgramRunner.run(invocation(program, emptyList()))

            // then
            // - standard error is discarded, never kept
            assertThat(outcome).isEqualTo(ProgramOutcome.Finished(3, "partial".toByteArray()))
        }

        @Test
        fun `should keep only the first bytes of a long output, without the program waiting for the rest to be read`() {
            // given
            // - far more than a pipe holds, so a program whose output is not read on would block until its time limit
            val program =
                installExecutable(root.resolve("long"), "#!/bin/sh\nexec '${locateProgram("awk")}' 'BEGIN { for (i = 0; i < 300000; i++) printf \"x\" }'\n")

            // when
            val outcome = SystemProgramRunner.run(invocation(program, emptyList(), maxOutputBytes = 1000))

            // then
            assertThat(outcome).isEqualTo(ProgramOutcome.Finished(0, ByteArray(1000) { 'x'.code.toByte() }))
        }

        @Test
        fun `should kill a program and every process it started once the time limit has passed`() {
            // given
            // - a program that starts a child, writes the id of that child and waits for it
            val childFile = root.resolve("child")
            val program = installExecutable(
                root.resolve("hang"),
                "#!/bin/sh\n'${locateProgram("sleep")}' 60 &\nprintf '%s' \"\$!\" >'$childFile'\nwait\n",
            )
            val started = System.nanoTime()

            // when
            val outcome = SystemProgramRunner.run(invocation(program, emptyList(), timeLimit = Duration.ofSeconds(1)))

            // then
            val elapsed = Duration.ofNanos(System.nanoTime() - started)
            assertThat(outcome).isEqualTo(ProgramOutcome.TimedOut)
            assertThat(elapsed).isLessThan(Duration.ofSeconds(20))
            assertThat(childFile).exists()
            val child = ProcessHandle.of(childFile.readText().trim().toLong())
            child.ifPresent { it.onExit().get(10, TimeUnit.SECONDS) }
            assertThat(child.map { it.isAlive }.orElse(false)).isFalse()
        }

        @Test
        fun `should report a program the operating system cannot start by the kind of failure only`() {
            // given
            // - an executable whose interpreter does not exist
            val program = installExecutable(root.resolve("broken"), "#!/nonexistent/aitools-interpreter\n")

            // when
            val outcome = SystemProgramRunner.run(invocation(program, emptyList()))

            // then
            assertThat(outcome).isEqualTo(ProgramOutcome.NotStarted("IOException"))
        }
    }

    @Nested
    inner class Texts {

        @Test
        fun `should never show the output of a program or a value of its environment in a text`() {
            // given
            val invocation =
                invocation(Path.of("/usr/bin/busctl"), listOf("call"), environment = mapOf("DBUS_SESSION_BUS_ADDRESS" to MARKER))
            val outcome = ProgramOutcome.Finished(0, MARKER.toByteArray())

            // then
            assertThat(invocation.toString()).contains("DBUS_SESSION_BUS_ADDRESS").doesNotContain(MARKER)
            assertThat(outcome.toString()).isEqualTo("Finished(exitCode=0, output=${MARKER.length} bytes)")
        }

        @Test
        fun `should refuse an invocation of a program that is not an absolute path or has no time limit`() {
            // when / then
            assertThatThrownBy { invocation(Path.of("busctl"), emptyList()) }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("absolute path")
            assertThatThrownBy { invocation(Path.of("/usr/bin/busctl"), emptyList(), timeLimit = Duration.ZERO) }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("time limit")
        }
    }

    private fun invocation(
        program: Path,
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
        // A program that must end ends at once; the limit only has to be far above what a loaded machine needs to start it.
        timeLimit: Duration = Duration.ofSeconds(30),
        maxOutputBytes: Int = 64 * 1024,
    ) = ProgramInvocation(program, arguments, environment, timeLimit, maxOutputBytes)

    companion object {
        private const val MARKER = "s3cr3t-M4RK3R"
    }
}
