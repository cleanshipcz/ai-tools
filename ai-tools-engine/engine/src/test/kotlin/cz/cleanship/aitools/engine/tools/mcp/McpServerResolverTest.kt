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
import cz.cleanship.aitools.engine.models.McpTextPart
import cz.cleanship.aitools.engine.models.McpVariable
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory

class McpServerResolverTest {

    // - the config of the run declares a base URL; the environment of the run carries a plain value and a secret, and neither may reach a rendered value
    private val variables = VariableResolver(
        variables = mapOf("JIRA_BASE_URL" to "https://jira.example.com", "NESTED" to "https://proxy/\${OTHER}?pw=$CONFIG_VALUE"),
        environment = { name -> mapOf("JIRA_VERIFY_SSL" to ENVIRONMENT_VALUE, "JIRA_PAT" to SECRET_VALUE)[name] },
    )

    private lateinit var resolver: McpServerResolver

    private val logAppender = ListAppender<ILoggingEvent>()
    private val resolverLogger = LoggerFactory.getLogger(McpServerResolver::class.java) as Logger

    @BeforeEach
    fun setUp() {
        resolver = McpServerResolver(variables)
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
    inner class StdioServers {

        @Test
        fun `should pass every variable in the environment of the server, resolving plain ones from the config and referencing secret ones`() {
            // given
            val server = stdio(
                McpVariable("JIRA_PAT", "Token", secret = true),
                McpVariable("JIRA_BASE_URL", "Base URL", secret = false),
            )

            // when
            val resolved = resolver.resolve(server)

            // then
            assertThat(resolved.id).isEqualTo("atlassian")
            assertThat(resolved.transport).isEqualTo(
                ResolvedMcpTransport.Stdio(
                    command = "/work/jira-mcp-server",
                    args = listOf("--url=https://jira.example.com"),
                    env = linkedMapOf(
                        "LOG_LEVEL" to McpValue.Plain("info"),
                        "JIRA_PAT" to McpValue.Secret("JIRA_PAT", required = true),
                        "JIRA_BASE_URL" to McpValue.Plain("https://jira.example.com"),
                    ),
                ),
            )
        }

        @Test
        fun `should never read a plain value from the environment of the run`() {
            // given
            // - the deploying shell exports JIRA_VERIFY_SSL, the config does not declare it
            val server =
                stdio(McpVariable("JIRA_BASE_URL", "Base URL", secret = false), McpVariable("JIRA_VERIFY_SSL", "Verify", secret = false, required = false))

            // when
            val resolved = resolver.resolve(server)

            // then
            assertThat((resolved.transport as ResolvedMcpTransport.Stdio).env).doesNotContainKey("JIRA_VERIFY_SSL")
            assertThat(resolved.toString()).doesNotContain(ENVIRONMENT_VALUE)
        }

        @Test
        fun `should never read the value of a secret variable`() {
            // given
            val server =
                stdio(McpVariable("JIRA_BASE_URL", "Base URL", secret = false), McpVariable("JIRA_PAT", "Token", secret = true))

            // when
            val resolved = resolver.resolve(server)

            // then
            assertThat(resolved.toString()).doesNotContain(SECRET_VALUE)
        }

        @Test
        fun `should fail naming the server and the variable when a required plain variable is declared nowhere in the config`() {
            // given
            // - the environment carries it, which is not a source of plain values
            val server =
                stdio(McpVariable("JIRA_BASE_URL", "Base URL", secret = false), McpVariable("JIRA_VERIFY_SSL", "Verify", secret = false))

            // when / then
            assertThatThrownBy { resolver.resolve(server) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'atlassian'")
                .hasMessageContaining("'JIRA_VERIFY_SSL'")
                .hasMessageNotContaining(ENVIRONMENT_VALUE)
        }

        @Test
        fun `should fail naming the variable when an optional plain variable declared nowhere is part of an argument`() {
            // given
            // - an argument cannot be left out without changing what the rest of the command line means
            val server = server(
                McpServerTransport.Stdio(command = "server", args = listOf(McpText(listOf(McpTextPart.Literal("--proxy="), McpTextPart.Variable("PROXY")))), env = emptyMap()),
                McpVariable("PROXY", "Proxy", secret = false, required = false),
            )

            // when / then
            // - the only remedy that works is a value in the config: marking the variable required fails with the message for a required one instead
            assertThatThrownBy { resolver.resolve(server) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'PROXY'")
                .hasMessageEndingWith("Declare it under 'env_vars:'.")
                .hasMessageNotContaining("required: true")
        }

        @Test
        fun `should fail without echoing the value when a plain value carries a reference a tool would expand`() {
            // given
            val server =
                server(McpServerTransport.Stdio(command = "server", args = emptyList(), env = emptyMap()), McpVariable("NESTED", "Proxy", secret = false))

            // when / then
            assertThatThrownBy { resolver.resolve(server) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'NESTED'")
                .hasMessageNotContaining(CONFIG_VALUE)
                .hasMessageNotContaining("https://proxy")
        }

        @Test
        fun `should refuse a secret variable referenced in an argument of a server the loader did not build, without reading it`() {
            // given
            // - the loader never lets this through; the guard keeps a server built any other way from writing a secret
            val server = server(
                McpServerTransport.Stdio(command = "server", args = listOf(McpText(listOf(McpTextPart.Variable("JIRA_PAT")))), env = emptyMap()),
                McpVariable("JIRA_PAT", "Token", secret = true),
            )

            // when / then
            assertThatThrownBy { resolver.resolve(server) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'JIRA_PAT'")
                .hasMessageNotContaining(SECRET_VALUE)
        }

        @Test
        fun `should warn naming the variable but not its value when a secret is not set in the environment of the run`() {
            // given
            val server =
                stdio(McpVariable("JIRA_BASE_URL", "Base URL", secret = false), McpVariable("CONFLUENCE_PAT", "Token", secret = true, required = false))

            // when
            resolver.resolve(server)

            // then
            val warnings = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
            assertThat(warnings).singleElement().satisfies({
                assertThat(it).contains("'atlassian'").contains("'CONFLUENCE_PAT'")
            })
        }

        @Test
        fun `should not warn when a secret is set in the environment of the run`() {
            // given
            val server =
                stdio(McpVariable("JIRA_BASE_URL", "Base URL", secret = false), McpVariable("JIRA_PAT", "Token", secret = true))

            // when
            resolver.resolve(server)

            // then
            assertThat(logAppender.list.filter { it.level == Level.WARN }).isEmpty()
        }
    }

    @Nested
    inner class HttpServers {

        @Test
        fun `should render a whole secret header, a bearer secret and a resolved plain header`() {
            // given
            val server = server(
                McpServerTransport.Http(
                    url = McpText(listOf(McpTextPart.Variable("JIRA_BASE_URL"), McpTextPart.Literal("/mcp"))),
                    headers = linkedMapOf(
                        "Authorization" to McpHeader.BearerSecret("GITHUB_TOKEN"),
                        "X-Api-Key" to McpHeader.Secret("API_KEY"),
                        "X-Tenant" to McpHeader.Text(McpText(listOf(McpTextPart.Variable("JIRA_BASE_URL"), McpTextPart.Literal("/tenant")))),
                    ),
                ),
                McpVariable("GITHUB_TOKEN", "Token", secret = true),
                McpVariable("API_KEY", "Key", secret = true, required = false),
                McpVariable("JIRA_BASE_URL", "Base", secret = false),
            )

            // when
            val resolved = resolver.resolve(server)

            // then
            assertThat(resolved.transport).isEqualTo(
                ResolvedMcpTransport.Http(
                    url = "https://jira.example.com/mcp",
                    headers = linkedMapOf(
                        "Authorization" to McpValue.BearerSecret("GITHUB_TOKEN", required = true),
                        "X-Api-Key" to McpValue.Secret("API_KEY", required = false),
                        "X-Tenant" to McpValue.Plain("https://jira.example.com/tenant"),
                    ),
                ),
            )
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                // - credentials in the url, which every tool would write and send
                "https://user:pw-CONFIG-SECRET@host.example/mcp | false | Give 'BASE' a value in 'env_vars:' that makes the url start with 'http://' or 'https://' followed by a host, without a backslash or credentials.",
                // - a secret header sent in clear text
                "http://host.example/mcp | true | Give 'BASE' a value in 'env_vars:' that makes the url start with 'https://'.",
            ],
        )
        fun `should refuse a resolved url that carries userinfo or sends a secret header in clear text, without repeating it`(
            url: String,
            secretHeader: Boolean,
            remedy: String,
        ) {
            // given
            val resolver = McpServerResolver(VariableResolver(variables = mapOf("BASE" to url), environment = { null }))
            val headers = if (secretHeader) mapOf<String, McpHeader>("Authorization" to McpHeader.BearerSecret("TOKEN")) else emptyMap()
            val server = server(
                McpServerTransport.Http(url = McpText(listOf(McpTextPart.Variable("BASE"))), headers = headers),
                McpVariable("BASE", "Base", secret = false),
                McpVariable("TOKEN", "Token", secret = true, required = false),
            )

            // when / then
            assertThatThrownBy { resolver.resolve(server) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'remote'")
                .hasMessageContaining("'BASE'")
                .hasMessageEndingWith(remedy)
                .hasMessageNotContaining("host.example")
                .hasMessageNotContaining("pw-CONFIG-SECRET")
        }

        @ParameterizedTest
        @CsvSource(
            // - no scheme at all, which no tool can connect to
            "host.example/mcp",
            // - a scheme other than http and https
            "ftp://host.example/mcp",
            "ws://host.example/mcp",
        )
        fun `should refuse a url resolved from the config without an http or https scheme, naming the server and the variable but not the value`(
            url: String,
        ) {
            // given
            val resolver = McpServerResolver(VariableResolver(variables = mapOf("BASE" to url), environment = { null }))
            val server = server(
                McpServerTransport.Http(url = McpText(listOf(McpTextPart.Variable("BASE"))), headers = emptyMap()),
                McpVariable("BASE", "Base", secret = false),
            )

            // when / then
            assertThatThrownBy { resolver.resolve(server) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'remote'")
                .hasMessageContaining("'BASE'")
                .hasMessageEndingWith("Give 'BASE' a value in 'env_vars:' that makes the url start with 'http://' or 'https://' followed by a host, without a backslash or credentials.")
                .hasMessageNotContaining("host.example")
        }

        @Test
        fun `should end with one remedy naming the required form when a url that references no variable is refused`() {
            // given
            // - a server the loader did not build, since the loader refuses such a url already
            val server =
                server(McpServerTransport.Http(url = McpText.literal("https://user:pw@host.example/mcp"), headers = emptyMap()))

            // when / then
            assertThatThrownBy { resolver.resolve(server) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageStartingWith("MCP server 'remote' resolves its 'url' to one that carries credentials, which every tool would write into its config file and send.")
                .hasMessageEndingWith(" Write a 'url' that starts with 'http://' or 'https://' followed by a host, without a backslash or credentials.")
                .hasMessageNotContaining("Pass them")
                .hasMessageNotContaining("host.example")
        }

        @Test
        fun `should name a variable once in the remedy when the url references it more than once`() {
            // given
            val resolver =
                McpServerResolver(VariableResolver(variables = mapOf("BASE" to "host.example"), environment = { null }))
            val server = server(
                McpServerTransport.Http(url = McpText(listOf(McpTextPart.Variable("BASE"), McpTextPart.Literal("/"), McpTextPart.Variable("BASE"))), headers = emptyMap()),
                McpVariable("BASE", "Base", secret = false),
            )

            // when / then
            assertThatThrownBy { resolver.resolve(server) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("resolves its 'url' from 'BASE' to one that")
                .hasMessageEndingWith("Give 'BASE' a value in 'env_vars:' that makes the url start with 'http://' or 'https://' followed by a host, without a backslash or credentials.")
                .hasMessageNotContaining("host.example")
        }

        @ParameterizedTest
        @CsvSource(
            // - plain http is accepted for a server that sends no secret
            "http://host.example/mcp",
            // - the scheme is matched without regard to case, as every tool does
            "HTTPS://host.example/mcp",
        )
        fun `should accept a url resolved from the config with an http or https scheme`(url: String) {
            // given
            val resolver = McpServerResolver(VariableResolver(variables = mapOf("BASE" to url), environment = { null }))
            val server = server(
                McpServerTransport.Http(url = McpText(listOf(McpTextPart.Variable("BASE"))), headers = emptyMap()),
                McpVariable("BASE", "Base", secret = false),
            )

            // when
            val resolved = resolver.resolve(server)

            // then
            assertThat((resolved.transport as ResolvedMcpTransport.Http).url).isEqualTo(url)
        }

        @Test
        fun `should leave out a header whose optional plain variable the config does not declare`() {
            // given
            val server = server(
                McpServerTransport.Http(
                    url = McpText.literal("https://example.com/mcp"),
                    headers = mapOf("X-Proxy" to McpHeader.Text(McpText(listOf(McpTextPart.Variable("HTTPS_PROXY"))))),
                ),
                McpVariable("HTTPS_PROXY", "Proxy", secret = false, required = false),
            )

            // when
            val resolved = resolver.resolve(server)

            // then
            assertThat((resolved.transport as ResolvedMcpTransport.Http).headers).isEmpty()
        }
    }

    private fun stdio(vararg variables: McpVariable) = server(
        McpServerTransport.Stdio(
            command = "/work/jira-mcp-server",
            args = listOf(McpText(listOf(McpTextPart.Literal("--url="), McpTextPart.Variable("JIRA_BASE_URL")))),
            env = mapOf("LOG_LEVEL" to McpText.literal("info")),
        ),
        *variables,
    )

    private fun server(transport: McpServerTransport, vararg variables: McpVariable) = McpServer(
        id = if (transport is McpServerTransport.Http) "remote" else "atlassian",
        description = "Jira",
        metadata = ManifestMetadata(version = Version("1.0.0")),
        transport = transport,
        variables = variables.toList(),
    )

    companion object {
        private const val SECRET_VALUE = "s3cr3t-value-that-must-never-be-written"
        private const val ENVIRONMENT_VALUE = "value-exported-by-the-deploying-shell"
        private const val CONFIG_VALUE = "hunter2"
    }
}
