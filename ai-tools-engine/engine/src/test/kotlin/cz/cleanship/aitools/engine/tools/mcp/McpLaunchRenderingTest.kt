package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.McpHeader
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpText
import cz.cleanship.aitools.engine.models.McpTextPart
import cz.cleanship.aitools.engine.models.McpVariable
import cz.cleanship.aitools.engine.models.SecretSource
import cz.cleanship.aitools.engine.models.Version
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.tomlj.Toml
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * A stdio server whose secrets the libsecret keyring supplies, from its manifest to the entry of every MCP config file, and from the entry back to the process a tool starts.
 */
class McpLaunchRenderingTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var programs: FakePrograms
    private lateinit var launcher: Path

    private val file = File("/project/mcp-config")

    // - the config of the run declares the base URL; the environment of the run carries nothing a file may hold
    private val variables =
        VariableResolver(mapOf("JIRA_BASE_URL" to "https://jira.example.com"), environment = { name -> mapOf("JIRA_PAT" to SECRET_VALUE)[name] })

    @BeforeEach
    fun setUp() {
        assumeTrue(missingProgramsReason() == null) { missingProgramsReason() }
        programs = FakePrograms(tempDir)
        FakeSecretService(tempDir, programs)
        // - the checkout lies in a directory whose name holds a space, which every format must keep inside one string
        launcher = FakePrograms.executable(tempDir.resolve("ai tools/scripts/mcp-launch"), "#!/bin/sh\nexec \"\$@\"\n")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonFormats")
    fun `should start the launcher from a JSON entry, keeping the real command, its arguments and every reference`(
        name: String,
        format: JsonMcpConfigFormat,
        serversKey: String,
        reference: (String, Boolean) -> String,
    ) {
        // when
        val content = format.merge(null, listOf(resolve(atlassian)), setOf("atlassian"), file)

        // then
        val entry = Json
            .parseToJsonElement(content)
            .jsonObject
            .getValue(serversKey)
            .jsonObject
            .getValue("atlassian")
            .jsonObject
        assertThat(entry.getValue("command").jsonPrimitive.content).describedAs(name).isEqualTo(launcher.toRealPath().toString())
        assertThat(entry.getValue("args").jsonArray.map { it.jsonPrimitive.content }).describedAs(name).containsExactlyElementsOf(LAUNCHER_ARGUMENTS)
        // - the secrets the launcher reads are no longer required from the tool: the launcher itself refuses to start without a required one
        assertThat(entry.getValue("env").jsonObject).describedAs(name).isEqualTo(
            JsonObject(
                linkedMapOf(
                    "JIRA_PAT" to JsonPrimitive(reference("JIRA_PAT", false)),
                    "CONFLUENCE_PAT" to JsonPrimitive(reference("CONFLUENCE_PAT", false)),
                    "PROXY_TOKEN" to JsonPrimitive(reference("PROXY_TOKEN", true)),
                    "JIRA_BASE_URL" to JsonPrimitive("https://jira.example.com"),
                ),
            ),
        )
        assertThat(listOf(entry.getValue("command").jsonPrimitive.content) + LAUNCHER_ARGUMENTS).noneMatch { it.contains("\${") }
        assertThat(content).doesNotContain(SECRET_VALUE)
    }

    @Test
    fun `should start the launcher from a Codex table, forwarding the secrets and the bus variables by name`() {
        // when
        val content = CodexTomlMcpConfigFormat.merge(null, listOf(resolve(atlassian)), setOf("atlassian"), file)

        // then
        val table = Toml.parse(content).also { assertThat(it.hasErrors()).isFalse() }
        assertThat(table.getString("mcp_servers.atlassian.command")).isEqualTo(launcher.toRealPath().toString())
        assertThat(table.getArray("mcp_servers.atlassian.args")?.toList()).containsExactlyElementsOf(LAUNCHER_ARGUMENTS)
        assertThat(table.getArray("mcp_servers.atlassian.env_vars")?.toList())
            .containsExactly("JIRA_PAT", "CONFLUENCE_PAT", "PROXY_TOKEN", "DBUS_SESSION_BUS_ADDRESS", "XDG_RUNTIME_DIR")
        assertThat(table.getTable("mcp_servers.atlassian.env")?.toMap()).isEqualTo(mapOf("JIRA_BASE_URL" to "https://jira.example.com"))
        assertThat(content).doesNotContain(SECRET_VALUE).doesNotContain("unix:path")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFormats")
    fun `should render a server without a secret the keyring supplies byte for byte as without a secrets manager`(
        name: String,
        format: McpConfigFormat,
    ) {
        // given
        // - a stdio server without secrets, one whose every secret is read from the environment, and an http server with a secret header
        val servers = listOf(plainStdio, environmentOnly, remote)
        val owned = servers.map { it.id }.toSet()

        // when
        val withManager = format.merge(null, servers.map { resolve(it) }, owned, file)
        val withoutManager = format.merge(null, servers.map { McpServerResolver(variables).resolve(it) }, owned, file)

        // then
        assertThat(withManager).describedAs(name).isEqualTo(withoutManager).doesNotContain(launcher.toString())
    }

    /**
     * The rendering and the launcher agree: the command and arguments of a rendered entry, started as a tool starts them, hand the server the secret of the fake keyring.
     */
    @Test
    fun `should hand the server the secret of the keyring when the rendered entry is started as a tool starts it`() {
        // given
        assumeTrue(McpLaunchSandbox.missingPrerequisite == null) { McpLaunchSandbox.missingPrerequisite }
        // - the real launcher of the repository, the fake secret-tool holding JIRA_PAT only, and a probe server in a directory whose name holds a space
        val keyring = tempDir.resolve("keyring").createDirectories()
        keyring
            .resolve("entries")
            .createDirectories()
            .resolve("JIRA_PAT")
            .writeText(KEYRING_VALUE)
        // - the launcher starts secret-tool with nearly no environment, so the fakes name their state directory and the programs they need by absolute paths
        val fakeTools =
            mapOf("@STATE@" to keyring.toString(), "@TOOLS@" to programs.pathWithoutFakes, "@INTERPRETER@" to "/bin/sh")
        programs.install("mcp-launch/secret-tool", replacements = fakeTools)
        val probeServer = tempDir.resolve("server dir/probe-server")
        probeServer.parent.createDirectories()
        val probeTemplate = checkNotNull(javaClass.getResourceAsStream("/mcp-launch/probe-server")).use { it.readBytes().decodeToString() }
        FakePrograms.executable(probeServer, fakeTools.entries.fold(probeTemplate) { text, (key, value) -> text.replace(key, value) })
        val server = atlassian.copy(
            transport = McpServerTransport.Stdio(command = probeServer.toString(), args = SERVER_ARGUMENTS.map { McpText.literal(it) }, env = emptyMap()),
        )
        val content = JsonMcpConfigFormat.CLAUDE_CODE.merge(null, listOf(resolve(server, mcpLaunchScript)), setOf("atlassian"), file)
        val entry = Json
            .parseToJsonElement(content)
            .jsonObject
            .getValue("mcpServers")
            .jsonObject
            .getValue("atlassian")
            .jsonObject

        // when
        // - the tool passes the references of the entry as it read them, as a tool does that expands nothing, and a bus address that leads nowhere
        val command = listOf(entry.getValue("command").jsonPrimitive.content) + entry.getValue("args").jsonArray.map { it.jsonPrimitive.content }
        val environment = entry.getValue("env").jsonObject.mapValues { it.value.jsonPrimitive.content } +
            mapOf("PATH" to programs.path, "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/nonexistent/aitools-test-bus")
        val result = start(command, environment)

        // then
        assertThat(result.exitCode).describedAs(result.stderr).isEqualTo(0)
        val report = result.server()
        assertThat(report.pid).isEqualTo(result.pid)
        assertThat(report.arguments).containsExactlyElementsOf(SERVER_ARGUMENTS)
        assertThat(report.environment).containsEntry("JIRA_PAT", KEYRING_VALUE).doesNotContainKey("CONFLUENCE_PAT")
        assertThat(report.environment).containsEntry("JIRA_BASE_URL", "https://jira.example.com")
        assertThat(command).noneMatch { it.contains(KEYRING_VALUE) }
    }

    private fun resolve(server: McpServer, launcherFile: Path = launcher): ResolvedMcpServer {
        val environment =
            mapOf("PATH" to programs.path, "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/nonexistent/aitools-test-bus")
        val source = EnvironmentSource { environment[it] }
        // - the checkout is the directory scripts/mcp-launch lies in; every call of the fake busctl answers at once, far within the limit
        val manager =
            LibsecretSecretsManager(launcherFile.parent.parent.toFile(), source, probe = SecretServiceProbe(source, Duration.ofSeconds(30)))
        return McpServerResolver(variables, McpSecretDelivery(manager, variables)).resolve(server)
    }

    private fun start(command: List<String>, environment: Map<String, String>): LaunchResult {
        val stdout = tempDir.resolve("stdout").toFile()
        val stderr = tempDir.resolve("stderr").toFile()
        val builder = ProcessBuilder(command).directory(tempDir.toFile()).redirectOutput(stdout).redirectError(stderr)
        builder.environment().clear()
        builder.environment().putAll(environment)
        val process = builder.start()
        process.outputStream.close()
        check(process.waitFor(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            "the server did not end within $START_TIMEOUT_SECONDS seconds"
        }
        return LaunchResult(process.exitValue(), stdout.readBytes(), stderr.readText(), process.pid())
    }

    private val atlassian = server(
        McpServerTransport.Stdio(command = SERVER_ARGUMENTS_COMMAND, args = SERVER_ARGUMENTS.map { McpText.literal(it) }, env = emptyMap()),
        McpVariable("JIRA_PAT", "Token", secret = true),
        McpVariable("CONFLUENCE_PAT", "Token", secret = true, required = false),
        McpVariable("PROXY_TOKEN", "Proxy", secret = true, from = SecretSource.ENVIRONMENT),
        McpVariable("JIRA_BASE_URL", "Base URL", secret = false),
    ).copy(id = "atlassian")

    private val plainStdio = server(
        McpServerTransport.Stdio(command = "npx", args = listOf(McpText.literal("-y"), McpText.literal("@playwright/mcp")), env = emptyMap()),
    ).copy(id = "playwright")

    private val environmentOnly = server(
        McpServerTransport.Stdio(command = "/opt/other", args = emptyList(), env = emptyMap()),
        McpVariable("OTHER_TOKEN", "Token", secret = true, from = SecretSource.ENVIRONMENT),
    ).copy(id = "other")

    private val remote = server(
        McpServerTransport.Http(
            McpText(listOf(McpTextPart.Literal("https://api.githubcopilot.com/mcp/"))),
            mapOf("Authorization" to McpHeader.BearerSecret("GITHUB_TOKEN")),
        ),
        McpVariable("GITHUB_TOKEN", "Token", secret = true),
    ).copy(id = "github")

    private fun server(transport: McpServerTransport, vararg variables: McpVariable) = McpServer(
        id = "server",
        description = "A server",
        metadata = ManifestMetadata(version = Version("1.0.0")),
        transport = transport,
        variables = variables.toList(),
    )

    companion object {
        private const val SECRET_VALUE = "s3cr3t-value-that-must-never-be-written"
        private const val KEYRING_VALUE = "keyring-s3cr3t-M4RK3R"
        private const val START_TIMEOUT_SECONDS = 30L
        private const val SERVER_ARGUMENTS_COMMAND = "/opt/jira server/bin/jira-mcp-server"

        // - arguments with a space, both quotes, a separator of their own and a dollar sign, none of which the launcher or a format may change
        private val SERVER_ARGUMENTS = listOf("--name=it's \"quoted\"", "a b", "--", "\$HOME")

        private val LAUNCHER_ARGUMENTS =
            listOf("atlassian", "--required", "JIRA_PAT", "--optional", "CONFLUENCE_PAT", "--", SERVER_ARGUMENTS_COMMAND) + SERVER_ARGUMENTS

        @JvmStatic
        fun jsonFormats(): Stream<Arguments> {
            val claude = { variable: String, required: Boolean -> if (required) "\${$variable}" else "\${$variable:-}" }
            val env = { variable: String, _: Boolean -> "\${env:$variable}" }
            return Stream.of(
                Arguments.of(".mcp.json", JsonMcpConfigFormat.CLAUDE_CODE, "mcpServers", claude),
                Arguments.of("~/.claude.json", JsonMcpConfigFormat.CLAUDE_CODE_USER, "mcpServers", claude),
                Arguments.of(".vscode/mcp.json", JsonMcpConfigFormat.VS_CODE, "servers", env),
                Arguments.of(".cursor/mcp.json", JsonMcpConfigFormat.CURSOR, "mcpServers", env),
            )
        }

        @JvmStatic
        fun allFormats(): Stream<Arguments> = Stream.of(
            Arguments.of(".mcp.json", JsonMcpConfigFormat.CLAUDE_CODE),
            Arguments.of("~/.claude.json", JsonMcpConfigFormat.CLAUDE_CODE_USER),
            Arguments.of(".vscode/mcp.json", JsonMcpConfigFormat.VS_CODE),
            Arguments.of(".cursor/mcp.json", JsonMcpConfigFormat.CURSOR),
            Arguments.of(".codex/config.toml and ~/.codex/config.toml", CodexTomlMcpConfigFormat),
        )
    }
}
