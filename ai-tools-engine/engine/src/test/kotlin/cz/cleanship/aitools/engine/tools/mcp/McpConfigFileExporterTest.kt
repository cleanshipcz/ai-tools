package cz.cleanship.aitools.engine.tools.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.services.ArtifactSink
import cz.cleanship.aitools.engine.services.ConfigFileState
import cz.cleanship.aitools.engine.services.DryRunArtifactSink
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.services.FileSystemArtifactSink
import cz.cleanship.aitools.engine.services.NewConfigFileMode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
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
        val existing = """{"mcpServers":{"atlassian":{"command":"old"},"playwright":{"command":"npx"}}}"""
        file.writeText(existing)
        // - without a selection, an entry is the engine's only while the ledger records what it holds
        val recorded = JsonMcpConfigFormat.CURSOR
            .entryFingerprints(existing, file)
            .filterKeys { it == "atlassian" }
            .asRecorded()
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        exporter.export(McpContext(emptyList(), setOf("atlassian"), recorded))

        // then
        assertThat(file).content().doesNotContain("atlassian").contains("playwright")
    }

    @Test
    fun `should leave an entry named after a manifest untouched when no server is selected and the ledger does not record it`() {
        // given
        file.parentFile.mkdirs()
        val existing = """{"mcpServers":{"atlassian":{"command":"mine"},"playwright":{"command":"npx"}}}"""
        file.writeText(existing)
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when
        val prepared = exporter.prepare(McpContext(emptyList(), setOf("atlassian")))

        // then
        assertThat(prepared).isNull()
        assertThat(file).hasContent(existing)
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

    /**
     * The MCP ledger names the entries the engine wrote with the fingerprint of what it wrote: only an entry that still holds exactly that is the engine's to remove.
     */
    @Nested
    inner class RecordedEntries {

        private val format = JsonMcpConfigFormat.CURSOR
        private val retired = """{"mcpServers":{"retired":{"command":"old"},"playwright":{"command":"npx"}}}"""
        private val ledgerLogger = LoggerFactory.getLogger(McpLedger::class.java) as Logger
        private val warnings = ListAppender<ILoggingEvent>()

        @BeforeEach
        fun setUp() {
            file.parentFile.mkdirs()
            warnings.start()
            ledgerLogger.addAppender(warnings)
        }

        @AfterEach
        fun tearDown() {
            ledgerLogger.detachAppender(warnings)
            warnings.stop()
        }

        @Test
        fun `should remove an entry the ledger records while it still holds what the engine wrote`() {
            // given
            file.writeText(retired)
            val recorded = format.entryFingerprints(retired, file).filterKeys { it == "retired" }.asRecorded()
            val exporter = McpConfigFileExporter(file, format, ExportService(), projectDir)

            // when
            exporter.export(McpContext(emptyList(), manifestIds = emptySet(), recorded = recorded))

            // then
            assertThat(file).content().doesNotContain("retired").contains("playwright")
            assertThat(warnings.list).isEmpty()
        }

        /**
         * A ledger written while an edit was pending records both what the file held and what the edit writes; the entry is the engine's whichever of the two it holds.
         */
        @Test
        fun `should remove an entry the ledger records with several fingerprints while it holds one of them`() {
            // given
            file.writeText(retired)
            val recorded =
                mapOf("retired" to setOf("sha256:" + "0".repeat(64)) + format.entryFingerprints(retired, file).getValue("retired"))
            val exporter = McpConfigFileExporter(file, format, ExportService(), projectDir)

            // when
            exporter.export(McpContext(emptyList(), manifestIds = emptySet(), recorded = recorded))

            // then
            assertThat(file).content().doesNotContain("retired").contains("playwright")
            assertThat(warnings.list).isEmpty()
        }

        @Test
        fun `should leave an entry the ledger records in place once it changed, warning with the file and the entry`() {
            // given
            val recorded = format.entryFingerprints(retired, file).filterKeys { it == "retired" }.asRecorded()
            // - the entry was edited by hand after the engine wrote it
            val edited = """{"mcpServers":{"retired":{"command":"mine"},"playwright":{"command":"npx"}}}"""
            file.writeText(edited)
            val exporter = McpConfigFileExporter(file, format, ExportService(), projectDir)

            // when
            val prepared = exporter.prepare(McpContext(emptyList(), manifestIds = emptySet(), recorded = recorded))

            // then
            assertThat(prepared).isNull()
            assertThat(file).hasContent(edited)
            assertThat(warnings.list.filter { it.level == Level.WARN }.map { it.formattedMessage }).singleElement().satisfies({
                assertThat(it).contains(file.absolutePath).contains("'retired'").contains("changed since the engine wrote it")
            })
        }

        @Test
        fun `should remove nothing on the word of a ledger whose fingerprints the entries do not hold, as one the engine did not write`() {
            // given
            file.writeText(retired)
            val planted =
                mapOf("retired" to setOf("sha256:" + "0".repeat(64)), "playwright" to setOf("sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64)))
            val exporter = McpConfigFileExporter(file, format, ExportService(DryRunArtifactSink), projectDir)

            // when
            val prepared = exporter.prepare(McpContext(emptyList(), manifestIds = emptySet(), recorded = planted))

            // then
            assertThat(prepared).isNull()
            assertThat(logAppender.list).isEmpty()
        }

        @Test
        fun `should write nothing until the prepared edit is committed, and name what the file then holds of the engine by fingerprint`() {
            // given
            file.writeText(retired)
            val exporter = McpConfigFileExporter(file, format, ExportService(), projectDir)

            // when
            val prepared =
                requireNotNull(exporter.prepare(McpContext(listOf(server), manifestIds = setOf("atlassian", "retired"))))
            val beforeCommit = file.readText()
            prepared.commit()

            // then
            assertThat(beforeCommit).isEqualTo(retired)
            assertThat(prepared.file).isEqualTo(file)
            assertThat(prepared.entries).isEqualTo(format.entryFingerprints(file.readText(), file).filterKeys { it == "atlassian" })
            assertThat(file)
                .content()
                .contains("atlassian")
                .contains("playwright")
                .doesNotContain("retired")
        }

        @Test
        fun `should refuse to commit an edit whose file changed after it was prepared, leaving the file as it is`() {
            // given
            file.writeText(retired)
            val exporter = McpConfigFileExporter(file, format, ExportService(), projectDir)
            val prepared =
                requireNotNull(exporter.prepare(McpContext(listOf(server), manifestIds = setOf("atlassian"))))
            val changed = """{"mcpServers":{"playwright":{"command":"changed"}}}"""
            file.writeText(changed)

            // when / then
            assertThatThrownBy { prepared.commit() }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining("changed while the engine merged it")
            assertThat(file).hasContent(changed)
        }
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
    fun `should leave the file as the tool wrote it and fail naming it when it changes after the merge was checked, right before it is replaced`() {
        // given
        file.parentFile.mkdirs()
        file.writeText("""{"mcpServers":{}}""")
        // - a tool rewrites the file after the engine checked it once more, while the sink writes the merged content
        val racing = object : ArtifactSink by FileSystemArtifactSink {
            override fun writeConfigFile(
                targetFile: File,
                content: String,
                describedBy: String,
                unchangedFrom: ConfigFileState,
                createdAs: NewConfigFileMode,
            ) {
                targetFile.writeText("""{"mcpServers":{"written-by-a-tool":{}}}""")
                FileSystemArtifactSink.writeConfigFile(targetFile, content, describedBy, unchangedFrom, createdAs)
            }
        }
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(racing), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(file.absolutePath)
            .hasMessageContaining("changed while the engine merged it")
        assertThat(file).hasContent("""{"mcpServers":{"written-by-a-tool":{}}}""")
    }

    @Test
    fun `should refuse a file that is not valid UTF-8, which a rewrite could not give back byte for byte`() {
        // given
        file.parentFile.mkdirs()
        val existing =
            byteArrayOf('{'.code.toByte(), '"'.code.toByte(), 0xC3.toByte(), '"'.code.toByte(), ':'.code.toByte(), '1'.code.toByte(), '}'.code.toByte())
        file.writeBytes(existing)
        val exporter = McpConfigFileExporter(file, JsonMcpConfigFormat.CURSOR, ExportService(), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(file.absolutePath)
            .hasMessageContaining("not valid UTF-8")
        assertThat(file.readBytes()).isEqualTo(existing)
    }

    @Test
    fun `should write through a tool directory and a config file of the home that link outside it, as a dotfile repository does`() {
        // given
        val home = tempDir.resolve("home")
        val dotfiles = tempDir.resolve("dotfiles")
        dotfiles.resolve("codex").mkdirs()
        home.mkdirs()
        Files.createSymbolicLink(home.resolve(".codex").toPath(), dotfiles.resolve("codex").toPath())
        dotfiles.resolve("claude.json").writeText("{\"numStartups\": 1}\n")
        Files.createSymbolicLink(home.resolve(".claude.json").toPath(), dotfiles.resolve("claude.json").toPath())
        val codex =
            McpConfigFileExporter(home.resolve(".codex/config.toml"), CodexTomlMcpConfigFormat, ExportService(), TargetRoot.UserHome(home))
        val claude =
            McpConfigFileExporter(home.resolve(".claude.json"), JsonMcpConfigFormat.CLAUDE_CODE_USER, ExportService(), TargetRoot.UserHome(home))

        // when
        codex.export(McpContext(listOf(server), setOf("atlassian")))
        claude.export(McpContext(listOf(server), setOf("atlassian")))

        // then
        assertThat(dotfiles.resolve("codex/config.toml")).content().contains("[mcp_servers.atlassian]")
        assertThat(dotfiles.resolve("claude.json")).content().startsWith("{\"numStartups\": 1,").contains("\"atlassian\"")
        assertThat(Files.isSymbolicLink(home.resolve(".claude.json").toPath())).isTrue()
    }

    @Test
    fun `should refuse a tool directory of the home that is a link leading nowhere, naming the file`() {
        // given
        val home = tempDir.resolve("home")
        home.mkdirs()
        Files.createSymbolicLink(home.resolve(".codex").toPath(), tempDir.resolve("dotfiles/missing").toPath())
        val codex =
            McpConfigFileExporter(home.resolve(".codex/config.toml"), CodexTomlMcpConfigFormat, ExportService(), TargetRoot.UserHome(home))

        // when / then
        assertThatThrownBy { codex.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(home.resolve(".codex/config.toml").absolutePath)
            .hasMessageContaining("cannot be followed")
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

    /**
     * A linked tool directory that leads nowhere fails as its config file, in a dry run exactly as in a deploy, and nothing is created on the way: neither the file, nor its temporary file, nor the missing target.
     */
    @ParameterizedTest
    @CsvSource(
        // - the link leads to a directory that no longer exists, such as a moved dotfiles folder
        "dangling, false",
        "dangling, true",
        // - the same, written relative to the directory of the link, which is named as the path it stands for
        "relative, false",
        "relative, true",
        // - the link leads to a second link that leads back to it
        "looping, false",
        "looping, true",
    )
    fun `should fail naming the file and where the link leads when the tool directory is a link that cannot be followed`(
        kind: String,
        dryRun: Boolean,
    ) {
        // given
        projectDir.mkdirs()
        val codexDir = projectDir.resolve(".codex")
        val leadsTo = when (kind) {
            "looping" -> projectDir.resolve("loop").also { Files.createSymbolicLink(it.toPath(), codexDir.toPath()) }
            else -> tempDir.resolve("out/missing")
        }
        Files.createSymbolicLink(codexDir.toPath(), if (kind == "relative") Path.of("../out/missing") else leadsTo.toPath())
        val configFile = codexDir.resolve("config.toml")
        val sink = if (dryRun) DryRunArtifactSink else FileSystemArtifactSink
        val exporter = McpConfigFileExporter(configFile, CodexTomlMcpConfigFormat, ExportService(sink), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining("'${configFile.absolutePath}'")
            .hasMessageContaining("'${leadsTo.absolutePath}'")
        assertThat(projectDir.list()).containsExactlyInAnyOrderElementsOf(if (kind == "looping") listOf(".codex", "loop") else listOf(".codex"))
        assertThat(tempDir.resolve("out")).doesNotExist()
        assertThat(logAppender.list).noneMatch { it.formattedMessage.contains("Would write") }
    }

    @ParameterizedTest
    @CsvSource("false", "true")
    fun `should fail naming the file and what lies at the tool directory when it is not a directory, in a dry run as in a deploy`(
        dryRun: Boolean,
    ) {
        // given
        // - a regular file where the tool directory belongs, so the directory of the config file cannot be created
        projectDir.mkdirs()
        val codexFile = projectDir.resolve(".codex")
        codexFile.writeText("not a directory\n")
        val configFile = codexFile.resolve("config.toml")
        val sink = if (dryRun) DryRunArtifactSink else FileSystemArtifactSink
        val exporter = McpConfigFileExporter(configFile, CodexTomlMcpConfigFormat, ExportService(sink), projectDir)

        // when / then
        assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessage(
                "'${configFile.absolutePath}' lies below '${codexFile.toPath().toRealPath()}', which is not a directory, so the engine leaves it untouched. Remove what is at that path, and deploy again.",
            )
        assertThat(codexFile).hasContent("not a directory\n")
        assertThat(logAppender.list).noneMatch { it.formattedMessage.contains("Would write") }
    }

    @Test
    fun `should fail naming the file when the config file cannot be written`() {
        // given
        // - a tool directory the user may read but not write, so no temporary file can be created in it
        val codexDir = projectDir.resolve(".codex")
        codexDir.mkdirs()
        Files.setPosixFilePermissions(codexDir.toPath(), PosixFilePermissions.fromString("r-xr-xr-x"))
        val configFile = codexDir.resolve("config.toml")
        val exporter = McpConfigFileExporter(configFile, CodexTomlMcpConfigFormat, ExportService(), projectDir)

        // when / then
        try {
            // - a user who may write anywhere, such as root, cannot be refused a write
            assumeTrue(!Files.isWritable(codexDir.toPath()))
            assertThatThrownBy { exporter.export(McpContext(listOf(server), setOf("atlassian"))) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining("'${configFile.absolutePath}' cannot be written")
                .hasCauseInstanceOf(IOException::class.java)
            assertThat(codexDir.list()).isEmpty()
        } finally {
            Files.setPosixFilePermissions(codexDir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
        }
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

    /**
     * An entry of an MCP config file of the home that is named after an MCP server manifest is the engine's while the deployment selects servers, whether or not the ledger records it; the user is warned before the engine replaces or removes one the ledger does not record, since it may hold a credential written by hand.
     */
    @Nested
    inner class UnrecordedEntriesOfTheHome {

        private val home get() = tempDir.resolve("home")
        private val ledgerLogger = LoggerFactory.getLogger(McpLedger::class.java) as Logger
        private val ledgerAppender = ListAppender<ILoggingEvent>()

        @BeforeEach
        fun setUp() {
            ledgerAppender.start()
            ledgerLogger.addAppender(ledgerAppender)
        }

        @AfterEach
        fun tearDown() {
            ledgerLogger.detachAppender(ledgerAppender)
            ledgerAppender.stop()
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                // - the file of Claude Code, in a deploy and in a dry run
                ".claude.json              | claude  | false",
                ".claude.json              | claude  | true",
                // - the file of Codex
                ".codex/config.toml        | codex   | false",
                // - the file of Copilot CLI, in a deploy and in a dry run
                ".copilot/mcp-config.json  | copilot | false",
                ".copilot/mcp-config.json  | copilot | true",
            ],
        )
        fun `should warn naming the file and the entry, but not its content, before it replaces an entry of the home the ledger does not record`(
            path: String,
            tool: String,
            dryRun: Boolean,
        ) {
            // given
            // - an entry written by hand under the name of a manifest, holding a credential
            val (format, existing) = handWritten(tool, "atlassian")
            val configFile = home.resolve(path).apply { parentFile.mkdirs() }
            configFile.writeText(existing)
            val exporter = McpConfigFileExporter(configFile, format, exportService(dryRun), TargetRoot.UserHome(home))

            // when
            exporter.export(McpContext(listOf(server), manifestIds = setOf("atlassian")))

            // then
            assertThat(warnings()).singleElement().satisfies({
                assertThat(it)
                    .contains(configFile.absolutePath)
                    .contains("'atlassian'")
                    .contains("replaces it")
                    .doesNotContain(HAND_WRITTEN_TOKEN)
            })
            assertThat(configFile.readText().contains(HAND_WRITTEN_TOKEN)).isEqualTo(dryRun)
        }

        @ParameterizedTest
        @CsvSource("claude, false", "codex, false", "copilot, false", "copilot, true")
        fun `should warn naming the file and the entry, but not its content, before it removes an entry of the home the ledger does not record`(
            tool: String,
            dryRun: Boolean,
        ) {
            // given
            // - the deployment selects another server, so the entry named after a manifest is the engine's to remove
            val (format, existing) = handWritten(tool, "retired")
            val configFile = home.resolve(pathOf(tool)).apply { parentFile.mkdirs() }
            configFile.writeText(existing)
            val exporter = McpConfigFileExporter(configFile, format, exportService(dryRun), TargetRoot.UserHome(home))

            // when
            exporter.export(McpContext(listOf(server), manifestIds = setOf("atlassian", "retired")))

            // then
            assertThat(warnings()).singleElement().satisfies({
                assertThat(it)
                    .contains(configFile.absolutePath)
                    .contains("'retired'")
                    .contains("removes it")
                    .doesNotContain(HAND_WRITTEN_TOKEN)
            })
            assertThat(configFile.readText().contains(HAND_WRITTEN_TOKEN)).isEqualTo(dryRun)
        }

        @Test
        fun `should not warn about an entry of the home the ledger records with what it holds`() {
            // given
            val (format, existing) = handWritten("copilot", "atlassian")
            val configFile = home.resolve(".copilot/mcp-config.json").apply { parentFile.mkdirs() }
            configFile.writeText(existing)
            val recorded = format.entryFingerprints(existing, configFile).asRecorded()
            val exporter = McpConfigFileExporter(configFile, format, ExportService(), TargetRoot.UserHome(home))

            // when
            exporter.export(McpContext(listOf(server), manifestIds = setOf("atlassian"), recorded = recorded))

            // then
            assertThat(warnings()).isEmpty()
            assertThat(configFile).content().doesNotContain(HAND_WRITTEN_TOKEN)
        }

        @Test
        fun `should not warn about an entry of the home that already holds what the engine writes`() {
            // given
            // - a file the engine wrote, whose ledger is gone
            val configFile = home.resolve(".copilot/mcp-config.json")
            McpConfigFileExporter(configFile, JsonMcpConfigFormat.COPILOT_CLI, ExportService(), TargetRoot.UserHome(home)).export(McpContext(listOf(server), setOf("atlassian")))
            val written = configFile.readText()
            ledgerAppender.list.clear()
            val exporter =
                McpConfigFileExporter(configFile, JsonMcpConfigFormat.COPILOT_CLI, ExportService(), TargetRoot.UserHome(home))

            // when
            exporter.export(McpContext(listOf(server), manifestIds = setOf("atlassian")))

            // then
            assertThat(warnings()).isEmpty()
            assertThat(configFile).hasContent(written)
        }

        @Test
        fun `should not warn about an entry of a project the ledger does not record`() {
            // given
            val (format, existing) = handWritten("copilot", "atlassian")
            val configFile = projectDir.resolve(".github/mcp.json").apply { parentFile.mkdirs() }
            configFile.writeText(existing)
            val exporter = McpConfigFileExporter(configFile, format, ExportService(), projectDir)

            // when
            exporter.export(McpContext(listOf(server), manifestIds = setOf("atlassian")))

            // then
            assertThat(warnings()).isEmpty()
            assertThat(configFile).content().doesNotContain(HAND_WRITTEN_TOKEN)
        }

        private fun warnings() = ledgerAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

        private fun exportService(dryRun: Boolean) = if (dryRun) ExportService(DryRunArtifactSink) else ExportService()

        private fun pathOf(tool: String) = when (tool) {
            "claude" -> ".claude.json"
            "codex" -> ".codex/config.toml"
            else -> ".copilot/mcp-config.json"
        }

        // The format of [tool] and a file of it holding the entry [name] written by hand, with a credential.
        private fun handWritten(tool: String, name: String): Pair<McpConfigFormat, String> = when (tool) {
            "claude" -> JsonMcpConfigFormat.CLAUDE_CODE_USER to """{"mcpServers": {"$name": {"type": "http", "url": "https://mine.example.com/mcp", "headers": {"Authorization": "Bearer $HAND_WRITTEN_TOKEN"}}}}"""
            "codex" -> CodexTomlMcpConfigFormat to "[mcp_servers.$name]\ncommand = \"mine\"\nargs = [\"--token=$HAND_WRITTEN_TOKEN\"]\n"
            else -> JsonMcpConfigFormat.COPILOT_CLI to """{"mcpServers": {"$name": {"tools": ["*"], "type": "http", "url": "https://mine.example.com/mcp", "headers": {"Authorization": "Bearer $HAND_WRITTEN_TOKEN"}}}}"""
        }
    }

    companion object {
        private const val HAND_WRITTEN_TOKEN = "ghp-written-by-hand"
    }
}
