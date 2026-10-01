package cz.cleanship.aitools.engine

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.McpServer
import cz.cleanship.aitools.engine.models.McpServerTransport
import cz.cleanship.aitools.engine.models.McpToolRestriction
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.tools.McpLimits
import cz.cleanship.aitools.engine.tools.mcp.JsonMcpConfigFormat
import cz.cleanship.aitools.engine.tools.mcp.McpConfigExporter
import cz.cleanship.aitools.engine.tools.mcp.McpConfigFileException
import cz.cleanship.aitools.engine.tools.mcp.McpConfigFileExporter
import cz.cleanship.aitools.engine.tools.mcp.McpConfigShadow
import cz.cleanship.aitools.engine.tools.mcp.McpContext
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsContext
import cz.cleanship.aitools.engine.tools.mcp.McpPermissionsExporter
import cz.cleanship.aitools.engine.tools.mcp.McpServerResolver
import cz.cleanship.aitools.engine.tools.mcp.PreparedMcpEdit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * The MCP policy of a run, planned per deployment and tool: which entries each exporter owns, how the ledger records an edit around its commit, and which deployment may write a file several of them cover.
 */
class McpConfigExportPlannerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var projectDir: File
    private lateinit var ledgerFile: File
    private lateinit var planner: McpConfigExportPlanner
    private lateinit var config: FakeConfigExporter
    private lateinit var permissions: FakePermissionsExporter

    private val atlassian = server("atlassian")
    private val restricted = mapOf("atlassian" to McpToolRestriction(allow = listOf("search"), deny = listOf("delete")))
    private val appliesEverything =
        McpLimits(agentServers = null, allowedTools = null, deniedTools = null, userScope = null)

    private val logAppender = ListAppender<ILoggingEvent>()
    private val engineLogger = LoggerFactory.getLogger(ToolsEngine::class.java) as Logger

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.resolve("project").also { it.mkdirs() }
        ledgerFile = projectDir.resolve(".ai-tools/mcp-ledger.json")
        planner = McpConfigExportPlanner(McpServerResolver(VariableResolver()), ExportService())
        config = FakeConfigExporter(projectDir.resolve(".mcp.json"))
        permissions = FakePermissionsExporter(projectDir.resolve(".claude/settings.json"))
        logAppender.start()
        engineLogger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        engineLogger.detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
    }

    @Nested
    inner class Ownership {

        @Test
        fun `should let a deployment that selects servers own every manifest id of the run in the config file, beside the records of the ledger`() {
            // given
            writeLedger(""""files": {".mcp.json": {"gone": "${fingerprint('a')}"}}""")

            // when
            runAll(deployment(servers = listOf(atlassian), ownedMcpIds = setOf("atlassian", "other")))

            // then
            assertThat(config.contexts).singleElement().satisfies({ context ->
                assertThat(context.servers.map { it.id }).containsExactly("atlassian")
                assertThat(context.manifestIds).containsExactlyInAnyOrder("atlassian", "other")
                assertThat(context.recorded).isEqualTo(mapOf("gone" to setOf(fingerprint('a'))))
            })
        }

        @Test
        fun `should hand the config file of a deployment that selects no server every manifest id of the run and the records of the ledger, whose ownership the exporter decides`() {
            // given
            writeLedger(""""files": {".mcp.json": {"gone": "${fingerprint('a')}"}}""")

            // when
            runAll(deployment(servers = emptyList(), ownedMcpIds = setOf("atlassian", "other"), declaresMcps = false))

            // then
            assertThat(config.contexts).singleElement().satisfies({ context ->
                assertThat(context.servers).isEmpty()
                assertThat(context.manifestIds).containsExactlyInAnyOrder("atlassian", "other")
                assertThat(context.recorded).isEqualTo(mapOf("gone" to setOf(fingerprint('a'))))
            })
        }

        @Test
        fun `should hand the permissions file only the denied tools and the records of the ledger when the tool applies no allowed tools, and report the allowed ones as skipped`() {
            // given
            writeLedger(""""files": {".claude/settings.json": {"deny:mcp__atlassian__old": "${fingerprint('b')}"}}""")
            val denyOnly = appliesEverything.copy(allowedTools = "no list of allowed tools")

            // when
            runAll(deployment(servers = listOf(atlassian), restrictions = restricted), limits = denyOnly)

            // then
            assertThat(permissions.contexts).singleElement().satisfies({ context ->
                assertThat(context.restrictions).isEqualTo(mapOf("atlassian" to McpToolRestriction(deny = listOf("delete"))))
                assertThat(context.recorded).isEqualTo(mapOf("deny:mcp__atlassian__old" to setOf(fingerprint('b'))))
                assertThat(context.presumedAbsent).isFalse()
            })
            assertThat(
                config.contexts
                    .single()
                    .servers
                    .single()
                    .tools,
            ).isEqualTo(McpToolRestriction(deny = listOf("delete")))
            assertThat(warnings()).singleElement().satisfies({
                assertThat(it).isEqualTo("p: claude does not apply the 'allow' list of the MCP server(s) [atlassian]: no list of allowed tools.")
            })
        }

        @Test
        fun `should hand a tool that applies both lists the whole restriction`() {
            // when
            runAll(deployment(servers = listOf(atlassian), restrictions = restricted))

            // then
            assertThat(
                config.contexts
                    .single()
                    .servers
                    .single()
                    .tools,
            ).isEqualTo(restricted.getValue("atlassian"))
            assertThat(permissions.contexts.single().restrictions).isEqualTo(restricted)
            assertThat(warnings()).isEmpty()
        }

        @Test
        fun `should plan no permissions file when a deny-only tool is left with nothing but allowed tools and the ledger records nothing there`() {
            // given
            val allowOnly = mapOf("atlassian" to McpToolRestriction(allow = listOf("search")))

            // when
            val planned = planner.exportsFor(deployment(servers = listOf(atlassian), restrictions = allowOnly), ToolType.CLAUDE, listOf(config), permissions, appliesEverything.copy(allowedTools = "no list"))

            // then
            assertThat(planned.map { it.name }).containsExactly("MCP servers [atlassian]")
        }

        @Test
        fun `should report a tool that applies neither list once for the restriction, with its reason`() {
            // when
            planner.exportsFor(deployment(servers = listOf(atlassian), restrictions = restricted), ToolType.CURSOR, listOf(config), null, appliesEverything.copy(allowedTools = "no setting", deniedTools = "no setting"))

            // then
            assertThat(warnings()).containsExactly("p: cursor does not restrict the tools of the MCP server(s) [atlassian]: no setting.")
        }

        @Test
        fun `should plan nothing and read no file for a deployment that selects and restricts nothing and whose ledger records nothing`() {
            // when
            val planned = planner.exportsFor(deployment(servers = emptyList(), declaresMcps = false), ToolType.CLAUDE, listOf(config), permissions, appliesEverything)

            // then
            assertThat(planned).isEmpty()
            assertThat(config.contexts).isEmpty()
            assertThat(permissions.contexts).isEmpty()
        }

        @Test
        fun `should take a permissions file below a path the deploy deletes first as missing`() {
            // when
            runAll(deployment(servers = listOf(atlassian), restrictions = restricted), deletedFirst = listOf(projectDir.resolve(".claude")))

            // then
            assertThat(permissions.contexts.single().presumedAbsent).isTrue()
        }
    }

    /**
     * The ledger is a write-ahead record: what an edit writes is recorded after the edit is prepared and before it is committed, and narrowed to what it wrote once it is.
     */
    @Nested
    inner class LedgerProtocol {

        @Test
        fun `should record what the edit writes beside what the ledger recorded before it commits, and only what it wrote once it committed`() {
            // given
            writeLedger(""""files": {".mcp.json": {"gone": "${fingerprint('a')}", "atlassian": "${fingerprint('b')}"}}""")
            config.entries = mapOf("atlassian" to fingerprint('c'), "new" to fingerprint('d'))
            config.onCommit = { config.ledgerAtCommit = ledger() }

            // when
            runAll(deployment(servers = listOf(atlassian)))

            // then
            // - an entry the edit changes is recorded with what the file holds and with what the edit writes until the commit ran, so it is the engine's whether the commit succeeds or not
            assertThat(config.ledgerAtCommit).isEqualTo(ledgerOf(""""files": {".mcp.json": {"atlassian": ["${fingerprint('b')}", "${fingerprint('c')}"], "gone": "${fingerprint('a')}", "new": "${fingerprint('d')}"}}"""))
            assertThat(ledger()).isEqualTo(ledgerOf(""""files": {".mcp.json": {"atlassian": "${fingerprint('c')}", "new": "${fingerprint('d')}"}}"""))
        }

        @Test
        fun `should leave the ledger as it was when the edit cannot be prepared`() {
            // given
            writeLedger(""""files": {".mcp.json": {"gone": "${fingerprint('a')}"}}""")
            val before = ledgerFile.readText()
            config.failure = McpConfigFileException("'${config.file}' cannot be read")

            // when / then
            assertThatThrownBy { runAll(deployment(servers = listOf(atlassian))) }.isSameAs(config.failure)
            assertThat(ledgerFile).hasContent(before)
        }

        @Test
        fun `should keep the widened record when the commit fails, which names nothing the file does not hold with that content`() {
            // given
            writeLedger(""""files": {".mcp.json": {"gone": "${fingerprint('a')}"}}""")
            config.entries = mapOf("atlassian" to fingerprint('c'))
            config.onCommit = { throw McpConfigFileException("'${config.file}' changed while the engine merged it") }

            // when / then
            assertThatThrownBy { runAll(deployment(servers = listOf(atlassian))) }.isInstanceOf(McpConfigFileException::class.java)
            assertThat(ledger()).isEqualTo(ledgerOf(""""files": {".mcp.json": {"atlassian": "${fingerprint('c')}", "gone": "${fingerprint('a')}"}}"""))
        }

        /**
         * The commit changed the entry and the ledger became unwritable before it could be narrowed, as a concurrent deploy could make it: the ledger still records the entry with what the edit wrote beside what the file held, so a later deploy that no longer selects the server removes it.
         */
        @Test
        fun `should keep owning an entry the commit changed when the ledger cannot be narrowed, so a later deploy without an mcps block still removes it`() {
            // given
            assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            val mcpJson = projectDir.resolve(".mcp.json")
            val real =
                McpConfigFileExporter(mcpJson, JsonMcpConfigFormat.CLAUDE_CODE, ExportService(), TargetRoot.Project(projectDir))
            // - the first run writes the server with its first command, and records it
            runWith(real, deployment(servers = listOf(server("atlassian", command = "first"))))
            val ledgerDirectory = ledgerFile.parentFile.toPath()
            Files.setPosixFilePermissions(ledgerDirectory, PosixFilePermissions.fromString("r-x------"))
            val readOnly = !Files.isWritable(ledgerDirectory)
            Files.setPosixFilePermissions(ledgerDirectory, PosixFilePermissions.fromString("rwx------"))
            assumeTrue(readOnly, "the user running the tests writes every directory")
            // - the second run changes the command; right after its commit, the directory of the ledger becomes read-only
            val lockingLedger = object : McpConfigExporter {
                override val file = mcpJson

                override fun prepare(context: McpContext): PreparedMcpEdit? = real.prepare(context)?.let { edit ->
                    PreparedMcpEdit(edit.file, edit.entries) {
                        edit.commit()
                        Files.setPosixFilePermissions(ledgerDirectory, PosixFilePermissions.fromString("r-x------"))
                    }
                }
            }
            val changed = try {
                assertThatThrownBy { runWith(lockingLedger, deployment(servers = listOf(server("atlassian", command = "second")))) }
                    .isInstanceOf(McpLedgerException::class.java)
                    .hasMessageContaining(ledgerFile.absolutePath)
                mcpJson.readText()
            } finally {
                Files.setPosixFilePermissions(ledgerDirectory, PosixFilePermissions.fromString("rwx------"))
            }
            val pending = ledger()
                .jsonObject
                .getValue("files")
                .jsonObject
                .getValue(".mcp.json")
                .jsonObject
                .getValue("atlassian")

            // when
            runWith(real, deployment(servers = emptyList(), declaresMcps = false))

            // then
            assertThat(changed).contains("second")
            assertThat(pending.jsonArray).hasSize(2)
            assertThat(mcpJson).content().doesNotContain("atlassian")
            assertThat(ledgerFile).doesNotExist()
        }

        @Test
        fun `should drop the record of a file whose edit finds nothing of the engine in it, and delete the ledger left without a file`() {
            // given
            writeLedger(""""files": {".mcp.json": {"gone": "${fingerprint('a')}"}}""")
            config.entries = null

            // when
            runAll(deployment(servers = emptyList(), declaresMcps = false))

            // then
            assertThat(config.contexts).hasSize(1)
            assertThat(ledgerFile).doesNotExist()
        }

        @Test
        fun `should fail every MCP export of the tool with one failure naming the ledger when the ledger cannot be read`() {
            // given
            ledgerFile.parentFile.mkdirs()
            ledgerFile.writeText("{\"version\": 1, \"files\": {\".mcp.json\": [\"atlassian\"]}}")

            // when
            val planned = planner.exportsFor(deployment(servers = listOf(atlassian), restrictions = restricted), ToolType.CLAUDE, listOf(config), permissions, appliesEverything)

            // then
            assertThat(planned.map { it.name }).containsExactly(McpConfigExportPlanner.LEDGER)
            assertThatThrownBy { planned.single().run() }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(ledgerFile.absolutePath)
                .hasMessageContaining("version 1")
            assertThat(config.contexts).isEmpty()
        }

        @Test
        fun `should share one ledger between the roots that lead to the same directory`() {
            // given
            val link = tempDir.resolve("link").toPath()
            Files.createSymbolicLink(link, projectDir.toPath())
            config.entries = mapOf("atlassian" to fingerprint('c'))
            runAll(deployment(servers = listOf(atlassian)))
            val linked = FakeConfigExporter(link.toFile().resolve(".mcp.json")).also { it.entries = null }

            // when
            planner.exportsFor(deployment(servers = emptyList(), declaresMcps = false, root = TargetRoot.Project(link.toFile())), ToolType.CLAUDE, listOf(linked), null, appliesEverything).forEach { it.run() }

            // then
            assertThat(linked.contexts.single().recorded).isEqualTo(mapOf("atlassian" to setOf(fingerprint('c'))))
            assertThat(ledgerFile).doesNotExist()
        }
    }

    /**
     * One MCP file of a target belongs to at most one deployment of a run: the one whose `mcps` block covers it.
     */
    @Nested
    inner class Claims {

        @Test
        fun `should fail every deployment whose mcps block covers a file another one covers too, even through a linked directory, and write nothing`() {
            // given
            val link = tempDir.resolve("link").toPath()
            Files.createSymbolicLink(link, projectDir.toPath())
            val linked = FakeConfigExporter(link.toFile().resolve(".mcp.json"))
            planner.claim(listOf(McpFileClaim(config.file, "project 'p'"), McpFileClaim(linked.file, "project 'q'")))

            // when
            val first = planner.exportsFor(deployment(servers = listOf(atlassian)), ToolType.CLAUDE, listOf(config), null, appliesEverything)
            val second = planner.exportsFor(deployment(id = "q", servers = emptyList(), root = TargetRoot.Project(link.toFile())), ToolType.CLAUDE, listOf(linked), null, appliesEverything)

            // then
            (first + second).forEach { export ->
                assertThatThrownBy { export.run() }
                    .isInstanceOf(ContendedMcpFileException::class.java)
                    .hasMessageContaining("project 'p', project 'q'")
            }
            assertThat(config.contexts + linked.contexts).isEmpty()
            assertThat(errors()).singleElement().satisfies({ assertThat(it).startsWith("Not writing the MCP entries of '").contains("[project 'p', project 'q']") })
        }

        @Test
        fun `should plan nothing for a deployment without an mcps block in a file another deployment covers`() {
            // given
            writeLedger(""""files": {".mcp.json": {"atlassian": "${fingerprint('a')}"}}""")
            planner.claim(listOf(McpFileClaim(config.file, "project 'p'")))

            // when
            val planned = planner.exportsFor(deployment(id = "q", servers = emptyList(), declaresMcps = false), ToolType.CLAUDE, listOf(config), null, appliesEverything)

            // then
            assertThat(planned).isEmpty()
        }

        @Test
        fun `should plan the export of the one deployment that covers a file`() {
            // given
            planner.claim(listOf(McpFileClaim(config.file, "project 'p'")))

            // when
            runAll(deployment(servers = listOf(atlassian)))

            // then
            assertThat(config.contexts).hasSize(1)
            assertThat(errors()).isEmpty()
        }
    }

    /**
     * A tool that ignores its MCP config file while another file lies beside it, as Copilot CLI ignores `.github/mcp.json` while `.mcp.json` exists in the same directory.
     */
    @Nested
    inner class HiddenFiles {

        private lateinit var hidden: FakeConfigExporter
        private lateinit var hiding: File

        @BeforeEach
        fun setUpHiddenFile() {
            hiding = projectDir.resolve(".mcp.json")
            hidden = FakeConfigExporter(projectDir.resolve(".github/mcp.json"), McpConfigShadow(hiding, "Copilot CLI"))
        }

        @Test
        fun `should warn once, naming both files, when it writes servers into a file that a file this deployment does not write hides`() {
            // given
            hiding.writeText("{}")

            // when
            planner.exportsFor(deployment(servers = listOf(atlassian), mcpConfigFiles = setOf(hidden.file)), ToolType.GITHUB_COPILOT, listOf(hidden), null, appliesEverything).forEach { it.run() }

            // then
            assertThat(hidden.contexts).hasSize(1)
            assertThat(warnings()).containsExactly(
                "p: '${hidden.file.absolutePath}' gets the MCP servers [atlassian] for github_copilot, but '${hiding.absolutePath}' exists and this deployment does not write it: Copilot CLI reads only .mcp.json in that directory and ignores .github/mcp.json there. Let this deployment write .mcp.json too, or remove that file.",
            )
        }

        @Test
        fun `should not warn when the same deployment writes the file that hides it, even through a linked directory`() {
            // given
            hiding.writeText("{}")
            val link = tempDir.resolve("link").toPath()
            Files.createSymbolicLink(link, projectDir.toPath())

            // when
            planner.exportsFor(deployment(servers = listOf(atlassian), mcpConfigFiles = setOf(hidden.file, link.toFile().resolve(".mcp.json"))), ToolType.GITHUB_COPILOT, listOf(hidden), null, appliesEverything).forEach { it.run() }

            // then
            assertThat(hidden.contexts).hasSize(1)
            assertThat(warnings()).isEmpty()
        }

        @ParameterizedTest
        @CsvSource(
            // - nothing lies beside the file
            "false, true",
            // - the deployment selects no server, so it only removes what the ledger records
            "true, false",
        )
        fun `should not warn when no file hides it or it writes no server`(
            hidingExists: Boolean,
            selectsServers: Boolean,
        ) {
            // given
            if (hidingExists) hiding.writeText("{}")
            writeLedger(""""files": {".github/mcp.json": {"atlassian": "${fingerprint('a')}"}}""")

            // when
            planner.exportsFor(deployment(servers = if (selectsServers) listOf(atlassian) else emptyList()), ToolType.GITHUB_COPILOT, listOf(hidden), null, appliesEverything).forEach { it.run() }

            // then
            assertThat(hidden.contexts).hasSize(1)
            assertThat(warnings()).isEmpty()
        }

        @Test
        fun `should not warn when the edit of the file fails`() {
            // given
            hiding.writeText("{}")
            hidden.failure = McpConfigFileException("broken")

            // when
            val export = planner.exportsFor(deployment(servers = listOf(atlassian)), ToolType.GITHUB_COPILOT, listOf(hidden), null, appliesEverything).single()

            // then
            assertThatThrownBy { export.run() }.isInstanceOf(McpConfigFileException::class.java)
            assertThat(warnings()).isEmpty()
        }
    }

    @Test
    fun `should plan an export for every MCP config file of a tool, each recorded in the ledger under its own path`() {
        // given
        val second = FakeConfigExporter(projectDir.resolve(".github/mcp.json"))

        // when
        planner.exportsFor(deployment(servers = listOf(atlassian)), ToolType.GITHUB_COPILOT, listOf(config, second), null, appliesEverything).forEach { it.run() }

        // then
        assertThat(config.contexts).hasSize(1)
        assertThat(second.contexts).hasSize(1)
        assertThat(
            ledger()
                .jsonObject
                .getValue("files")
                .jsonObject.keys,
        ).containsExactlyInAnyOrder(".mcp.json", ".github/mcp.json")
    }

    @Test
    fun `should report a tool without an MCP config file as skipped for the servers of a deployment and plan nothing`() {
        // when
        val planned = planner.exportsFor(deployment(servers = listOf(atlassian)), ToolType.WINDSURF, emptyList(), null, appliesEverything)

        // then
        assertThat(planned).isEmpty()
        assertThat(warnings()).containsExactly("p: windsurf has no MCP support in this engine, so the MCP server(s) [atlassian] are not deployed for it.")
    }

    private fun runAll(
        deployment: McpDeployment,
        limits: McpLimits = appliesEverything,
        deletedFirst: List<File> = emptyList(),
    ) = planner.exportsFor(deployment, ToolType.CLAUDE, listOf(config), permissions, limits, deletedFirst).forEach { it.run() }

    // A test builder: every parameter is one field of the deployment with the default a test rarely needs to change.
    @Suppress("LongParameterList")
    private fun deployment(
        id: String = "p",
        servers: List<McpServer>,
        ownedMcpIds: Set<String> = servers.map { it.id }.toSet(),
        restrictions: Map<String, McpToolRestriction> = emptyMap(),
        declaresMcps: Boolean = true,
        root: TargetRoot = TargetRoot.Project(projectDir),
        mcpConfigFiles: Set<File> = emptySet(),
    ) = McpDeployment(id, root, servers.associateBy { it.id }, ownedMcpIds, restrictions, declaresMcps, mcpConfigFiles)

    // A run of its own: a planner holds the ledgers of one run, so each run gets a new one.
    private fun runWith(exporter: McpConfigExporter, deployment: McpDeployment) =
        McpConfigExportPlanner(McpServerResolver(VariableResolver()), ExportService())
            .exportsFor(deployment, ToolType.CLAUDE, listOf(exporter), null, appliesEverything)
            .forEach { it.run() }

    private fun server(id: String, command: String = "$id-server") = McpServer(
        id = id,
        description = id,
        metadata = ManifestMetadata(version = Version("1.0.0")),
        transport = McpServerTransport.Stdio(command = command, args = emptyList(), env = emptyMap()),
        variables = emptyList(),
    )

    private fun writeLedger(files: String) {
        ledgerFile.parentFile.mkdirs()
        ledgerFile.writeText("{\"version\": 2, $files}")
    }

    private fun ledger(): JsonElement = Json.parseToJsonElement(ledgerFile.readText())

    private fun ledgerOf(files: String): JsonElement = Json.parseToJsonElement("{\"version\": 2, $files}")

    private fun fingerprint(digit: Char) = "sha256:" + digit.toString().repeat(64)

    private fun warnings() = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun errors() = logAppender.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }

    /**
     * A config exporter that records the contexts it is given and prepares an edit writing [entries], or none when it is `null`.
     */
    private class FakeConfigExporter(
        override val file: File,
        override val hiddenBy: McpConfigShadow? = null,
    ) : McpConfigExporter {
        val contexts = mutableListOf<McpContext>()
        var entries: Map<String, String>? = mapOf("atlassian" to "sha256:" + "e".repeat(64))
        var failure: McpConfigFileException? = null
        var onCommit: () -> Unit = {}
        var ledgerAtCommit: JsonElement? = null

        override fun prepare(context: McpContext): PreparedMcpEdit? {
            contexts += context
            failure?.let { throw it }
            return entries?.let { PreparedMcpEdit(file, it) { onCommit() } }
        }
    }

    /**
     * A permissions exporter that records the contexts it is given and prepares an edit writing one denied entry per restricted server.
     */
    private class FakePermissionsExporter(override val file: File) : McpPermissionsExporter {
        val contexts = mutableListOf<McpPermissionsContext>()

        override fun prepare(context: McpPermissionsContext): PreparedMcpEdit? {
            contexts += context
            return PreparedMcpEdit(file, context.restrictions.keys.associate { "deny:mcp__${it}__x" to "sha256:" + "f".repeat(64) }) {}
        }
    }
}
