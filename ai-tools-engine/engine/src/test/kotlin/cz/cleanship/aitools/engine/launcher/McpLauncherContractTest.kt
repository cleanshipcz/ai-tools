package cz.cleanship.aitools.engine.launcher

import cz.cleanship.aitools.engine.tools.mcp.LaunchResult
import cz.cleanship.aitools.engine.tools.mcp.McpLaunchSandbox
import cz.cleanship.aitools.engine.tools.mcp.SearchPath
import cz.cleanship.aitools.engine.tools.mcp.mcpLaunchScript
import cz.cleanship.aitools.engine.tools.mcp.refusedNameSamples
import cz.cleanship.aitools.engine.tools.mcp.refusedSecretNameClasses
import cz.cleanship.aitools.engine.tools.mcp.refusedSecretNamePatterns
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The launcher contract: the rules the engine applies on behalf of `scripts/mcp-launch`, and the proof that the script applies the same ones.
 */
class McpLauncherContractTest {

    @Nested
    inner class Rules {

        @ParameterizedTest
        @CsvSource(
            // - a name, a prefix and a suffix pattern, each matched without regard to case
            "PATH, PATH",
            "path, PATH",
            "Path, PATH",
            "mcp_launch_value, MCP_LAUNCH_*",
            "MCP_LAUNCH_, MCP_LAUNCH_*",
        )
        fun `should name the pattern a refused secret name matches, whatever the case of its letters`(
            name: String,
            pattern: String,
        ) {
            // when
            val refused = McpLauncherContract.refusedPatternOf(name)

            // then
            assertThat(refused).isEqualTo(pattern)
            assertThat(McpLauncherContract.refusesName(name)).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["PATH_TOKEN", "XPATH", "MY_MCP_LAUNCH_X", "JIRA_PAT", "GITHUB_TOKEN"])
        fun `should accept a secret name that only resembles a refused one`(name: String) {
            // when
            val refused = McpLauncherContract.refusedPatternOf(name)

            // then
            assertThat(refused).isNull()
            assertThat(McpLauncherContract.refusesName(name)).isFalse()
        }

        @Test
        fun `should write every refused pattern as an upper-case name with at most a leading or a trailing star, once`() {
            // then
            assertThat(McpLauncherContract.REFUSED_NAME_PATTERNS).isNotEmpty().doesNotHaveDuplicates()
            assertThat(McpLauncherContract.REFUSED_NAME_PATTERNS).allSatisfy({ assertThat(it).matches("\\*?[A-Z0-9_]+\\*?") })
        }

        @Test
        fun `should place in class A the names the launcher's shell, the loader and C library in it, or its helpers read, or that the shell sets itself, and every other refused name in class B`() {
            // given
            val byClass = McpLauncherContract.REFUSED_NAMES.groupBy({ it.nameClass }, { it.pattern })

            // then
            // - the lists of the security review of run 3, with MALLOC_* added to class A (decision E11)
            assertThat(byClass[RefusedNameClass.LAUNCHER]).containsExactlyInAnyOrderElementsOf(CLASS_A)
            assertThat(byClass[RefusedNameClass.SERVER]).containsExactlyInAnyOrderElementsOf(CLASS_B)
            assertThat(McpLauncherContract.REFUSED_NAME_PATTERNS).containsExactlyElementsOf(McpLauncherContract.REFUSED_NAMES.map { it.pattern })
        }

        @ParameterizedTest
        @CsvSource(
            // - a name of one class only
            "SHELLOPTS, LAUNCHER, SHELLOPTS",
            "malloc_check_, LAUNCHER, MALLOC_*",
            "HTTPS_PROXY, SERVER, *_PROXY",
            "node_options, SERVER, NODE_*",
            // - a name matching a pattern of each class belongs to class A
            "G_PROXY, LAUNCHER, G_*",
            "xdg_proxy, LAUNCHER, XDG_*",
            "LD_PROXY, LAUNCHER, LD_*",
        )
        fun `should classify a refused name by the class of the pattern it matches, class A first`(
            name: String,
            nameClass: RefusedNameClass,
            pattern: String,
        ) {
            // when
            val refused = McpLauncherContract.refusedNameOf(name)

            // then
            assertThat(refused).isEqualTo(RefusedName(nameClass, pattern))
            assertThat(McpLauncherContract.refusedPatternOf(name)).isEqualTo(pattern)
        }

        @ParameterizedTest
        @ValueSource(strings = ["JIRA_PAT", "TZ", "PATH_TOKEN", "MALLOC", "HTTPS_PROXY_TOKEN"])
        fun `should classify no name the launcher accepts`(name: String) {
            // when
            val refused = McpLauncherContract.refusedNameOf(name)

            // then
            assertThat(refused).isNull()
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                "'' | false",
                "\${JIRA_PAT} | false",
                "\${JIRA_PAT:-} | false",
                "\${env:JIRA_PAT} | false",
                // - text that only resembles an unexpanded reference to the variable is a value
                "\${OTHER} | true",
                "\${JIRA_PAT:-x} | true",
                "\$JIRA_PAT | true",
                "' ' | true",
                "token | true",
            ],
        )
        fun `should count as set every value but the empty one and an unexpanded reference to the variable itself`(
            value: String,
            set: Boolean,
        ) {
            // when
            val counted = McpLauncherContract.countsAsSet("JIRA_PAT", value)

            // then
            assertThat(counted).isEqualTo(set)
        }

        @ParameterizedTest
        @CsvSource(
            "atlassian, true",
            "0jira.mcp_server-2, true",
            "'', false",
            "-jira, false",
            ".jira, false",
            "_jira, false",
            "'jira server', false",
            "jira/x, false",
        )
        fun `should accept a server id that starts with a letter or digit and holds only letters, digits, dots, underscores and dashes`(
            id: String,
            accepted: Boolean,
        ) {
            // when
            val accepts = McpLauncherContract.acceptsServerId(id)

            // then
            assertThat(accepts).isEqualTo(accepted)
        }

        @ParameterizedTest
        @CsvSource("JIRA_PAT, true", "_x9, true", "'', false", "1BAD, false", "BAD-NAME, false", "JIRA_PAT_ž, false")
        fun `should accept a secret name of letters, digits and underscores that does not start with a digit`(
            name: String,
            accepted: Boolean,
        ) {
            // when
            val accepts = McpLauncherContract.acceptsSecretName(name)

            // then
            assertThat(accepts).isEqualTo(accepted)
        }

        @Test
        fun `should look a secret up under the service ai-tools-mcp and the variable name`() {
            // when
            val attributes = McpLauncherContract.keyringAttributes("JIRA_PAT")

            // then
            assertThat(attributes).containsExactly("service", "ai-tools-mcp", "variable", "JIRA_PAT")
        }
    }

    /**
     * Every element of the contract, compared with what the launcher script does when it is started through its own `#!` line: a change of one side without the other fails here.
     */
    @Nested
    inner class AgreementWithTheScript {

        @TempDir
        lateinit var root: Path

        private lateinit var sandbox: McpLaunchSandbox

        @BeforeEach
        fun setUp() {
            assumeTrue(McpLaunchSandbox.missingPrerequisite == null) { McpLaunchSandbox.missingPrerequisite }
            sandbox = McpLaunchSandbox(root, shell = null)
        }

        @Test
        fun `should hold exactly the patterns of the block of refused names of the script`() {
            // when
            val script = refusedSecretNamePatterns()

            // then
            assertThat(McpLauncherContract.REFUSED_NAME_PATTERNS).containsExactlyInAnyOrderElementsOf(script)
        }

        @Test
        fun `should hold exactly the class of every pattern of the block of refused names of the script`() {
            // when
            val script = refusedSecretNameClasses()

            // then
            assertThat(McpLauncherContract.REFUSED_NAMES.map { it.nameClass.letter to it.pattern }).containsExactlyInAnyOrderElementsOf(script)
        }

        @Test
        fun `should refuse exactly the secret names the launcher refuses, after either option, for the reason the launcher states`() {
            // when
            val disagreements = refusalDisagreements(sandbox, mcpLaunchScript).toList()

            // then
            assertThat(disagreements).isEmpty()
        }

        @Test
        fun `should refuse a secret name in one place of the script, the loop over its block, with one message`() {
            // given
            // - comments may name the block and the message; the code must hold each once, so that no refusal can bypass the block the engine compares
            val code = mcpLaunchScript.readLines().filterNot { it.trimStart().startsWith("#") }

            // then
            assertThat(code.filter { "is refused:" in it }).hasSize(1)
            assertThat(code.filter { "\$mcp_launch_refused_names" in it }).containsExactly(BLOCK_LOOP)
        }

        @Test
        fun `should accept exactly the server ids the launcher accepts`() {
            // given
            // - every printable ASCII character, a tab and a newline, first and later in an id, and a letter beyond ASCII
            val ids = (CHARACTERS.flatMap { listOf("$it" + "a", "a$it") } + listOf("", "ž", "až", "atlassian")).distinct()

            // when
            val launches = ids.associateWith { id -> launch(listOf(id, McpLauncherContract.SEPARATOR, sandbox.probeServer.toString())) }

            // then
            assertSoftly { softly ->
                launches.forEach { (id, result) ->
                    val accepted = result.stderrLines != listOf(SERVER_NAME_RULE)
                    softly.assertThat(accepted).describedAs("id '%s': %s", id, result.stderr).isEqualTo(McpLauncherContract.acceptsServerId(id))
                    if (accepted) softly.assertThat(result.exitCode).describedAs("id '%s': %s", id, result.stderr).isZero()
                }
            }
        }

        @Test
        fun `should accept exactly the secret names the launcher accepts as environment variable names`() {
            // given
            val names = (CHARACTERS.flatMap { listOf("$it" + "A", "A$it") } + listOf("", "ž", "Až", "A\nB")).distinct().filterNot { McpLauncherContract.refusesName(it) }
            val (valid, invalid) = names.partition { McpLauncherContract.acceptsSecretName(it) }

            // when
            val rejections = invalid.associateWith { name -> launch(listOf(SERVER, McpLauncherContract.OPTIONAL, name, McpLauncherContract.SEPARATOR, sandbox.probeServer.toString())) }
            val acceptance =
                launch(listOf(SERVER) + valid.flatMap { listOf(McpLauncherContract.OPTIONAL, it) } + listOf(McpLauncherContract.SEPARATOR, sandbox.probeServer.toString()))

            // then
            assertSoftly { softly ->
                rejections.forEach { (name, result) ->
                    softly.assertThat(result.stderrLines).describedAs("name '%s'", name).containsExactly("$USAGE_ERROR argument 3 is not a valid environment variable name")
                }
                softly.assertThat(acceptance.exitCode).describedAs(acceptance.stderr).isZero()
            }
        }

        @Test
        fun `should start a server only without a required secret it cannot find, and hand over everything after the separator unchanged`() {
            // given
            val command =
                listOf(McpLauncherContract.SEPARATOR, sandbox.probeServer.toString(), McpLauncherContract.REQUIRED, "JIRA_PAT", McpLauncherContract.SEPARATOR)

            // when
            // - the fake keyring holds no entry, which the launcher reads as "not stored" without a message
            val required =
                launch(listOf(SERVER, McpLauncherContract.REQUIRED, "JIRA_PAT") + command, searchPath = SearchPath.WITH_FAKE_SECRET_TOOL)
            val optional =
                launch(listOf(SERVER, McpLauncherContract.OPTIONAL, "JIRA_PAT") + command, searchPath = SearchPath.WITH_FAKE_SECRET_TOOL)

            // then
            assertThat(required.exitCode).isEqualTo(1)
            assertThat(required.stderrLines).containsExactly("mcp-launch: $SERVER: not started: required secret not found in the keyring or the environment: JIRA_PAT")
            assertThat(optional.exitCode).describedAs(optional.stderr).isZero()
            assertThat(optional.server().arguments).containsExactly(McpLauncherContract.REQUIRED, "JIRA_PAT", McpLauncherContract.SEPARATOR)
            assertThat(optional.server().environment).doesNotContainKey("JIRA_PAT")
        }

        @Test
        fun `should count as not set exactly the values the launcher counts as not set, in the environment and in the keyring`() {
            // given
            // - every reference to the variable a tool may leave, built from the forms of reference the tools know rather than taken from either side, and values that only resemble one
            val references = listOf("", "env:", "ENV:", "input:").flatMap { prefix ->
                listOf("", ":-", ":-x", "-", ":=").flatMap { suffix -> listOf("JIRA_PAT", "jira_pat", "OTHER").map { name -> "\${$prefix$name$suffix}" } }
            }
            val values = references + listOf("", " ", "\$JIRA_PAT", "{JIRA_PAT}", "value")

            // when
            val fromEnvironment = values.associateWith { value ->
                launch(listOf(SERVER, McpLauncherContract.REQUIRED, "JIRA_PAT", McpLauncherContract.SEPARATOR, sandbox.probeServer.toString()), McpLaunchSandbox.REACHABLE_BUS + ("JIRA_PAT" to value), SearchPath.WITH_FAKE_SECRET_TOOL)
            }
            val fromKeyring = values.associateWith { value ->
                sandbox.storeInKeyring("JIRA_PAT", value)
                launch(listOf(SERVER, McpLauncherContract.REQUIRED, "JIRA_PAT", McpLauncherContract.SEPARATOR, sandbox.probeServer.toString()), McpLaunchSandbox.REACHABLE_BUS, SearchPath.WITH_FAKE_SECRET_TOOL)
            }

            // then
            assertSoftly { softly ->
                (fromEnvironment.map { "environment" to it } + fromKeyring.map { "keyring" to it }).forEach { (source, entry) ->
                    val (value, result) = entry
                    softly.assertThat(result.exitCode == 0).describedAs("%s value '%s': %s", source, value, result.stderr).isEqualTo(McpLauncherContract.countsAsSet("JIRA_PAT", value))
                }
            }
        }

        @Test
        fun `should look a secret up with exactly the keyring attributes of the contract`() {
            // given
            sandbox.storeInKeyring("JIRA_PAT", "value")

            // when
            val result =
                launch(listOf(SERVER, McpLauncherContract.REQUIRED, "JIRA_PAT", McpLauncherContract.SEPARATOR, sandbox.probeServer.toString()), McpLaunchSandbox.REACHABLE_BUS, SearchPath.WITH_FAKE_SECRET_TOOL)

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(sandbox.lookups().map { it.arguments }).containsExactly(listOf("lookup") + McpLauncherContract.keyringAttributes("JIRA_PAT"))
        }

        private fun launch(
            arguments: List<String>,
            environment: Map<String, String> = McpLaunchSandbox.REACHABLE_BUS,
            searchPath: SearchPath = SearchPath.WITHOUT_SECRET_TOOL,
        ): LaunchResult = sandbox.launch(arguments, environment, searchPath)
    }

    /**
     * The agreement check of [AgreementWithTheScript] run against a changed copy of the launcher, never the launcher of the repository: each change is one the check must notice.
     */
    @Nested
    inner class DisagreementsTheCheckFinds {

        @TempDir
        lateinit var root: Path

        @BeforeEach
        fun setUp() {
            assumeTrue(McpLaunchSandbox.missingPrerequisite == null) { McpLaunchSandbox.missingPrerequisite }
        }

        @Test
        fun `should find a script that refuses a secret name outside its block`() {
            // given
            // - TZ matches no pattern, and the copy refuses it after converting the name to upper case, where no comparison with the block reaches
            val refusal = "    case \$mcp_launch_upper in TZ) mcp_launch_usage_error \"secret name \$1 is refused: ${McpLauncherContract.REFUSAL_REASON}\" ;; esac"
            val copy = mutatedCopy(UPPERCASE_CALL, "$UPPERCASE_CALL\n$refusal")

            // when
            val disagreement = refusalDisagreements(McpLaunchSandbox(root.resolve("sandbox"), shell = null, script = copy), copy).firstOrNull()

            // then
            assertThat(disagreement).isNotNull().contains(McpLauncherContract.REQUIRED)
        }

        @Test
        fun `should find a script that skips its block for a required secret`() {
            // given
            val copy = mutatedCopy(BLOCK_LOOP, "    [ \"\$mcp_launch_kind\" = required ] || ${BLOCK_LOOP.trimStart()}")

            // when
            val disagreement = refusalDisagreements(McpLaunchSandbox(root.resolve("sandbox"), shell = null, script = copy), copy).firstOrNull()

            // then
            assertThat(disagreement).isNotNull().startsWith("${McpLauncherContract.REQUIRED} ")
        }

        // A copy of the launcher with the one line [line] replaced by [replacement], executable by its owner only, in a directory of its own.
        private fun mutatedCopy(line: String, replacement: String): Path {
            val original = mcpLaunchScript.readText()
            check(original.lines().count { it == line } == 1) { "the launcher holds the line '$line' not exactly once, so this test no longer changes what it means to" }
            val copy = root.resolve("checkout/scripts").createDirectories().resolve("mcp-launch")
            copy.writeText(original.lines().joinToString("\n") { if (it == line) replacement else it })
            Files.setPosixFilePermissions(copy, PosixFilePermissions.fromString("rwx------"))
            return copy
        }
    }

    companion object {
        private const val SERVER = "atlassian"
        private const val USAGE_ERROR = "mcp-launch: usage error:"
        private const val SERVER_NAME_RULE = "$USAGE_ERROR the server name must start with a letter or digit and hold only letters, digits, '.', '_' and '-'"

        // Every printable ASCII character, a tab and a newline.
        private val CHARACTERS: List<Char> = (' '..'~').toList() + listOf('\t', '\n')

        // The one line of the launcher that compares a secret name with the block of refused names, and the line before it.
        private const val BLOCK_LOOP = "    for mcp_launch_entry in \$mcp_launch_refused_names; do"
        private const val UPPERCASE_CALL = "    mcp_launch_uppercase \"\$1\""

        // Plain names near no refused pattern, which every launcher must accept: a refusal outside the block shows up among them.
        private val PLAIN_NAMES =
            listOf("A", "Z9", "TZ", "TERM", "USER", "LOGNAME", "EDITOR", "PAGER", "JIRA_PAT", "CONFLUENCE_PAT", "GITHUB_TOKEN", "API_TOKEN", "_TOKEN", "SSH_AUTH_SOCK", "NO_COLOR", "CI")

        // Class A: the names that change what the launcher's shell, the loader and C library in it, or its helpers do, or that the shell sets itself.
        private val CLASS_A = listOf(
            "MCP_LAUNCH_*",
            "PATH",
            "HOME",
            "DBUS_*",
            "XDG_*",
            "DISPLAY",
            "G_*",
            "GIO_*",
            "IFS",
            "_",
            "BASH*",
            "ENV",
            "SHELL",
            "SHELLOPTS",
            "SHLVL",
            "CDPATH",
            "PPID",
            "PWD",
            "OLDPWD",
            "OPTIND",
            "OPTARG",
            "OPTERR",
            "PS0",
            "PS1",
            "PS2",
            "PS3",
            "PS4",
            "LINENO",
            "RANDOM",
            "SRANDOM",
            "SECONDS",
            "UID",
            "EUID",
            "GROUPS",
            "HOSTNAME",
            "HOSTTYPE",
            "MACHTYPE",
            "OSTYPE",
            "MAIL",
            "MAILCHECK",
            "MAILPATH",
            "HISTCHARS",
            "HISTCMD",
            "HISTCONTROL",
            "HISTFILE",
            "HISTFILESIZE",
            "HISTIGNORE",
            "HISTSIZE",
            "HISTTIMEFORMAT",
            "FUNCNAME",
            "FUNCNEST",
            "GLOBIGNORE",
            "GLOBSORT",
            "EXECIGNORE",
            "FIGNORE",
            "TIMEFORMAT",
            "TMOUT",
            "PROMPT_COMMAND",
            "PROMPT_DIRTRIM",
            "PIPESTATUS",
            "DIRSTACK",
            "COMP_*",
            "COMPREPLY",
            "COPROC",
            "COLUMNS",
            "LINES",
            "EMACS",
            "INSIDE_EMACS",
            "EPOCHREALTIME",
            "EPOCHSECONDS",
            "FCEDIT",
            "HOSTFILE",
            "IGNOREEOF",
            "INPUTRC",
            "MAPFILE",
            "READLINE_*",
            "REPLY",
            "CHILD_MAX",
            "POSIXLY_CORRECT",
            "LANG",
            "LANGUAGE",
            "LC_*",
            "NLSPATH",
            "LOCPATH",
            "LD_*",
            "DYLD_*",
            "GLIBC_TUNABLES",
            "GCONV_PATH",
            "MALLOC_*",
        )

        // Class B: every other refused name, which changes what the server or a program it starts does, never the launcher.
        private val CLASS_B = listOf(
            "HOSTALIASES",
            "LOCALDOMAIN",
            "RES_OPTIONS",
            "NODE_*",
            "NPM_*",
            "PYTHON*",
            "UV_*",
            "PIP_*",
            "PERL5LIB",
            "PERL5OPT",
            "PERL5DB",
            "PERLLIB",
            "RUBYOPT",
            "RUBYLIB",
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS",
            "DOCKER_*",
            "CONTAINER_*",
            "CONTAINERS_*",
            "GIT_*",
            "SSL_*",
            "*_PROXY",
            "TMPDIR",
            "TMP",
            "TEMP",
            "REQUESTS_CA_BUNDLE",
            "CURL_CA_BUNDLE",
        )

        /**
         * Returns, lazily, every way the launcher of [sandbox], whose script is [script], disagrees with [McpLauncherContract.refusesName] about a secret name, after `--required` and after `--optional`: a name of [PLAIN_NAMES], or a sample of a pattern of either side that the contract accepts, which the launcher does not accept; and a sample the contract refuses, which the launcher does not refuse with the one line of the contract and the exit code 2. Empty when both agree.
         */
        internal fun refusalDisagreements(sandbox: McpLaunchSandbox, script: Path): Sequence<String> = sequence {
            val samples = (nameSamples(McpLauncherContract.REFUSED_NAME_PATTERNS + refusedSecretNamePatterns(script)) + PLAIN_NAMES).distinct()
            val (refused, accepted) = samples.partition { McpLauncherContract.refusesName(it) }
            for (role in listOf(McpLauncherContract.REQUIRED, McpLauncherContract.OPTIONAL)) {
                // - a launch stops at the first name it refuses, so the names it must accept are given all at once, each set in the environment, so that a required one is found
                val acceptance = sandbox.launch(
                    listOf(SERVER) + accepted.flatMap { listOf(role, it) } + listOf(McpLauncherContract.SEPARATOR, sandbox.probeServer.toString()),
                    McpLaunchSandbox.REACHABLE_BUS + accepted.associateWith { "value" },
                    SearchPath.WITHOUT_SECRET_TOOL,
                )
                if (acceptance.exitCode != 0) yield("$role: a name the contract accepts is not accepted: exit ${acceptance.exitCode}, ${acceptance.stderr}")
                for (name in refused) {
                    val result = sandbox.launch(listOf(SERVER, role, name, McpLauncherContract.SEPARATOR, sandbox.probeServer.toString()), McpLaunchSandbox.REACHABLE_BUS, SearchPath.WITHOUT_SECRET_TOOL)
                    if (result.exitCode != 2 || result.stderrLines != listOf("$USAGE_ERROR secret name $name is refused: ${McpLauncherContract.REFUSAL_REASON}")) {
                        yield("$role $name: exit ${result.exitCode}, ${result.stderr}")
                    }
                }
            }
        }

        /**
         * Returns, for each of [patterns], the names [refusedNameSamples] gives, the same names in lower case, and names that miss the pattern by one character before or after it.
         */
        fun nameSamples(patterns: List<String>): List<String> {
            val matching = refusedNameSamples(patterns)
            val misses = patterns.flatMap { pattern ->
                val core = pattern.trim('*')
                listOfNotNull("Q$core".takeUnless { pattern.startsWith('*') }, "${core}Q".takeUnless { pattern.endsWith('*') })
            }
            return (matching + matching.map { it.lowercase() } + misses).distinct().filter { McpLauncherContract.acceptsSecretName(it) }
        }
    }
}
