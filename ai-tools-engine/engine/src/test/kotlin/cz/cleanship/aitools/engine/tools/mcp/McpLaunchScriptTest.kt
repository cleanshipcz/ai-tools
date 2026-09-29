package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.launcher.McpLauncherContract
import cz.cleanship.aitools.engine.launcher.RefusedNameClass
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.McpServerManifest
import cz.cleanship.aitools.engine.models.McpTransport
import cz.cleanship.aitools.engine.models.McpVariable
import cz.cleanship.aitools.engine.models.SecretSource
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.services.InvalidMcpServerManifestException
import cz.cleanship.aitools.engine.services.McpServerReader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.stream.Stream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Runs every launcher test with dash as `/bin/sh`. */
class McpLaunchScriptDashTest : McpLaunchScriptTest(LaunchShell.DASH) {

    @Nested
    inner class KeyringSource : McpLaunchScriptTest.KeyringSourceCases()

    @Nested
    inner class EnvironmentFallback : McpLaunchScriptTest.EnvironmentFallbackCases()

    @Nested
    inner class TimeLimit : McpLaunchScriptTest.TimeLimitCases()

    @Nested
    inner class ValuesThatCountAsNotSet : McpLaunchScriptTest.ValuesThatCountAsNotSetCases()

    @Nested
    inner class MissingSecrets : McpLaunchScriptTest.MissingSecretsCases()

    @Nested
    inner class ValuesPassThrough : McpLaunchScriptTest.ValuesPassThroughCases()

    @Nested
    inner class ServerProcess : McpLaunchScriptTest.ServerProcessCases()

    @Nested
    inner class NoLeaks : McpLaunchScriptTest.NoLeaksCases()

    @Nested
    inner class InheritedEnvironment : McpLaunchScriptTest.InheritedEnvironmentCases()

    @Nested
    inner class HelperSearch : McpLaunchScriptTest.HelperSearchCases()

    @Nested
    inner class MalformedInvocation : McpLaunchScriptTest.MalformedInvocationCases()
}

/** Runs every launcher test with bash as `/bin/sh`. */
class McpLaunchScriptBashTest : McpLaunchScriptTest(LaunchShell.BASH) {

    @Nested
    inner class KeyringSource : McpLaunchScriptTest.KeyringSourceCases()

    @Nested
    inner class EnvironmentFallback : McpLaunchScriptTest.EnvironmentFallbackCases()

    @Nested
    inner class TimeLimit : McpLaunchScriptTest.TimeLimitCases()

    @Nested
    inner class ValuesThatCountAsNotSet : McpLaunchScriptTest.ValuesThatCountAsNotSetCases()

    @Nested
    inner class MissingSecrets : McpLaunchScriptTest.MissingSecretsCases()

    @Nested
    inner class ValuesPassThrough : McpLaunchScriptTest.ValuesPassThroughCases()

    @Nested
    inner class ServerProcess : McpLaunchScriptTest.ServerProcessCases()

    @Nested
    inner class NoLeaks : McpLaunchScriptTest.NoLeaksCases()

    @Nested
    inner class InheritedEnvironment : McpLaunchScriptTest.InheritedEnvironmentCases()

    @Nested
    inner class HelperSearch : McpLaunchScriptTest.HelperSearchCases()

    @Nested
    inner class MalformedInvocation : McpLaunchScriptTest.MalformedInvocationCases()
}

// Each shell's class declares the nested groups itself, so that a report names the shell of every test case.
// Runs the launcher script scripts/mcp-launch as a real process, under the shell a subclass names, started as the kernel starts the script when that shell is /bin/sh. Every launch goes through McpLaunchSandbox, whose PATH holds a fake secret-tool and never the real one.
abstract class McpLaunchScriptTest(private val shell: LaunchShell) {

    @TempDir
    lateinit var root: Path

    private lateinit var sandbox: McpLaunchSandbox

    @BeforeEach
    fun setUp() {
        assumeTrue(McpLaunchSandbox.missingPrerequisite == null) { McpLaunchSandbox.missingPrerequisite }
        assumeTrue(shell.location != null) { "${shell.program} is not installed, so the launcher is not tested with it as /bin/sh" }
        sandbox = McpLaunchSandbox(root, shell)
    }

    // Example: mcp-launch atlassian --required JIRA_PAT --optional CONFLUENCE_PAT -- /path/to/probe-server
    private fun launcherArguments(
        vararg secrets: String,
    ): List<String> = listOf(SERVER) + secrets + listOf("--", sandbox.probeServer.toString())

    private fun filesCreatedByPayloads(): List<String> = root.listDirectoryEntries("pwned*").map { it.name }

    // What env receives from the subshell that unset every exported valid name but BASH_XTRACEFD, given the names [passed] the tool passed besides ordinary ones: under bash also the read-only SHELLOPTS and BASHOPTS when it received them, the SHLVL it exports itself, and every name that is not a valid shell name, which dash drops.
    private fun reducedEnvironmentNames(passed: Set<String> = emptySet()): Set<String> = when (shell) {
        LaunchShell.BASH -> setOf("SHLVL") + passed.filter { it in setOf("SHELLOPTS", "BASHOPTS", "BASH_XTRACEFD") || !McpLauncherContract.acceptsSecretName(it) }
        else -> passed.filter { it == "BASH_XTRACEFD" }.toSet()
    }

    abstract inner class KeyringSourceCases {

        @Test
        fun `should hand the keyring value to the server when the keyring has an entry`() {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", KEYRING_VALUE)
            assertThat(sandbox.lookups().map { it.arguments }).containsExactly(lookupOf("JIRA_PAT"))
        }

        @Test
        fun `should prefer the keyring value when the environment also sets the variable`() {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", KEYRING_VALUE)
        }

        @Test
        fun `should look up every secret once, in the order of the arguments, with the same attributes`() {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)
            sandbox.storeInKeyring("CONFLUENCE_PAT", "$KEYRING_VALUE-confluence")

            // when
            val result = sandbox.launch(launcherArguments("--optional", "CONFLUENCE_PAT", "--required", "JIRA_PAT"))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment)
                .containsEntry("JIRA_PAT", KEYRING_VALUE)
                .containsEntry("CONFLUENCE_PAT", "$KEYRING_VALUE-confluence")
            assertThat(sandbox.lookups().map { it.arguments }).containsExactly(lookupOf("CONFLUENCE_PAT"), lookupOf("JIRA_PAT"))
        }
    }

    abstract inner class EnvironmentFallbackCases {

        @Test
        fun `should use the environment value without a message when the keyring has no entry`() {
            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
            assertThat(sandbox.lookups()).hasSize(1)
        }

        @Test
        fun `should report once and use the environment for every secret when the keyring service fails`() {
            // given
            sandbox.keyringBehaves(FakeKeyringBehaviour.SERVICE_ERROR)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT", "--required", "CONFLUENCE_PAT"), bothInEnvironment())

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderrLines).containsExactly("$KEYRING_NOT_USED secret-tool lookup failed with exit code 1")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE).containsEntry("CONFLUENCE_PAT", "$ENVIRONMENT_VALUE-confluence")
            // - after the first failure the keyring is not asked again
            assertThat(sandbox.lookups()).hasSize(1)
        }

        @ParameterizedTest
        @CsvSource(
            "WITHOUT_SECRET_TOOL, secret-tool",
            "WITHOUT_TIMEOUT,     timeout",
            "WITHOUT_ENV,         env",
        )
        fun `should report once and use the environment when a helper is in no absolute directory of the PATH`(
            searchPath: SearchPath,
            helper: String,
        ) {
            // given
            // - the keyring holds the value, but without every helper the launcher never asks it
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT", "--required", "CONFLUENCE_PAT"), bothInEnvironment(), searchPath)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderrLines).containsExactly("$KEYRING_NOT_USED $helper is not in any absolute directory of the PATH")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE).containsEntry("CONFLUENCE_PAT", "$ENVIRONMENT_VALUE-confluence")
            assertThat(sandbox.lookups()).isEmpty()
        }

        @Test
        fun `should report once and use the environment when the lookup does not answer within the time limit`() {
            // given
            // - the fake sleeps 30 seconds, the launcher gives a lookup 5 seconds, and the sandbox waits at most 30 seconds for the whole launch
            sandbox.keyringBehaves(FakeKeyringBehaviour.HANGS)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT", "--required", "CONFLUENCE_PAT"), bothInEnvironment())

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderrLines).containsExactly("$KEYRING_NOT_USED secret-tool lookup timed out after 5 seconds")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE).containsEntry("CONFLUENCE_PAT", "$ENVIRONMENT_VALUE-confluence")
            assertThat(sandbox.lookups()).hasSize(1)
        }

        @ParameterizedTest
        @EnumSource(UnknownBus::class)
        fun `should not start secret-tool and use the environment when no session bus is known`(bus: UnknownBus) {
            // given
            // - DISPLAY is set, which would let the real secret-tool autolaunch a new bus and keyring daemon
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)
            val environment = bus.environment(root) + mapOf("DISPLAY" to ":99", "JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), environment)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderrLines).containsExactly(
                "$KEYRING_NOT_USED no D-Bus session bus is known (DBUS_SESSION_BUS_ADDRESS is not set and XDG_RUNTIME_DIR/bus is not a socket)",
            )
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
            assertThat(sandbox.lookups()).isEmpty()
        }

        @ParameterizedTest
        @CsvSource(
            // - no PATH at all, where the shell would otherwise search its own default path, which holds the real secret-tool; no bus is known either, so that no version of the launcher can reach a real helper here
            "missing, false",
            // - an empty PATH, with a bus known
            "empty,   true",
        )
        fun `should use the environment and start no helper when the tool passes no PATH or an empty one`(
            path: String,
            busKnown: Boolean,
        ) {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)
            val environment = (if (busKnown) McpLaunchSandbox.REACHABLE_BUS else emptyMap()) + ("JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), environment, path = { if (path == "missing") null else "" })

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.stderrLines).containsExactly("$KEYRING_NOT_USED the PATH is not set or is empty")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
            assertThat(sandbox.lookups()).isEmpty()
        }

        @ParameterizedTest
        @ValueSource(booleans = [false, true])
        fun `should reach the keyring through the bus socket under XDG_RUNTIME_DIR without passing DISPLAY or an empty bus address`(
            emptyAddress: Boolean,
        ) {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)
            val environment = mapOf("XDG_RUNTIME_DIR" to sandbox.runtimeDirectoryWithBus().toString(), "DISPLAY" to ":99") +
                (if (emptyAddress) mapOf("DBUS_SESSION_BUS_ADDRESS" to "") else emptyMap())

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), environment)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", KEYRING_VALUE)
            assertThat(sandbox.lookups()).singleElement().satisfies({ lookup ->
                assertThat(lookup.environmentNames).contains("XDG_RUNTIME_DIR").doesNotContain("DISPLAY", "DBUS_SESSION_BUS_ADDRESS")
            })
        }
    }

    abstract inner class TimeLimitCases {

        @Test
        fun `should kill a lookup that ignores the stop signal one second after the time limit and use the environment`() {
            // given
            // - the fake ignores SIGTERM and sleeps 30 seconds; one lookup takes at most 5 seconds plus 1 second of grace
            sandbox.keyringBehaves(FakeKeyringBehaviour.IGNORES_STOP_SIGNAL)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT", "--required", "CONFLUENCE_PAT"), bothInEnvironment())

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderrLines).containsExactly("$KEYRING_NOT_USED secret-tool lookup did not end within 5 seconds and was killed 1 second later")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE).containsEntry("CONFLUENCE_PAT", "$ENVIRONMENT_VALUE-confluence")
            assertThat(sandbox.lookups()).hasSize(1)
            // - the bound of 6 seconds, with a margin for a loaded build machine that still fails the 30 seconds of a lookup nobody stops
            assertThat(result.elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(5_500)).isLessThan(Duration.ofSeconds(10))
        }

        @Test
        fun `should write only its own line when the lookup is killed by a signal`() {
            // given
            sandbox.keyringBehaves(FakeKeyringBehaviour.KILLED_BY_SIGNAL)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderrLines).containsExactly("$KEYRING_NOT_USED secret-tool lookup failed with exit code 137")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
        }
    }

    abstract inner class ValuesThatCountAsNotSetCases {

        @ParameterizedTest
        @ValueSource(strings = ["", "\${JIRA_PAT}", "\${JIRA_PAT:-}", "\${env:JIRA_PAT}"])
        fun `should fall back to the environment when the keyring value is empty or a literal reference to the variable`(
            stored: String,
        ) {
            // given
            sandbox.storeInKeyring("JIRA_PAT", stored)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "\${JIRA_PAT}", "\${JIRA_PAT:-}", "\${env:JIRA_PAT}"])
        fun `should not start the server when a required secret is only in the environment as an empty value or a literal reference`(
            value: String,
        ) {
            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to value))

            // then
            assertThat(result.exitCode).isEqualTo(1)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderrLines).containsExactly("$NOT_STARTED required secret not found in the keyring or the environment: JIRA_PAT")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "\${CONFLUENCE_PAT}", "\${CONFLUENCE_PAT:-}", "\${env:CONFLUENCE_PAT}"])
        fun `should remove an optional secret from the environment of the server when its value is empty or a literal reference`(
            value: String,
        ) {
            // when
            val result = sandbox.launch(launcherArguments("--optional", "CONFLUENCE_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("CONFLUENCE_PAT" to value))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(result.server().environment).doesNotContainKey("CONFLUENCE_PAT")
        }

        @ParameterizedTest
        @ValueSource(strings = ["\${OTHER}", "\${JIRA_PAT", "\$JIRA_PAT", " ", "\${env:JIRA_PAT} ", "\n", "'\${JIRA_PAT}'", "\"\""])
        fun `should keep an environment value that only resembles a reference to the variable`(value: String) {
            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to value))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", value)
        }
    }

    abstract inner class MissingSecretsCases {

        @Test
        fun `should not start the server and name the server and the variable in one line when a required secret is found nowhere`() {
            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"))

            // then
            assertThat(result.exitCode).isEqualTo(1)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderrLines).containsExactly("$NOT_STARTED required secret not found in the keyring or the environment: JIRA_PAT")
        }

        @Test
        fun `should name every missing required secret in the same single line`() {
            // given
            sandbox.storeInKeyring("OTHER_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT", "--optional", "OTHER_PAT", "--optional", "UNSET_PAT", "--required", "CONFLUENCE_PAT"))

            // then
            assertThat(result.exitCode).isEqualTo(1)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderrLines).containsExactly("$NOT_STARTED required secret not found in the keyring or the environment: JIRA_PAT CONFLUENCE_PAT")
        }

        @Test
        fun `should start the server without the variable when an optional secret is found nowhere`() {
            // when
            val result = sandbox.launch(launcherArguments("--optional", "CONFLUENCE_PAT"))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(result.server().environment).doesNotContainKey("CONFLUENCE_PAT")
        }

        @Test
        fun `should report the unreachable keyring and then the missing required secret`() {
            // given
            sandbox.keyringBehaves(FakeKeyringBehaviour.SERVICE_ERROR)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"))

            // then
            assertThat(result.exitCode).isEqualTo(1)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderrLines).containsExactly(
                "$KEYRING_NOT_USED secret-tool lookup failed with exit code 1",
                "$NOT_STARTED required secret not found in the keyring or the environment: JIRA_PAT",
            )
        }

        @Test
        fun `should read the environment without printenv, which is on no directory of the PATH`() {
            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
        }

        @Test
        fun `should not need printenv when the keyring holds every secret`() {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", KEYRING_VALUE)
        }
    }

    abstract inner class ValuesPassThroughCases {

        @ParameterizedTest
        @EnumSource(Source::class)
        fun `should hand a value with spaces, quotes, dollar signs, backslashes, an inner newline and non-ASCII characters to the server unchanged`(
            source: Source,
        ) {
            // given
            val environment = when (source) {
                Source.KEYRING -> McpLaunchSandbox.REACHABLE_BUS.also { sandbox.storeInKeyring("JIRA_PAT", SPECIAL_VALUE) }
                Source.ENVIRONMENT -> McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to SPECIAL_VALUE)
            }

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), environment)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", SPECIAL_VALUE)
            // - a command substitution inside the value must never have run
            assertThat(filesCreatedByPayloads()).isEmpty()
        }

        @ParameterizedTest
        @CsvSource(
            "'token\\n',             'token'",
            "'token\\n\\n',          'token\\n'",
            "'token',                'token'",
            "'line-1\\nline-2\\n',   'line-1\\nline-2'",
            "'token\\r\\n',          'token\\r'",
            "'\\ntoken',             '\\ntoken'",
        )
        fun `should remove exactly one trailing newline of a keyring value`(stored: String, expected: String) {
            // given
            // - secret-tool store keeps the newline of a value piped in with echo; the prompt never stores one
            sandbox.storeInKeyring("JIRA_PAT", stored.unescaped())

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"))

            // then
            assertThat(result.server().environment).containsEntry("JIRA_PAT", expected.unescaped())
        }

        @Test
        fun `should keep trailing newlines of an environment value`() {
            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to "token\n\n"))

            // then
            assertThat(result.server().environment).containsEntry("JIRA_PAT", "token\n\n")
        }

        @Test
        fun `should treat a keyring value made of a single newline as empty`() {
            // given
            sandbox.storeInKeyring("JIRA_PAT", "\n")

            // when
            val result = sandbox.launch(launcherArguments("--optional", "JIRA_PAT"))

            // then
            assertThat(result.server().environment).doesNotContainKey("JIRA_PAT")
        }
    }

    abstract inner class ServerProcessCases {

        @Test
        fun `should replace itself with the server so that the server keeps the process id`() {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"))

            // then
            assertThat(result.server().pid).isEqualTo(result.pid)
        }

        @Test
        fun `should hand the command its arguments unchanged`() {
            // given
            // - the working directory holds files, so an unquoted * would expand to their names
            val arguments =
                listOf("with space", "'single'", "\"double\"", "\$HOME", "\${JIRA_PAT}", "*", "", "--", "--required", "back\\slash", "-e")
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT") + arguments)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().arguments).containsExactlyElementsOf(arguments)
        }

        @ParameterizedTest
        @ValueSource(strings = ["-probe", "--", "-c", "-a", "-l"])
        fun `should start a command whose name starts with a dash as a command, never as an option of exec`(
            command: String,
        ) {
            // given
            // - bash's exec reads -c, -l, -a and -- as options of its own; the probe lies in a directory of the PATH under that name
            val name = sandbox.probeServerOnPath(command)
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(listOf(SERVER, "--required", "JIRA_PAT", "--", name, "a", "-c", "--"))

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.server().arguments).containsExactly("a", "-c", "--")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", KEYRING_VALUE)
        }

        @Test
        fun `should pass standard input to the server and its standard output to the caller unchanged`() {
            // given
            val input = ByteArray(256) { it.toByte() }
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("PROBE_MODE" to "echo"), stdin = input)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stdout).isEqualTo("pid=${result.pid}\u0000".toByteArray() + input)
        }

        @Test
        fun `should keep standard input away from secret-tool`() {
            // given
            // - this fake reads standard input to its end, which would leave nothing for the server
            sandbox.keyringBehaves(FakeKeyringBehaviour.DRAINS_STDIN)
            val input = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}\n".toByteArray()

            // when
            val result = sandbox.launch(launcherArguments("--optional", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + ("PROBE_MODE" to "echo"), stdin = input)

            // then
            assertThat(result.stdout).isEqualTo("pid=${result.pid}\u0000".toByteArray() + input)
        }

        @Test
        fun `should start the command without asking the keyring when no secret is named`() {
            // when
            val result = sandbox.launch(listOf(SERVER, "--", sandbox.probeServer.toString(), "only"))

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(result.server().arguments).containsExactly("only")
            assertThat(sandbox.lookups()).isEmpty()
        }

        @Test
        fun `should start no program but env, timeout, secret-tool and the command, and env only once the environment is reduced`() {
            // given
            // - the PATH holds only the fake secret-tool and recording stand-ins of env and timeout: a program started by name, before or after, would not be found and would write a message
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + FOREIGN_VARIABLES + ("JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment, SearchPath.WITH_ONLY_RECORDING_HELPERS)

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(sandbox.helperStarts().map { it.name }).containsExactly("env", "timeout", "env", "timeout")
            assertThat(sandbox.lookups()).hasSize(2)
            assertThat(sandbox.helperStarts().filter { it.name == "env" }).allSatisfy({ env -> assertThat(env.environmentNames).isEqualTo(reducedEnvironmentNames()) })
            assertThat(result.server().environment).containsEntry("CONFLUENCE_PAT", KEYRING_VALUE).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
        }

        @ParameterizedTest
        @ValueSource(booleans = [false, true])
        fun `should hand the server the SHELLOPTS and BASHOPTS the tool passed, except under bash, which passes its own values in their place`(
            passed: Boolean,
        ) {
            // given
            // - bash as sh with -p ignores both at start, holds its own values read-only and exports them only when it received them; no POSIX script can unset them
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)
            val options = if (passed) mapOf("SHELLOPTS" to "xtrace", "BASHOPTS" to "extdebug") else emptyMap()

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), McpLaunchSandbox.REACHABLE_BUS + options)

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            val server = result.server().environment
            when {
                !passed -> assertThat(server).doesNotContainKeys("SHELLOPTS", "BASHOPTS")
                shell == LaunchShell.DASH -> assertThat(server).containsAllEntriesOf(options)
                else -> {
                    assertThat(server.getValue("SHELLOPTS").split(':')).contains("privileged", "posix").doesNotContain("xtrace")
                    assertThat(server.getValue("BASHOPTS").split(':')).doesNotContain("extdebug")
                }
            }
        }

        @Test
        fun `should exit with 127 and write nothing to standard output when the command does not exist`() {
            // when
            val result = sandbox.launch(listOf(SERVER, "--", root.resolve("missing-server").toString()))

            // then
            assertThat(result.exitCode).isEqualTo(127)
            assertThat(result.stdout).isEmpty()
            // - the shell names the command it could not start, which tells this case apart from a missing launcher
            assertThat(result.stderr).contains("missing-server")
        }

        @Test
        fun `should exit with 126 and write nothing to standard output when the command is not executable`() {
            // given
            val command = root.resolve("not-executable-server").also { it.writeText("#!/bin/sh\nexit 0\n") }

            // when
            val result = sandbox.launch(listOf(SERVER, "--", command.toString()))

            // then
            assertThat(result.exitCode).isEqualTo(126)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderr).contains("not-executable-server")
        }

        @Test
        fun `should exit with the shell's code and without the value when a keyring value is too large for the environment of a program`() {
            // given
            // - Linux refuses an environment string over 128 KiB; dash then exits 2 and bash 126
            sandbox.storeInKeyring("JIRA_PAT", "x".repeat(OVERSIZED_LENGTH) + MARKER)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"))

            // then
            assertThat(result.exitCode).isEqualTo(if (shell == LaunchShell.DASH) 2 else 126)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderr).contains("Argument list too long").doesNotContain(MARKER)
        }
    }

    abstract inner class NoLeaksCases {

        @Test
        fun `should give secret-tool only names and attributes, never a value, in its arguments or its environment`() {
            // given
            // - CONFLUENCE_PAT comes from the keyring and is exported before JIRA_PAT is looked up; JIRA_PAT is inherited from the tool
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(sandbox.lookups().map { it.arguments }).containsExactly(lookupOf("CONFLUENCE_PAT"), lookupOf("JIRA_PAT"))
            assertThat(sandbox.lookups()).allSatisfy({ lookup ->
                assertThat(lookup.environmentNames).doesNotContain("CONFLUENCE_PAT", "JIRA_PAT", "DISPLAY").contains("DBUS_SESSION_BUS_ADDRESS")
            })
            assertThat(sandbox.recordedText()).doesNotContain(MARKER)
        }

        @Test
        fun `should give env, timeout and secret-tool no secret and no other variable of the tool, and the server all of them`() {
            // given
            // - CONFLUENCE_PAT is in the keyring, JIRA_PAT only in the environment, and the tool also passes a token of another server and variables of its own
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + FOREIGN_VARIABLES + mapOf("JIRA_PAT" to ENVIRONMENT_VALUE, "HOME" to "/home/tester")

            // when
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment, SearchPath.WITH_RECORDING_HELPERS)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment)
                .containsAllEntriesOf(environment)
                .containsEntry("CONFLUENCE_PAT", KEYRING_VALUE)
            assertThat(sandbox.helperStarts().map { it.name }).containsExactly("env", "timeout", "env", "timeout")
            val helperEnvironments = sandbox.helperStarts().map { it.environmentNames } + sandbox.lookups().map { it.environmentNames }
            assertThat(helperEnvironments).allSatisfy({ names ->
                assertThat(names).doesNotContainAnyElementsOf(FOREIGN_VARIABLES.keys + listOf("CONFLUENCE_PAT", "JIRA_PAT", "DISPLAY"))
            })
            // - secret-tool, and timeout that starts it, keep only what a bus client needs
            assertThat(sandbox.helperStarts().filter { it.name == "timeout" }.map { it.environmentNames } + sandbox.lookups().map { it.environmentNames })
                .allSatisfy({ names -> assertThat(names).contains("DBUS_SESSION_BUS_ADDRESS", "HOME") })
            assertThat(sandbox.recordedText()).doesNotContain(MARKER)
        }

        @ParameterizedTest
        @EnumSource(HelperEnvironmentCase::class)
        fun `should give env, timeout and secret-tool exactly the documented variables and arguments, and no value of a secret`(
            case: HelperEnvironmentCase,
        ) {
            // given
            // - CONFLUENCE_PAT comes from the keyring, JIRA_PAT only from the environment; the tool passes variables of its own besides
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val bus =
                mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=/nonexistent/aitools-test-bus", "XDG_RUNTIME_DIR" to "/nonexistent/run", "HOME" to "/home/tester")
            val environment = bus + FOREIGN_VARIABLES + case.variables + mapOf("JIRA_PAT" to ENVIRONMENT_VALUE, "DISPLAY" to ":99")

            // when
            // - the recording stand-ins of env and timeout are shell scripts, whose shell exports PWD, and under bash SHLVL, to the program it starts; so secret-tool is recorded in a launch with the real env and timeout
            val direct = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment)
            val secretToolEnvironments = sandbox.lookups().map { it.environmentNames }
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment, SearchPath.WITH_RECORDING_HELPERS)

            // then
            assertThat(direct.exitCode).describedAs(direct.stderr).isZero()
            assertThat(secretToolEnvironments).describedAs("secret-tool").containsExactly(bus.keys, bus.keys)
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            val starts = sandbox.helperStarts()
            assertThat(starts.map { it.name }).containsExactly("env", "timeout", "env", "timeout")
            val timeout = starts.first { it.name == "env" }.arguments.single { it.endsWith("/timeout") }
            val secretTool = starts.first { it.name == "env" }.arguments.single { it.endsWith("/secret-tool") }
            listOf("CONFLUENCE_PAT", "JIRA_PAT").forEachIndexed { index, name ->
                val lookup = listOf(secretTool, "lookup", "service", "ai-tools-mcp", "variable", name)
                val (env, timeoutStart) = starts.subList(2 * index, 2 * index + 2)
                assertThat(env.environmentNames).describedAs("env").isEqualTo(reducedEnvironmentNames(case.variables.keys))
                assertThat(env.arguments).describedAs("env").containsExactlyElementsOf(listOf("-i") + bus.map { (key, value) -> "$key=$value" } + timeout + listOf("-v", "-k", "1", "5") + lookup)
                assertThat(timeoutStart.environment).describedAs("timeout").isEqualTo(bus)
                assertThat(timeoutStart.arguments).describedAs("timeout").containsExactlyElementsOf(listOf("-v", "-k", "1", "5") + lookup)
            }
            assertThat(starts.flatMap { it.arguments + it.environment.values } + sandbox.recordedText()).noneMatch { it.contains(MARKER) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["HOME", "XDG_RUNTIME_DIR"])
        fun `should not hand secret-tool a variable it may receive when that variable is empty`(name: String) {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + mapOf("HOME" to "/home/tester", "XDG_RUNTIME_DIR" to "/run/user/test") + (name to "")

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), environment)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment).containsEntry(name, "")
            assertThat(sandbox.lookups().single().environmentNames)
                .doesNotContain(name)
                .contains("DBUS_SESSION_BUS_ADDRESS", (setOf("HOME", "XDG_RUNTIME_DIR") - name).single())
        }

        @Test
        fun `should keep an environment entry whose name is not a shell name away from timeout and secret-tool`() {
            // given
            // - no shell can unset such an entry: dash drops it from the environment of every program it starts, bash passes it on
            val environment = McpLaunchSandbox.REACHABLE_BUS + mapOf("FOREIGN-TOKEN" to "foreign-$MARKER", "JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), environment, SearchPath.WITH_RECORDING_HELPERS)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(sandbox.lookups().single().environmentNames).doesNotContain("FOREIGN-TOKEN")
            val byHelper = sandbox.helperStarts().associate { it.name to it.environmentNames }
            assertThat(byHelper.getValue("timeout")).doesNotContain("FOREIGN-TOKEN")
            if (shell == LaunchShell.BASH) {
                assertThat(byHelper.getValue("env")).contains("FOREIGN-TOKEN")
                assertThat(result.server().environment).containsEntry("FOREIGN-TOKEN", "foreign-$MARKER")
            } else {
                assertThat(byHelper.getValue("env")).doesNotContain("FOREIGN-TOKEN")
                assertThat(result.server().environment).doesNotContainKey("FOREIGN-TOKEN")
            }
        }

        @Test
        fun `should leave BASH_XTRACEFD set for env alone and keep it away from timeout and secret-tool`() {
            // given
            // - bash closes the file descriptor BASH_XTRACEFD names when the variable is unset, here the standard output that carries the value
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + ("BASH_XTRACEFD" to "1")

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), environment, SearchPath.WITH_RECORDING_HELPERS)

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", KEYRING_VALUE).containsEntry("BASH_XTRACEFD", "1")
            val byHelper = sandbox.helperStarts().associate { it.name to it.environmentNames }
            assertThat(byHelper.getValue("env")).contains("BASH_XTRACEFD")
            assertThat(byHelper.getValue("timeout")).doesNotContain("BASH_XTRACEFD")
            assertThat(sandbox.lookups().single().environmentNames).doesNotContain("BASH_XTRACEFD")
        }

        @ParameterizedTest
        @EnumSource(MessageScenario::class)
        fun `should never write a value into a message or onto standard output`(scenario: MessageScenario) {
            // given
            scenario.behaviour?.let { sandbox.keyringBehaves(it) }
            sandbox.storeInKeyring("OPTIONAL_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + ("OTHER_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--optional", "OPTIONAL_PAT", "--optional", "OTHER_PAT", "--required", "JIRA_PAT"), environment, scenario.searchPath)

            // then
            assertThat(result.exitCode).isEqualTo(1)
            assertThat(result.stderr).isNotEmpty().doesNotContain(MARKER)
            assertThat(result.stdout).isEmpty()
        }
    }

    abstract inner class InheritedEnvironmentCases {

        @ParameterizedTest
        @EnumSource(HostileEnvironment::class)
        fun `should neither trace nor evaluate nor run anything the inherited environment asks the shell for`(
            hostile: HostileEnvironment,
        ) {
            // given
            // - CONFLUENCE_PAT comes from the keyring and JIRA_PAT from the environment, so both ways a value takes through the launcher are covered
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + hostile.variables(root) + ("JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment)

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(filesCreatedByPayloads()).isEmpty()
            val server = result.server()
            assertThat(server.environment).containsEntry("CONFLUENCE_PAT", KEYRING_VALUE).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
            assertThat(server.environment.keys).noneMatch { it.startsWith("mcp_launch_") }
        }

        @ParameterizedTest
        @ValueSource(strings = ["BASH_XTRACEFD", "BASH_COMPAT", "LC_ALL", "LANG", "LC_CTYPE", "LC_MESSAGES", "OPTIND"])
        fun `should print no secret and run nothing when an inherited variable of the shell holds an invalid value`(
            name: String,
        ) {
            // given
            // - the shell reads these variables before the first line of the launcher; bash warns about an invalid one, and dash stops on an OPTIND that is not a number
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + mapOf(name to "\$(: >pwned-$name)x", "JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment)

            // then
            assertThat(filesCreatedByPayloads()).isEmpty()
            assertThat(result.stderr).doesNotContain(MARKER)
            if (shell == LaunchShell.DASH && name == "OPTIND") {
                assertThat(result.exitCode).isEqualTo(2)
                assertThat(result.stdout).isEmpty()
                assertThat(result.stderr).contains("Illegal number")
            } else {
                assertThat(result.exitCode).isZero()
                assertThat(result.server().environment).containsEntry("CONFLUENCE_PAT", KEYRING_VALUE).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
            }
            // - the warning of bash names the variable, and the probe server, which runs under the same bash, warns once more; which other variables a later bash warns about is not asserted
            if (shell == LaunchShell.BASH && name in setOf("BASH_XTRACEFD", "BASH_COMPAT", "LC_ALL")) {
                assertThat(result.stderrLines).isNotEmpty().allMatch { it.contains(name) }
            }
        }

        @Test
        fun `should print no value of a variable of class A except those the header names, and the engine refuses every class A name in every role of a server started through the launcher`() {
            // given
            // - every exact name of class A, a sample of each of its patterns, and the known members of those patterns; the command does not exist, so only the launcher's shell and its loader can print anything, never a server
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val names = launcherClassNames()
            val command = root.resolve("missing-server").toString()

            // when
            val printing = names.filter { name ->
                LEAK_VALUES.any { value ->
                    val result = sandbox.launch(listOf(SERVER, "--required", "CONFLUENCE_PAT", "--", command), McpLaunchSandbox.REACHABLE_BUS + (name to value), path = { if (name == "PATH") value else it })
                    // - the path of the temporary directory holds random digits, so it is removed before the output is searched for a number derived from the value
                    (String(result.stdout, Charsets.UTF_8) + result.stderr).replace(root.toString(), "<root>").let { output -> LEAK_TRACES.any { it in output } }
                }
            }
            val refusals = names.associateWith { name -> ROLES.associateWith { role -> runCatching { McpServerReader().read(startedThroughTheLauncher(role, name), root.resolve("unused.yml").toFile()) }.exceptionOrNull() } }

            // then
            assertThat(names).contains("OPTIND", "SHLVL", "LC_ALL", "BASH_COMPAT", "BASH_XTRACEFD", "LD_PRELOAD", "LD_DEBUG", "LD_AUDIT", "MALLOC_CHECK_", "GCONV_PATH")
            assertThat(printing).describedAs("names whose value the launcher printed under ${shell.program}").containsExactlyInAnyOrderElementsOf(namesPrintedAtStart(shell))
            assertSoftly { softly ->
                refusals.forEach { (name, byRole) ->
                    byRole.forEach { (role, error) ->
                        softly
                            .assertThat(error)
                            .describedAs("$name as $role")
                            .isInstanceOf(InvalidMcpServerManifestException::class.java)
                            .hasMessageContaining("'$name'")
                    }
                }
            }
        }

        @Test
        fun `should not let an inherited variable of its own reach a helper or the server`() {
            // given
            // - every name of the form mcp_launch_* in the launcher, inherited with a value
            val ownNames = Regex("mcp_launch_[a-z_]+").findAll(mcpLaunchScript.readText()).map { it.value }.toSet()
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + ownNames.associateWith { "inherited-$MARKER" } + ("JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment, SearchPath.WITH_RECORDING_HELPERS)

            // then
            assertThat(ownNames).hasSizeGreaterThan(10)
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.server().environment.keys).noneMatch { it.startsWith("mcp_launch_") }
            assertThat(result.server().environment).containsEntry("CONFLUENCE_PAT", KEYRING_VALUE).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
            assertThat(sandbox.recordedText()).doesNotContain("mcp_launch_")
        }

        @ParameterizedTest
        @ValueSource(strings = ["mcp_launch_zzz", "mcp_launch_", "mcp_launch_Mixed_Case9"])
        fun `should not let an inherited variable whose name starts with mcp_launch_ reach a helper or the server when the launcher does not use that name`(
            name: String,
        ) {
            // given
            // - a name of the launcher's prefix that the launcher itself never assigns, so only a removal by prefix keeps it away
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + mapOf(name to "inherited-$MARKER", "JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment, SearchPath.WITH_RECORDING_HELPERS)

            // then
            assertThat(Regex("mcp_launch_[A-Za-z0-9_]+").findAll(mcpLaunchScript.readText()).map { it.value }.toSet()).doesNotContain(name)
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.server().environment.keys).noneMatch { it.startsWith("mcp_launch_") }
            assertThat(result.server().environment).containsEntry("CONFLUENCE_PAT", KEYRING_VALUE).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
            assertThat(sandbox.helperStarts().map { it.name }).containsExactly("env", "timeout", "env", "timeout")
            assertThat(sandbox.recordedText()).doesNotContain("mcp_launch_")
        }

        @Test
        fun `should hand the server unchanged a variable whose value holds lines that look like exports of the launcher's own names`() {
            // given
            // - export -p prints this value across several lines, and each continuation line reads like the export of a variable of the launcher
            val lookalike = "first\nexport mcp_launch_limit=0\nexport mcp_launch_exported=x\nexport mcp_launch_line=y\nexport mcp_launch_zzz=z"
            sandbox.storeInKeyring("CONFLUENCE_PAT", KEYRING_VALUE)
            val environment = McpLaunchSandbox.REACHABLE_BUS + mapOf("FOREIGN_NOTE" to lookalike, "mcp_launch_zzz" to "inherited-$MARKER", "JIRA_PAT" to ENVIRONMENT_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "CONFLUENCE_PAT", "--required", "JIRA_PAT"), environment)

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(result.server().environment)
                .containsEntry("FOREIGN_NOTE", lookalike)
                .containsEntry("CONFLUENCE_PAT", KEYRING_VALUE)
                .containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
            assertThat(result.server().environment.keys).noneMatch { it.startsWith("mcp_launch_") }
            assertThat(sandbox.lookups()).hasSize(2)
        }
    }

    abstract inner class HelperSearchCases {

        @ParameterizedTest
        @ValueSource(strings = [":{}", "/nonexistent-directory::{}", ".:{}", "rel:{}", "./rel:{}"])
        fun `should start only the helpers of absolute PATH entries, never those of the working directory or a relative entry`(
            pattern: String,
        ) {
            // given
            // - secret-tool, timeout and env are planted in the working directory and in rel, and come before the absolute entries
            sandbox.plantHelpers(".")
            sandbox.plantHelpers("rel")
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT"), path = { pattern.replace("{}", it) })

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderr).isEmpty()
            assertThat(sandbox.plantedHelpersThatRan()).isEmpty()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", KEYRING_VALUE)
        }

        @ParameterizedTest
        @CsvSource(
            "'{}:',                  WITHOUT_SECRET_TOOL, secret-tool",
            "':{}',                  WITHOUT_TIMEOUT,     timeout",
            "'.:{}',                 WITHOUT_ENV,         env",
            "'rel:{}',               WITHOUT_SECRET_TOOL, secret-tool",
            "'{}::/nonexistent-dir', WITHOUT_TIMEOUT,     timeout",
            "'./rel:{}',             WITHOUT_ENV,         env",
        )
        fun `should treat a helper found only in the working directory or a relative entry as missing`(
            pattern: String,
            searchPath: SearchPath,
            helper: String,
        ) {
            // given
            sandbox.plantHelpers(".")
            sandbox.plantHelpers("rel")
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(
                launcherArguments("--required", "JIRA_PAT"),
                McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE),
                searchPath,
                path = { pattern.replace("{}", it) },
            )

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderrLines).containsExactly("$KEYRING_NOT_USED $helper is not in any absolute directory of the PATH")
            assertThat(sandbox.plantedHelpersThatRan()).isEmpty()
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
        }

        @Test
        fun `should skip an absolute PATH directory whose name holds an equals sign, which env would read as an assignment`() {
            // given
            // - the only timeout lies in such a directory
            val directory = sandbox.directoryWithRealHelper("tools=elsewhere", "timeout")
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(
                launcherArguments("--required", "JIRA_PAT"),
                McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE),
                SearchPath.WITHOUT_TIMEOUT,
                path = { "$directory:$it" },
            )

            // then
            assertThat(result.exitCode).isZero()
            assertThat(result.stderrLines).containsExactly("$KEYRING_NOT_USED timeout is not in any absolute directory of the PATH")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", ENVIRONMENT_VALUE)
        }
    }

    abstract inner class MalformedInvocationCases {

        @ParameterizedTest
        @MethodSource("cz.cleanship.aitools.engine.tools.mcp.McpLaunchScriptTest#malformedInvocations")
        fun `should start nothing and name the problem in one line when the invocation is malformed`(
            arguments: List<String>,
            message: String,
        ) {
            // given
            sandbox.storeInKeyring("JIRA_PAT", KEYRING_VALUE)

            // when
            val result = sandbox.launch(arguments.map { if (it == PROBE) sandbox.probeServer.toString() else it })

            // then
            assertThat(result.exitCode).isEqualTo(2)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderrLines).containsExactly(message)
            assertThat(sandbox.lookups()).isEmpty()
        }

        @ParameterizedTest
        @MethodSource("cz.cleanship.aitools.engine.tools.mcp.McpLaunchScriptTest#refusedNames")
        fun `should refuse a secret name that the launcher, a shell, a helper, a loader or an interpreter gives a meaning of its own`(
            name: String,
        ) {
            // given
            sandbox.storeInKeyring(name, KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", "JIRA_PAT", "--optional", name), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to ENVIRONMENT_VALUE))

            // then
            assertThat(result.exitCode).isEqualTo(2)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderrLines).containsExactly("$USAGE_ERROR secret name $name is refused: a shell, the launcher, a program it starts, a loader or an interpreter gives that variable a meaning of its own")
            assertThat(sandbox.lookups()).isEmpty()
        }

        @ParameterizedTest
        @ValueSource(strings = ["GITHUB_TOKEN", "PATH_TOKEN", "LCD_PAT", "MY_LD_PRELOAD", "XDG", "DBUS", "GIT", "G", "BA_SH", "PROXY_TOKEN", "OPTIND_PAT", "_TOKEN"])
        fun `should accept a secret name that only resembles a refused one`(name: String) {
            // given
            sandbox.storeInKeyring(name, KEYRING_VALUE)

            // when
            val result = sandbox.launch(launcherArguments("--required", name))

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.server().environment).containsEntry(name, KEYRING_VALUE)
        }
    }

    /** How a test environment leaves the session bus unknown to the launcher. */
    enum class UnknownBus {
        NO_ADDRESS_AND_NO_RUNTIME_DIRECTORY,
        EMPTY_ADDRESS_AND_NO_RUNTIME_DIRECTORY,
        RUNTIME_DIRECTORY_WITHOUT_BUS,
        ;

        fun environment(root: Path): Map<String, String> = when (this) {
            NO_ADDRESS_AND_NO_RUNTIME_DIRECTORY -> emptyMap()
            EMPTY_ADDRESS_AND_NO_RUNTIME_DIRECTORY -> mapOf("DBUS_SESSION_BUS_ADDRESS" to "")
            RUNTIME_DIRECTORY_WITHOUT_BUS -> mapOf("XDG_RUNTIME_DIR" to Files.createDirectories(root.resolve("run-without-bus")).toString())
        }
    }

    /** Variables the tool passes besides the bus variables, a secret and variables of its own, that decide what env receives. */
    enum class HelperEnvironmentCase(val variables: Map<String, String>) {
        NOTHING_ELSE(emptyMap()),

        // - the shell options bash holds read-only, a trace descriptor the launcher keeps for env alone, a shell level, and a name no shell can unset
        SHELL_VARIABLES(mapOf("SHELLOPTS" to "xtrace", "BASHOPTS" to "extdebug", "BASH_XTRACEFD" to "9", "SHLVL" to "3", "FOREIGN-NAME" to "foreign")),
    }

    /** Where a test puts the value of a secret. */
    enum class Source { KEYRING, ENVIRONMENT }

    /** A launch that fails on a missing required secret after the keyring answered in a given way. */
    enum class MessageScenario(val behaviour: FakeKeyringBehaviour?, val searchPath: SearchPath) {
        KEYRING_WITHOUT_ENTRY(null, SearchPath.WITH_FAKE_SECRET_TOOL),
        KEYRING_SERVICE_ERROR(FakeKeyringBehaviour.SERVICE_ERROR, SearchPath.WITH_FAKE_SECRET_TOOL),
        KEYRING_KILLED_BY_SIGNAL(FakeKeyringBehaviour.KILLED_BY_SIGNAL, SearchPath.WITH_FAKE_SECRET_TOOL),
        SECRET_TOOL_MISSING(null, SearchPath.WITHOUT_SECRET_TOOL),
        TIMEOUT_MISSING(null, SearchPath.WITHOUT_TIMEOUT),
        ENV_MISSING(null, SearchPath.WITHOUT_ENV),
    }

    /**
     * Variables an inherited environment can hold to make a shell trace its commands, evaluate text, run startup files, replace commands with functions, or split words differently. Every payload creates a file named `pwned-*` with shell builtins only, so a test sees whether it ran.
     */
    enum class HostileEnvironment {
        TRACE_WITH_COMMAND_IN_PS4,
        TRACE_TO_STANDARD_OUTPUT,
        VERBOSE,
        NO_EXECUTION,
        EXPORT_OF_EVERY_ASSIGNMENT,
        BASH_OPTIONS,
        FUNCTIONS_REPLACING_COMMANDS,
        STARTUP_FILES,
        FIELD_SEPARATORS_AND_SEARCH_PATHS,
        ;

        fun variables(root: Path): Map<String, String> = when (this) {
            TRACE_WITH_COMMAND_IN_PS4 -> mapOf("SHELLOPTS" to "xtrace", "PS4" to "\$(: >pwned-ps4)+ ")
            TRACE_TO_STANDARD_OUTPUT -> mapOf("SHELLOPTS" to "xtrace", "BASH_XTRACEFD" to "1")
            VERBOSE -> mapOf("SHELLOPTS" to "verbose")
            NO_EXECUTION -> mapOf("SHELLOPTS" to "noexec")
            EXPORT_OF_EVERY_ASSIGNMENT -> mapOf("SHELLOPTS" to "allexport")
            BASH_OPTIONS -> mapOf("BASHOPTS" to "xpg_echo:extdebug:nullglob", "POSIXLY_CORRECT" to "1")
            FUNCTIONS_REPLACING_COMMANDS -> listOf("printf", "[", "command", "export", "unset", "exec", "set", "shift").associate { name ->
                "BASH_FUNC_$name%%" to "() { : >pwned-function; }"
            }
            STARTUP_FILES -> {
                val startup = root.resolve("startup-file").also { it.writeText(": >pwned-startup\n") }
                mapOf("BASH_ENV" to startup.toString(), "ENV" to startup.toString())
            }
            FIELD_SEPARATORS_AND_SEARCH_PATHS -> mapOf(
                "IFS" to "AEIOU_",
                "CDPATH" to "/tmp",
                "GLOBIGNORE" to "*",
                "EXECIGNORE" to "*",
                "OPTIND" to "3",
                "OPTARG" to "x",
                "LC_ALL" to "C",
                "LANG" to "C",
            )
        }
    }

    companion object {
        private const val SERVER = "atlassian"
        private const val PROBE = "<probe>"
        private const val OVERSIZED_LENGTH = 200_000

        // Every value carries the marker, so a test that finds the marker in a message has found a leaked value.
        private const val MARKER = "s3cr3t-M4RK3R"
        private const val KEYRING_VALUE = "keyring-$MARKER"
        private const val ENVIRONMENT_VALUE = "environment-$MARKER"
        private const val SPECIAL_VALUE = "a b 'single' \"double\" \$HOME \${JIRA_PAT} \$(: >pwned-value) `: >pwned-value` back\\slash\nsecond line žluťoučký € 値 $MARKER"

        private const val KEYRING_NOT_USED = "mcp-launch: $SERVER: keyring not used, reading secrets from the environment:"
        private const val NOT_STARTED = "mcp-launch: $SERVER: not started:"
        private const val USAGE_ERROR = "mcp-launch: usage error:"
        private const val SERVER_NAME_RULE = "$USAGE_ERROR the server name must start with a letter or digit and hold only letters, digits, '.', '_' and '-'"

        // Variables of the tool that no helper needs: a token of another server, a secret read only from the environment, and ordinary variables of a session.
        private val FOREIGN_VARIABLES = mapOf(
            "OTHER_SERVER_TOKEN" to "other-$MARKER",
            "ENVIRONMENT_ONLY_PAT" to "environment-only-$MARKER",
            "LANG" to "C",
            "TERM" to "xterm",
            "USER" to "tester",
            "SHELL" to "/bin/sh",
        )

        // A text value and a number, which bash prints plus one for SHLVL; each trace is what the value or a number derived from it looks like in a message.
        private val LEAK_VALUES = listOf("leak-$MARKER", "987654321")
        private val LEAK_TRACES = listOf(MARKER, "98765432")

        // The roles a name takes in a server started through the launcher: a secret read through the secrets manager, an environment-only secret, a plain variable, a fixed environment variable.
        private val ROLES = listOf("keyring-secret", "environment-secret", "plain", "fixed")

        // Members of the patterns of class A that a shell, its loader or its C library reads, besides the sample of each pattern.
        private val KNOWN_MEMBERS = listOf(
            "LC_ALL",
            "LC_CTYPE",
            "LC_MESSAGES",
            "LC_NUMERIC",
            "LC_COLLATE",
            "LC_TIME",
            "BASH_ENV",
            "BASH_COMPAT",
            "BASH_XTRACEFD",
            "BASHOPTS",
            "BASH_ARGV0",
            "BASH_LOADABLES_PATH",
            "LD_PRELOAD",
            "LD_LIBRARY_PATH",
            "LD_DEBUG",
            "LD_DEBUG_OUTPUT",
            "LD_AUDIT",
            "LD_BIND_NOW",
            "LD_PROFILE",
            "LD_SHOW_AUXV",
            "LD_TRACE_LOADED_OBJECTS",
            "LD_ASSUME_KERNEL",
            "LD_HWCAP_MASK",
            "MALLOC_CHECK_",
            "MALLOC_PERTURB_",
            "MALLOC_ARENA_MAX",
            "MALLOC_TOP_PAD_",
            "DBUS_SESSION_BUS_ADDRESS",
            "XDG_RUNTIME_DIR",
            "XDG_CONFIG_HOME",
            "G_MESSAGES_DEBUG",
            "G_DEBUG",
            "GIO_USE_VFS",
            "COMP_WORDS",
            "READLINE_LINE",
            "MCP_LAUNCH_VALUE",
            "DYLD_INSERT_LIBRARIES",
        )

        /** Returns every name of class A the sweep tries: each exact pattern, a sample of each other pattern, and the [KNOWN_MEMBERS] the contract places in class A. */
        fun launcherClassNames(): List<String> {
            val patterns = McpLauncherContract.REFUSED_NAMES.filter { it.nameClass == RefusedNameClass.LAUNCHER }.map { it.pattern }
            return (refusedNameSamples(patterns).filter { it == it.uppercase() } + KNOWN_MEMBERS.filter { McpLauncherContract.refusedNameOf(it)?.nameClass == RefusedNameClass.LAUNCHER }).distinct()
        }

        /** Returns the names the header of the launcher lists as printed by [shell] or its loader before the first line: the list after "with <program> as /bin/sh:". */
        fun namesPrintedAtStart(shell: LaunchShell): List<String> {
            val marker = "with ${shell.program} as /bin/sh:"
            val line = mcpLaunchScript.readLines().single { it.startsWith("#") && marker in it }
            return line
                .substringAfter(marker)
                .trim()
                .removeSuffix(".")
                .split(", ")
                .map { it.trim() }
        }

        // An inline stdio server that a tool starts through the launcher, with [name] in [role].
        private fun startedThroughTheLauncher(role: String, name: String): McpServerManifest {
            val managed = McpVariable("API_TOKEN", "Token", secret = true)
            val (transport, variables) = when (role) {
                "keyring-secret" -> McpTransport.Stdio(command = "server") to listOf(McpVariable(name, "Token", secret = true))
                "environment-secret" -> McpTransport.Stdio(command = "server") to listOf(managed, McpVariable(name, "Token", secret = true, from = SecretSource.ENVIRONMENT))
                "plain" -> McpTransport.Stdio(command = "server") to listOf(managed, McpVariable(name, "Value", secret = false))
                else -> McpTransport.Stdio(command = "server", env = mapOf(name to "value")) to listOf(managed)
            }
            return McpServerManifest(id = "server", description = "Server", metadata = ManifestMetadata(version = Version("1.0.0")), transport = transport, variables = variables)
        }

        private fun lookupOf(name: String) = listOf("lookup", "service", "ai-tools-mcp", "variable", name)

        private fun bothInEnvironment() = McpLaunchSandbox.REACHABLE_BUS + mapOf("JIRA_PAT" to ENVIRONMENT_VALUE, "CONFLUENCE_PAT" to "$ENVIRONMENT_VALUE-confluence")

        private fun String.unescaped() = replace("\\n", "\n").replace("\\r", "\r")

        // One or two names per pattern of the block of refused names, read from the script itself, so the tests hold no copy of the list; the engine's own copy is compared with the block in McpLauncherContractTest.
        @JvmStatic
        fun refusedNames(): Stream<String> = refusedNameSamples().stream()

        @JvmStatic
        fun malformedInvocations(): Stream<Arguments> = Stream.of(
            Arguments.of(emptyList<String>(), "$USAGE_ERROR missing server name"),
            Arguments.of(listOf("", "--", PROBE), SERVER_NAME_RULE),
            Arguments.of(listOf("bad name", "--", PROBE), SERVER_NAME_RULE),
            Arguments.of(listOf("-x", "--", PROBE), SERVER_NAME_RULE),
            Arguments.of(listOf("--required", "JIRA_PAT", "--", PROBE), SERVER_NAME_RULE),
            Arguments.of(listOf(SERVER), "$USAGE_ERROR missing -- before the command"),
            Arguments.of(listOf(SERVER, "--required", "JIRA_PAT"), "$USAGE_ERROR missing -- before the command"),
            Arguments.of(listOf(SERVER, "--required", "JIRA_PAT", "--"), "$USAGE_ERROR missing command after --"),
            Arguments.of(listOf(SERVER, "--required"), "$USAGE_ERROR --required at argument 2 is not followed by a secret name"),
            Arguments.of(listOf(SERVER, "--required", "JIRA_PAT", "--optional"), "$USAGE_ERROR --optional at argument 4 is not followed by a secret name"),
            Arguments.of(listOf(SERVER, "--required", "1BAD", "--", PROBE), "$USAGE_ERROR argument 3 is not a valid environment variable name"),
            Arguments.of(listOf(SERVER, "--optional", "BAD-NAME", "--", PROBE), "$USAGE_ERROR argument 3 is not a valid environment variable name"),
            Arguments.of(listOf(SERVER, "--optional", "", "--", PROBE), "$USAGE_ERROR argument 3 is not a valid environment variable name"),
            Arguments.of(listOf(SERVER, "--optional", "A\nB", "--", PROBE), "$USAGE_ERROR argument 3 is not a valid environment variable name"),
            Arguments.of(listOf(SERVER, "--optional", "\$(: >pwned-name)", "--", PROBE), "$USAGE_ERROR argument 3 is not a valid environment variable name"),
            Arguments.of(listOf(SERVER, "--optional", "JIRA_PAT_ž", "--", PROBE), "$USAGE_ERROR argument 3 is not a valid environment variable name"),
            // - a value passed by mistake where a name belongs is never echoed back
            Arguments.of(listOf(SERVER, "--required", "JIRA_PAT", "--optional", "ghp-$MARKER", "--", PROBE), "$USAGE_ERROR argument 5 is not a valid environment variable name"),
            Arguments.of(listOf(SERVER, "--secret", "JIRA_PAT", "--", PROBE), "$USAGE_ERROR argument 2 must be --required, --optional or --"),
            Arguments.of(listOf(SERVER, "JIRA_PAT", "--", PROBE), "$USAGE_ERROR argument 2 must be --required, --optional or --"),
            Arguments.of(listOf(SERVER, "--required", "JIRA_PAT", "--optional", "JIRA_PAT", "--", PROBE), "$USAGE_ERROR secret JIRA_PAT is listed more than once"),
        )
    }
}
