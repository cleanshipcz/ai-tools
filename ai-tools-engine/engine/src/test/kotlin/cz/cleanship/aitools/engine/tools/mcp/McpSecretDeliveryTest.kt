package cz.cleanship.aitools.engine.tools.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.McpHeader
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpText
import cz.cleanship.aitools.engine.models.McpVariable
import cz.cleanship.aitools.engine.models.SecretSource
import cz.cleanship.aitools.engine.models.Version
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory

/**
 * The step between resolving a server and formatting it: which secrets the secrets manager supplies, how their server is started, and what a run reports about each secret.
 */
class McpSecretDeliveryTest {

    private lateinit var manager: McpSecretsManager

    // - the environment of the run sets JIRA_PAT only, to a value no message may repeat
    private val variables =
        VariableResolver(emptyMap(), environment = { name -> mapOf("JIRA_PAT" to SECRET_VALUE)[name] })

    private val logAppender = ListAppender<ILoggingEvent>()

    // The reports are logged under the resolver, which logged the warning about an unset secret before the secrets manager existed.
    private val resolverLogger = LoggerFactory.getLogger(McpServerResolver::class.java) as Logger

    // - a required and an optional secret the manager supplies, a secret read from the environment only, and a plain variable
    private val server = McpServer(
        id = "atlassian",
        description = "Jira",
        metadata = ManifestMetadata(version = Version("1.0.0")),
        transport = McpServerTransport.Stdio(command = "/opt/jira", args = listOf(McpText.literal("--verbose")), env = emptyMap()),
        variables = listOf(
            McpVariable("JIRA_PAT", "Token", secret = true),
            McpVariable("CONFLUENCE_PAT", "Token", secret = true, required = false),
            McpVariable("PROXY_TOKEN", "Proxy", secret = true, from = SecretSource.ENVIRONMENT),
            McpVariable("JIRA_BASE_URL", "Base URL", secret = false),
        ),
    )

    private val resolvedTransport = ResolvedMcpTransport.Stdio(
        command = "/opt/jira",
        args = listOf("--verbose"),
        env = linkedMapOf(
            "JIRA_PAT" to McpValue.Secret("JIRA_PAT", required = true),
            "CONFLUENCE_PAT" to McpValue.Secret("CONFLUENCE_PAT", required = false),
            "PROXY_TOKEN" to McpValue.Secret("PROXY_TOKEN", required = true),
            "JIRA_BASE_URL" to McpValue.Plain("https://jira.example.com"),
        ),
    )

    @BeforeEach
    fun setUp() {
        manager = mockk()
        every { manager.label } returns "the keyring"
        every { manager.countsAsSet("JIRA_PAT", SECRET_VALUE) } returns true
        logAppender.start()
        resolverLogger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        resolverLogger.detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
    }

    @Nested
    inner class Deliver {

        @Test
        fun `should start a stdio server through the manager, naming only the secrets the manager supplies, in the order of the entry`() {
            // given
            val launched = ResolvedMcpTransport.Stdio(command = "/repo/scripts/mcp-launch", args = listOf("atlassian"), env = emptyMap())
            every {
                manager.launch("atlassian", resolvedTransport, listOf(McpValue.Secret("JIRA_PAT", required = true), McpValue.Secret("CONFLUENCE_PAT", required = false)))
            } returns launched

            // when
            val delivered = McpSecretDelivery(manager, variables).deliver(server, ResolvedMcpServer("atlassian", resolvedTransport))

            // then
            assertThat(delivered).isEqualTo(ResolvedMcpServer("atlassian", launched))
        }

        @Test
        fun `should leave a stdio server whose every secret is read from the environment as it is`() {
            // given
            val environmentOnly = server.copy(variables = listOf(McpVariable("PROXY_TOKEN", "Proxy", secret = true, from = SecretSource.ENVIRONMENT)))
            val resolved =
                ResolvedMcpServer("atlassian", resolvedTransport.copy(env = linkedMapOf("PROXY_TOKEN" to McpValue.Secret("PROXY_TOKEN", required = true))))

            // when
            val delivered = McpSecretDelivery(manager, variables).deliver(environmentOnly, resolved)

            // then
            assertThat(delivered).isSameAs(resolved)
            verify { manager wasNot Called }
        }

        @Test
        fun `should leave a stdio server without secrets as it is`() {
            // given
            val plain = server.copy(variables = listOf(McpVariable("JIRA_BASE_URL", "Base URL", secret = false)))
            val resolved =
                ResolvedMcpServer("atlassian", resolvedTransport.copy(env = linkedMapOf("JIRA_BASE_URL" to McpValue.Plain("https://jira.example.com"))))

            // when
            val delivered = McpSecretDelivery(manager, variables).deliver(plain, resolved)

            // then
            assertThat(delivered).isSameAs(resolved)
            verify { manager wasNot Called }
        }

        @Test
        fun `should leave an http server as it is, since its secrets are sent by the tool`() {
            // given
            val remote = server.copy(
                transport = McpServerTransport.Http(McpText.literal("https://mcp.example.com/mcp"), mapOf("Authorization" to McpHeader.BearerSecret("TOKEN"))),
                variables = listOf(McpVariable("TOKEN", "Token", secret = true)),
            )
            val resolved =
                ResolvedMcpServer("atlassian", ResolvedMcpTransport.Http("https://mcp.example.com/mcp", linkedMapOf("Authorization" to McpValue.BearerSecret("TOKEN", required = true))))

            // when
            val delivered = McpSecretDelivery(manager, variables).deliver(remote, resolved)

            // then
            assertThat(delivered).isSameAs(resolved)
            verify { manager wasNot Called }
        }

        @Test
        fun `should leave every server as it is when the machine uses no secrets manager`() {
            // given
            val resolved = ResolvedMcpServer("atlassian", resolvedTransport)

            // when
            val delivered = McpSecretDelivery(null, variables).deliver(server, resolved)

            // then
            assertThat(delivered).isSameAs(resolved)
        }
    }

    @Nested
    inner class Report {

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = [
                "JIRA_PAT       | stored  | INFO | MCP server 'atlassian' reads the required secret variable 'JIRA_PAT' from the keyring, which holds it.",
                "JIRA_PAT       | absent  | INFO | MCP server 'atlassian' reads the required secret variable 'JIRA_PAT' from the environment of the tool that starts it: the keyring does not hold it, and the environment of this run sets it.",
                "CONFLUENCE_PAT | absent  | WARN | MCP server 'atlassian' reads the optional secret variable 'CONFLUENCE_PAT' from the keyring or the environment of the tool that starts it, but the keyring does not hold it and the environment of this run does not set it. Store it with: store CONFLUENCE_PAT",
                "CONFLUENCE_PAT | unknown | WARN | MCP server 'atlassian' reads the optional secret variable 'CONFLUENCE_PAT' from the keyring, and the engine cannot tell whether the keyring holds it: busctl is not on the PATH.",
            ],
        )
        fun `should report exactly one of four states for each secret the manager supplies`(
            name: String,
            presence: String,
            level: String,
            message: String,
        ) {
            // given
            val single = server.copy(variables = server.variables.filter { it.name == name })
            every { manager.presenceOf(name) } returns when (presence) {
                "stored" -> SecretPresence.Stored
                "absent" -> SecretPresence.Absent
                else -> SecretPresence.Unknown("busctl is not on the PATH")
            }
            every { manager.storeCommand(name) } returns "store $name"

            // when
            McpSecretDelivery(manager, variables).report(single)

            // then
            assertThat(logAppender.list.map { it.level.toString() to it.formattedMessage }).containsExactly(level to message)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "\${JIRA_PAT}", "\${JIRA_PAT:-}", "\${env:JIRA_PAT}"])
        fun `should report a secret the manager does not hold as found nowhere when the environment holds only a value the manager counts as not set`(
            value: String,
        ) {
            // given
            val single = server.copy(variables = server.variables.filter { it.name == "JIRA_PAT" })
            val environment = VariableResolver(emptyMap(), environment = { name -> mapOf("JIRA_PAT" to value)[name] })
            every { manager.presenceOf("JIRA_PAT") } returns SecretPresence.Absent
            every { manager.countsAsSet("JIRA_PAT", value) } returns false
            every { manager.storeCommand("JIRA_PAT") } returns "store JIRA_PAT"

            // when
            McpSecretDelivery(manager, environment).report(single)

            // then
            assertThat(logAppender.list.map { it.level to it.formattedMessage }).containsExactly(
                Level.WARN to
                    "MCP server 'atlassian' reads the required secret variable 'JIRA_PAT' from the keyring or the environment of the tool that starts it, " +
                    "but the keyring does not hold it and the environment of this run sets it empty or to its own unexpanded reference, which counts as not set. Store it with: store JIRA_PAT",
            )
        }

        @Test
        fun `should keep the warning about an unset secret for a secret read from the environment only, and not ask the manager about it`() {
            // given
            val environmentOnly = server.copy(variables = server.variables.filter { it.name == "PROXY_TOKEN" })

            // when
            McpSecretDelivery(manager, variables).report(environmentOnly)

            // then
            assertThat(logAppender.list.map { it.level to it.formattedMessage }).containsExactly(
                Level.WARN to
                    "MCP server 'atlassian' reads the required secret variable 'PROXY_TOKEN' from the environment of the tool that starts it, and the environment of this run does not set it. Export it before starting the tool.",
            )
            verify { manager wasNot Called }
        }

        @Test
        fun `should keep the warning about an unset secret for every secret of an http server`() {
            // given
            val remote = server.copy(
                transport = McpServerTransport.Http(McpText.literal("https://mcp.example.com/mcp"), mapOf("Authorization" to McpHeader.BearerSecret("TOKEN"))),
                variables = listOf(McpVariable("TOKEN", "Token", secret = true)),
            )

            // when
            McpSecretDelivery(manager, variables).report(remote)

            // then
            assertThat(logAppender.list.map { it.formattedMessage }).singleElement().satisfies({ assertThat(it).contains("'TOKEN'").contains("Export it before starting the tool.") })
            verify { manager wasNot Called }
        }

        @Test
        fun `should report every secret as before when the machine uses no secrets manager`() {
            // when
            McpSecretDelivery(null, variables).report(server)

            // then
            // - JIRA_PAT is set in the environment of the run; the other two are not
            assertThat(logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }).hasSize(2).allSatisfy {
                assertThat(it).contains("Export it before starting the tool.")
            }
            assertThat(logAppender.list.map { it.formattedMessage }).noneMatch { it.contains(SECRET_VALUE) || it.contains("JIRA_PAT") }
        }

        @Test
        fun `should never name a value of the environment in a report`() {
            // given
            every { manager.presenceOf("JIRA_PAT") } returns SecretPresence.Absent
            every { manager.presenceOf("CONFLUENCE_PAT") } returns SecretPresence.Unknown("no D-Bus session bus is known")

            // when
            McpSecretDelivery(manager, variables).report(server)

            // then
            assertThat(logAppender.list).hasSize(3)
            assertThat(logAppender.list.map { it.formattedMessage }).noneMatch { it.contains(SECRET_VALUE) }
        }
    }

    companion object {
        private const val SECRET_VALUE = "s3cr3t-value-that-must-never-be-logged"
    }
}
