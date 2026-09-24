package cz.cleanship.aitools.engine.tools.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.services.DryRunArtifactSink
import cz.cleanship.aitools.engine.services.ExportService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class McpConfigFileExporterTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var file: File
    private lateinit var projectDir: File

    private val server =
        ResolvedMcpServer("atlassian", ResolvedMcpTransport.Stdio(command = "server", args = emptyList(), env = emptyMap()))

    private val logAppender = ListAppender<ILoggingEvent>()
    private val sinkLogger = LoggerFactory.getLogger(DryRunArtifactSink::class.java) as Logger

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.resolve("project")
        file = projectDir.resolve(".cursor/mcp.json")
        logAppender.start()
        sinkLogger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        sinkLogger.detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
    }

    @Test
    fun `should create the file with the selected servers when it does not exist`() {
        // given
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(listOf(server), setOf("atlassian")))

        // then
        assertThat(file).content().contains("\"atlassian\"").contains("\"command\": \"server\"")
    }

    @Test
    fun `should merge into the file that exists`() {
        // given
        file.parentFile.mkdirs()
        file.writeText("""{ "mcpServers": { "playwright": { "command": "npx" } } }""")
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(listOf(server), setOf("atlassian")))

        // then
        assertThat(file).content().contains("\"playwright\"").contains("\"atlassian\"")
    }

    @Test
    fun `should not create the file when no server is selected`() {
        // given
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(emptyList(), setOf("atlassian")))

        // then
        assertThat(file).doesNotExist()
    }

    @Test
    fun `should leave a file holding no owned entry untouched when no server is selected`() {
        // given
        // - formatting that a rewrite would change
        file.parentFile.mkdirs()
        val existing = """{"mcpServers":{"playwright":{"command":"npx"}}}"""
        file.writeText(existing)
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(emptyList(), setOf("atlassian")))

        // then
        assertThat(file).hasContent(existing)
    }

    @Test
    fun `should remove an owned entry from the file when no server is selected`() {
        // given
        file.parentFile.mkdirs()
        file.writeText("""{"mcpServers":{"atlassian":{"command":"old"},"playwright":{"command":"npx"}}}""")
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(emptyList(), setOf("atlassian")))

        // then
        assertThat(file).content().doesNotContain("atlassian").contains("playwright")
    }

    @Test
    fun `should name the file and the servers without writing anything in a dry run`() {
        // given
        val exporter =
            McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(DryRunArtifactSink), projectDir)

        // when
        exporter.export(McpContext(listOf(server), setOf("atlassian")))

        // then
        assertThat(file).doesNotExist()
        assertThat(file.parentFile).doesNotExist()
        val infos = logAppender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }
        assertThat(infos).singleElement().satisfies({
            assertThat(it).contains("MCP servers [atlassian]").contains(file.absolutePath)
        })
    }

    @Test
    fun `should name the servers it removes as well as those it writes`() {
        // given
        file.parentFile.mkdirs()
        file.writeText("""{"mcpServers":{"retired":{"command":"old"},"playwright":{"command":"npx"}}}""")
        val exporter =
            McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(DryRunArtifactSink), projectDir)

        // when
        exporter.export(McpContext(listOf(server), setOf("atlassian", "retired")))

        // then
        val infos = logAppender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }
        assertThat(infos).singleElement().satisfies({
            assertThat(it).contains("MCP servers [atlassian]").contains("removing [retired]").contains(file.absolutePath)
        })
    }

    @Test
    fun `should keep the permissions of the file it rewrites`() {
        // given
        // - a config file the user restricted to themselves, as one holding a model provider token would be
        file.parentFile.mkdirs()
        file.writeText("""{"mcpServers":{}}""")
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(listOf(server), setOf("atlassian")))

        // then
        assertThat(file).content().contains("atlassian")
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))).isEqualTo("rw-------")
    }

    @Test
    fun `should write through a symbolic link to the file it leads to`() {
        // given
        // - the config file of the project is a link to one shared file inside the project
        val shared = projectDir.resolve("config/shared.json")
        shared.parentFile.mkdirs()
        shared.writeText("""{"mcpServers":{"playwright":{"command":"npx"}}}""")
        file.parentFile.mkdirs()
        Files.createSymbolicLink(file.toPath(), shared.toPath())
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(listOf(server), setOf("atlassian")))

        // then
        assertThat(Files.isSymbolicLink(file.toPath())).isTrue()
        assertThat(shared).content().contains("playwright").contains("atlassian")
    }

    @Test
    fun `should leave the file untouched and fail naming it when it changes while the deploy merges it`() {
        // given
        // - a tool rewrites the file between the read and the write of the deploy
        file.parentFile.mkdirs()
        file.writeText("""{"mcpServers":{}}""")
        val interfering = object : McpConfigFormat by JsonMcpConfigFormat.CURSOR {
            override fun merge(
                existing: String?,
                servers: List<ResolvedMcpServer>,
                ownedMcpIds: Set<String>,
                file: File,
            ): String =
                JsonMcpConfigFormat.CURSOR.merge(existing, servers, ownedMcpIds, file).also { file.writeText("""{"mcpServers":{"written-by-a-tool":{}}}""") }
        }
        val exporter = McpConfigFileExporter(file, interfering, ExportService(), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(file.absolutePath)
        assertThat(file).hasContent("""{"mcpServers":{"written-by-a-tool":{}}}""")
    }

    @Test
    fun `should refuse a symbolic link whose target lies outside the project, naming the file and the target`() {
        // given
        // - the project links its config file to one outside it, such as the config of the user
        val outside = tempDir.resolve("home/.cursor/mcp.json")
        outside.parentFile.mkdirs()
        val existing = """{"mcpServers":{"mine":{"command":"npx"}}}"""
        outside.writeText(existing)
        file.parentFile.mkdirs()
        Files.createSymbolicLink(file.toPath(), outside.toPath())
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(file.absolutePath)
            .hasMessageContaining(outside.canonicalPath)
        assertThat(outside).hasContent(existing)
        assertThat(Files.isSymbolicLink(file.toPath())).isTrue()
    }

    @Test
    @Timeout(10)
    fun `should refuse a FIFO at the config path without opening it`() {
        // given
        file.parentFile.mkdirs()
        val created = runCatching { ProcessBuilder("mkfifo", file.absolutePath).start().waitFor() }.getOrNull()
        // - a FIFO can only be made with mkfifo, which this machine may not have
        assumeTrue(created == 0)
        val exporter =
            McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(DryRunArtifactSink), projectDir)

        // when / then
        // - opening a FIFO blocks until a writer appears, so a refusal that opened it would time out
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(file.absolutePath)
            .hasMessageContaining("not a regular file")
    }

    @Test
    fun `should refuse a directory at the config path`() {
        // given
        file.mkdirs()
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(file.absolutePath)
            .hasMessageContaining("not a regular file")
    }

    @Test
    fun `should refuse a symbolic link that loops, as a failure of this file only`() {
        // given
        file.parentFile.mkdirs()
        val other = file.resolveSibling("other.json")
        Files.createSymbolicLink(file.toPath(), other.toPath())
        Files.createSymbolicLink(other.toPath(), file.toPath())
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(file.absolutePath)
    }

    @ParameterizedTest
    @CsvSource(
        // - a tool directory linked to the one in the home, as dotfile setups do
        ".codex, config.toml",
        ".vscode, mcp.json",
    )
    fun `should refuse a config file whose parent directory links outside the project, naming the file and the target`(
        directory: String,
        fileName: String,
    ) {
        // given
        val outsideDir = tempDir.resolve("home/$directory")
        outsideDir.mkdirs()
        val outside = outsideDir.resolve(fileName)
        val existing = if (fileName.endsWith(".toml")) "approval_policy = \"untrusted\"\n" else """{"servers":{}}"""
        outside.writeText(existing)
        projectDir.mkdirs()
        Files.createSymbolicLink(projectDir.resolve(directory).toPath(), outsideDir.toPath())
        val configFile = projectDir.resolve("$directory/$fileName")
        val format = if (fileName.endsWith(".toml")) CodexTomlMcpConfigFormat else JsonMcpConfigFormat.VS_CODE
        val exporter = McpConfigFileExporter(configFile, format, ExportService(), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(configFile.absolutePath)
            .hasMessageContaining(outsideDir.canonicalPath)
        assertThat(outside).hasContent(existing)
    }

    @Test
    fun `should write through a parent directory that links inside the project`() {
        // given
        val shared = projectDir.resolve("shared/.cursor")
        shared.mkdirs()
        Files.createSymbolicLink(projectDir.resolve(".cursor").toPath(), shared.toPath())
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(listOf(server), setOf("atlassian")))

        // then
        assertThat(shared.resolve("mcp.json")).content().contains("atlassian")
    }
}
