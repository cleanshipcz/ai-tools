package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.process.ProgramInvocation
import cz.cleanship.aitools.engine.process.ProgramOutcome
import cz.cleanship.aitools.engine.process.ProgramRunner
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories

/**
 * The probe asks the secret service whether an item exists for a secret, through `busctl` and the `SearchItems` call, which answers item paths and never a value. Every `busctl` here is the fake of [FakeSecretService].
 */
class SecretServiceProbeTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var programs: FakePrograms
    private lateinit var secretService: FakeSecretService

    // Only the groups that start the fake busctl need the programs it is written with; the group with a replaced runner starts nothing.
    private fun setUpFakeBusctl() {
        assumeTrue(missingProgramsReason() == null) { missingProgramsReason() }
        programs = FakePrograms(tempDir)
        secretService = FakeSecretService(tempDir, programs)
    }

    @Nested
    inner class States {

        @BeforeEach
        fun setUp() = setUpFakeBusctl()

        @ParameterizedTest
        @CsvSource(
            // - an unlocked item and a locked one are both stored: the launcher may unlock the locked one
            "unlocked, STORED",
            "locked, STORED",
            "none, ABSENT",
        )
        fun `should tell whether the secret service holds an item for the secret`(item: String, expected: String) {
            // given
            if (item != "none") secretService.store("JIRA_PAT", locked = item == "locked")

            // when
            val presence = probe(reachableBus()).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(if (expected == "STORED") SecretPresence.Stored else SecretPresence.Absent)
        }

        @Test
        fun `should tell apart two secrets by their own names`() {
            // given
            secretService.store("JIRA_PAT")
            val probe = probe(reachableBus())

            // when
            val stored = probe.search("JIRA_PAT")
            val absent = probe.search("CONFLUENCE_PAT")

            // then
            assertThat(stored).isEqualTo(SecretPresence.Stored)
            assertThat(absent).isEqualTo(SecretPresence.Absent)
        }

        @ParameterizedTest
        @CsvSource(
            "fail, busctl failed with exit code 1",
            "garbage, busctl answered in a form the engine does not read",
        )
        fun `should not tell when busctl fails or answers in another form, naming the reason`(
            behaviour: String,
            reason: String,
        ) {
            // given
            secretService.behaves(behaviour)

            // when
            val presence = probe(reachableBus()).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown(reason))
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                // - the answer of another method
                "{\"type\":\"as\",\"data\":[[],[]]}",
                // - one list instead of two
                "{\"type\":\"aoao\",\"data\":[[]]}",
                // - a second value that is not a list
                "{\"type\":\"aoao\",\"data\":[[],\"x\"]}",
                // - JSON that is not an object
                "[[],[]]",
            ],
        )
        fun `should not tell when busctl answers JSON other than two lists of item paths`(answer: String) {
            // given
            programs.installScript("busctl", "#!/bin/sh\nprintf '%s\\n' '$answer'\n")

            // when
            val presence = probe(reachableBus()).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown("busctl answered in a form the engine does not read"))
        }

        @Test
        fun `should not tell when busctl cannot be started, naming the kind of failure only`() {
            // given
            // - an executable whose interpreter does not exist, so the operating system refuses to start it
            programs.installScript("busctl", "#!/nonexistent/aitools-interpreter\n")

            // when
            val presence = probe(reachableBus()).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown("busctl could not be started (IOException)"))
        }

        @Test
        fun `should not tell when busctl does not answer within the time limit, and not ask again during the run`() {
            // given
            // - a fake that never answers, given a limit of one second
            secretService.behaves("hang")
            val probe = probe(reachableBus(), Duration.ofSeconds(1))

            // when
            val first = probe.search("JIRA_PAT")
            val second = probe.search("CONFLUENCE_PAT")

            // then
            assertThat(first).isEqualTo(SecretPresence.Unknown("busctl did not answer within 1 seconds"))
            assertThat(second).isEqualTo(first)
            assertThat(secretService.calls()).hasSize(1)
        }

        @Test
        fun `should not ask again during the run once the secret service could not be asked`() {
            // given
            secretService.behaves("fail")
            val probe = probe(reachableBus())

            // when
            probe.search("JIRA_PAT")
            val second = probe.search("CONFLUENCE_PAT")

            // then
            assertThat(second).isEqualTo(SecretPresence.Unknown("busctl failed with exit code 1"))
            assertThat(secretService.calls()).hasSize(1)
        }
    }

    @Nested
    inner class NoProgramStarted {

        @BeforeEach
        fun setUp() = setUpFakeBusctl()

        @Test
        fun `should not tell and start nothing when busctl is not on the PATH`() {
            // given
            val environment = reachableBus() + mapOf("PATH" to programs.pathWithoutFakes)

            // when
            val presence = probe(environment).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown("busctl is not on the PATH"))
            assertThat(secretService.calls()).isEmpty()
        }

        @ParameterizedTest
        @CsvSource(
            // - neither variable is set; DISPLAY is, and must not make any program start a bus of its own
            "unset, unset",
            // - an empty address is no address
            "empty, unset",
            // - a runtime directory without a bus socket in it
            "unset, without-socket",
            // - an empty runtime directory is no runtime directory
            "unset, empty",
            // - a runtime directory the file system cannot name
            "unset, invalid",
        )
        fun `should not tell and start nothing when no session bus is known`(
            address: String,
            runtimeDirectory: String,
        ) {
            // given
            val environment = buildMap {
                put("PATH", programs.path)
                put("DISPLAY", ":99")
                if (address == "empty") put("DBUS_SESSION_BUS_ADDRESS", "")
                when (runtimeDirectory) {
                    "without-socket" -> put("XDG_RUNTIME_DIR", tempDir.resolve("run").createDirectories().toString())
                    "empty" -> put("XDG_RUNTIME_DIR", "")
                    "invalid" -> put("XDG_RUNTIME_DIR", "/run/user/\u0000")
                }
            }

            // when
            val presence = probe(environment).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(
                SecretPresence.Unknown("no D-Bus session bus is known (DBUS_SESSION_BUS_ADDRESS is not set and XDG_RUNTIME_DIR/bus is not a socket)"),
            )
            assertThat(secretService.calls()).isEmpty()
        }

        @Test
        fun `should not tell and start nothing when the run has no PATH`() {
            // given
            val environment = reachableBus() - "PATH"

            // when
            val presence = probe(environment).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown("busctl is not on the PATH"))
            assertThat(secretService.calls()).isEmpty()
        }

        @Test
        fun `should skip a relative entry of the PATH, so no program of the working directory is started`() {
            // given
            // - '.' and an empty entry both mean the working directory to a shell
            val environment = reachableBus() + mapOf("PATH" to ".::relative/bin")

            // when
            val presence = probe(environment).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown("busctl is not on the PATH"))
        }
    }

    @Nested
    inner class WhatBusctlReceives {

        @BeforeEach
        fun setUp() = setUpFakeBusctl()

        @Test
        fun `should give busctl only the call, the attribute names and the name of the secret, as a fixed argument list`() {
            // given
            val probe = probe(reachableBus())

            // when
            probe.search("JIRA_PAT")

            // then
            assertThat(secretService.calls().single().arguments).containsExactly(
                "--user",
                "--no-pager",
                "--auto-start=no",
                "--timeout=30",
                "--json=short",
                "call",
                "org.freedesktop.secrets",
                "/org/freedesktop/secrets",
                "org.freedesktop.Secret.Service",
                "SearchItems",
                "a{ss}",
                "2",
                "service",
                "ai-tools-mcp",
                "variable",
                "JIRA_PAT",
            )
        }

        @Test
        fun `should start busctl with the bus variables and the PATH only, never DISPLAY or a secret of the run`() {
            // given
            // - the run exports a secret and a display; neither may reach the program
            val environment = reachableBus() + mapOf("DISPLAY" to ":99", "JIRA_PAT" to MARKER, "HOME" to tempDir.toString())

            // when
            probe(environment).search("JIRA_PAT")

            // then
            val names = secretService.calls().single().environmentNames
            assertThat(names).contains("PATH", "DBUS_SESSION_BUS_ADDRESS").doesNotContain("DISPLAY", "JIRA_PAT", "HOME")
            // - the shell of the fake adds PWD and the like, and gawk, which records the names, its own AWKPATH and AWKLIBPATH; nothing else may be there
            assertThat(names - setOf("PWD", "OLDPWD", "SHLVL", "_", "AWKPATH", "AWKLIBPATH")).containsExactlyInAnyOrder("PATH", "DBUS_SESSION_BUS_ADDRESS")
            assertThat(secretService.recordedText()).doesNotContain(MARKER)
        }

        @Test
        fun `should reach the bus through the socket under XDG_RUNTIME_DIR when no address is set`() {
            // given
            val runtime = runtimeDirectoryWithBus(tempDir.resolve("xdg"))
            secretService.store("JIRA_PAT")
            val environment =
                mapOf("PATH" to programs.path, "XDG_RUNTIME_DIR" to runtime.toString(), "DISPLAY" to ":99")

            // when
            val presence = probe(environment).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Stored)
            assertThat(secretService.calls().single().environmentNames).contains("XDG_RUNTIME_DIR").doesNotContain("DISPLAY", "DBUS_SESSION_BUS_ADDRESS")
        }

        @Test
        fun `should close the standard input of busctl`() {
            // given
            // - a fake that reads its standard input to the end answers only once that input is closed
            programs.install("mcp-secrets/busctl-reading-stdin", name = "busctl")

            // when
            val presence = probe(reachableBus()).search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Absent)
        }
    }

    /**
     * The probe with the runner of programs replaced in-process: what it asks the runner to start, and how it reads each way a start can end, without any process.
     */
    @Nested
    inner class WithAReplacedRunner {

        private val runner = mockk<ProgramRunner>()
        private val busctl = Path.of("/fake/bin/busctl")
        private val environment =
            mapOf("PATH" to "/fake/bin", "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/nonexistent/aitools-test-bus", "DISPLAY" to ":99", "JIRA_PAT" to MARKER)

        // - the one start the probe may ask for: the fixed argument list, the bus address and the PATH only, the time limit and a bounded output
        private val invocation = ProgramInvocation(
            program = busctl,
            arguments = listOf(
                "--user",
                "--no-pager",
                "--auto-start=no",
                "--timeout=5",
                "--json=short",
                "call",
                "org.freedesktop.secrets",
                "/org/freedesktop/secrets",
                "org.freedesktop.Secret.Service",
                "SearchItems",
                "a{ss}",
                "2",
                "service",
                "ai-tools-mcp",
                "variable",
                "JIRA_PAT",
            ),
            environment = mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=/nonexistent/aitools-test-bus", "PATH" to "/fake/bin"),
            timeLimit = Duration.ofSeconds(5),
            maxOutputBytes = 64 * 1024,
        )

        @BeforeEach
        fun setUpRunner() {
            every { runner.find("busctl", "/fake/bin") } returns busctl
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                "0 | {\"type\":\"aoao\",\"data\":[[\"/org/freedesktop/secrets/collection/login/1\"],[]]} | STORED",
                "0 | {\"type\":\"aoao\",\"data\":[[],[]]}                                              | ABSENT",
                "1 | Failed to connect to bus                                                                 | busctl failed with exit code 1",
                "0 | not json                                                                                 | busctl answered in a form the engine does not read",
            ],
        )
        fun `should start busctl exactly once with the fixed invocation and read how it ended`(
            exitCode: Int,
            output: String,
            expected: String,
        ) {
            // given
            every { runner.run(invocation) } returns ProgramOutcome.Finished(exitCode, output.toByteArray())

            // when
            val presence = probe().search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(
                when (expected) {
                    "STORED" -> SecretPresence.Stored
                    "ABSENT" -> SecretPresence.Absent
                    else -> SecretPresence.Unknown(expected)
                },
            )
        }

        @Test
        fun `should not tell, naming the time limit, when the program did not end within it`() {
            // given
            every { runner.run(invocation) } returns ProgramOutcome.TimedOut

            // when
            val presence = probe().search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown("busctl did not answer within 5 seconds"))
        }

        @Test
        fun `should not tell, naming the kind of failure only, when the program cannot be started`() {
            // given
            every { runner.run(invocation) } returns ProgramOutcome.NotStarted("IOException")

            // when
            val presence = probe().search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown("busctl could not be started (IOException)"))
        }

        @Test
        fun `should start nothing when the runner finds no busctl on the PATH`() {
            // given
            // - the runner is not told how to run anything, so a start would fail this test
            every { runner.find("busctl", "/fake/bin") } returns null

            // when
            val presence = probe().search("JIRA_PAT")

            // then
            assertThat(presence).isEqualTo(SecretPresence.Unknown("busctl is not on the PATH"))
        }

        private fun probe() = SecretServiceProbe(EnvironmentSource { environment[it] }, SecretServiceProbe.DEFAULT_TIMEOUT, runner)
    }

    private fun reachableBus(): Map<String, String> =
        mapOf("PATH" to programs.path, "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/nonexistent/aitools-test-bus")

    // Every call but that of a fake that hangs answers at once; the limit only has to be far above what a loaded machine needs to start the fake.
    private fun probe(
        environment: Map<String, String>,
        timeLimit: Duration = Duration.ofSeconds(30),
    ) = SecretServiceProbe(EnvironmentSource { environment[it] }, timeLimit)

    companion object {
        private const val MARKER = "s3cr3t-M4RK3R"
    }
}
