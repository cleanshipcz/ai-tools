package cz.cleanship.aitools.engine.tools.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.services.DryRunArtifactSink
import cz.cleanship.aitools.engine.services.ExportService
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class McpLedgerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var projectDir: File
    private lateinit var ledgerFile: File

    private val github = fingerprint('a')
    private val atlassian = fingerprint('b')

    private val logAppender = ListAppender<ILoggingEvent>()
    private val sinkLogger = LoggerFactory.getLogger(DryRunArtifactSink::class.java) as Logger

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.resolve("project")
        projectDir.mkdirs()
        ledgerFile = projectDir.resolve(".ai-tools/mcp-ledger.json")
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
    fun `should record nothing and create no file when the target has no ledger`() {
        // when
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService())

        // then
        assertThat(ledger.recorded(".mcp.json")).isEmpty()
        assertThat(projectDir.resolve(".ai-tools")).doesNotExist()
    }

    @Test
    fun `should record the fingerprint of every entry written into each file by its path relative to the target, and nothing else`() {
        // given
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService())

        // when
        ledger.record(".mcp.json", mapOf("github" to setOf(github), "atlassian" to setOf(atlassian)))
        ledger.record(".codex/config.toml", mapOf("atlassian" to setOf(atlassian)))

        // then
        assertThat(Json.parseToJsonElement(ledgerFile.readText())).isEqualTo(
            Json.parseToJsonElement("""{"version": 2, "files": {".codex/config.toml": {"atlassian": "$atlassian"}, ".mcp.json": {"atlassian": "$atlassian", "github": "$github"}}}"""),
        )
        val reloaded = McpLedger.load(TargetRoot.Project(projectDir), ExportService())
        assertThat(reloaded.recorded(".mcp.json")).isEqualTo(mapOf("github" to setOf(github), "atlassian" to setOf(atlassian)))
        assertThat(reloaded.recorded(".codex/config.toml")).isEqualTo(mapOf("atlassian" to setOf(atlassian)))
    }

    /**
     * While an edit is pending, an entry is recorded with what its file holds and with what the edit writes, so it stays the engine's whether the commit runs or not.
     */
    @Test
    fun `should write an entry recorded with several fingerprints as their sorted list and one recorded with one as a text, and read both back`() {
        // given
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService())

        // when
        ledger.record(".mcp.json", mapOf("atlassian" to setOf(github, atlassian), "github" to setOf(github)))

        // then
        assertThat(Json.parseToJsonElement(ledgerFile.readText())).isEqualTo(
            Json.parseToJsonElement("""{"version": 2, "files": {".mcp.json": {"atlassian": ["$github", "$atlassian"], "github": "$github"}}}"""),
        )
        val reloaded = McpLedger.load(TargetRoot.Project(projectDir), ExportService())
        assertThat(reloaded.recorded(".mcp.json")).isEqualTo(mapOf("atlassian" to setOf(github, atlassian), "github" to setOf(github)))
    }

    @Test
    fun `should drop a file recorded with no id and delete the ledger and its directory once no file is left`() {
        // given
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService())
        ledger.record(".mcp.json", mapOf("github" to setOf(github)))
        ledger.record(".codex/config.toml", mapOf("github" to setOf(github)))

        // when
        ledger.record(".mcp.json", emptyMap())
        val afterFirst = Json.parseToJsonElement(ledgerFile.readText())
        ledger.record(".codex/config.toml", emptyMap())

        // then
        assertThat(afterFirst).isEqualTo(Json.parseToJsonElement("""{"version": 2, "files": {".codex/config.toml": {"github": "$github"}}}"""))
        assertThat(ledgerFile).doesNotExist()
        assertThat(projectDir.resolve(".ai-tools")).doesNotExist()
    }

    @Test
    fun `should keep the directory of the ledger when it holds anything else`() {
        // given
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService())
        ledger.record(".mcp.json", mapOf("github" to setOf(github)))
        val other = projectDir.resolve(".ai-tools/notes.txt").also { it.writeText("mine\n") }

        // when
        ledger.record(".mcp.json", emptyMap())

        // then
        assertThat(ledgerFile).doesNotExist()
        assertThat(other).hasContent("mine\n")
    }

    @Test
    fun `should delete the ledger and warn, without failing, when its directory cannot be listed once it is left empty`() {
        // given
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService())
        ledger.record(".mcp.json", mapOf("github" to setOf(github)))
        val directory = ledgerFile.parentFile.toPath()
        // - write and search only: the ledger can be deleted, the directory cannot be listed
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("-wx------"))
        assumeFalse(Files.isReadable(directory), "the user running the tests reads every directory")
        val warnings = ListAppender<ILoggingEvent>().also { it.start() }
        val ledgerLogger = LoggerFactory.getLogger(McpLedger::class.java) as Logger
        ledgerLogger.addAppender(warnings)

        // when
        try {
            ledger.record(".mcp.json", emptyMap())
        } finally {
            ledgerLogger.detachAppender(warnings)
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        }

        // then
        assertThat(ledgerFile).doesNotExist()
        assertThat(warnings.list.filter { it.level == Level.WARN }.map { it.formattedMessage }).singleElement().satisfies({
            assertThat(it).contains(directory.toString()).contains("AccessDeniedException")
        })
    }

    @Test
    fun `should name the ledger in a dry run, write nothing, and keep what it would record for the files after it`() {
        // given
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService(DryRunArtifactSink))

        // when
        ledger.record(".mcp.json", mapOf("github" to setOf(github)))

        // then
        assertThat(projectDir.resolve(".ai-tools")).doesNotExist()
        assertThat(ledger.recorded(".mcp.json")).isEqualTo(mapOf("github" to setOf(github)))
        assertThat(infos()).anyMatch { it.contains("Would write") && it.contains(ledgerFile.absolutePath) }
    }

    @Test
    fun `should write nothing when a record does not change`() {
        // given
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService(DryRunArtifactSink))
        ledger.record(".mcp.json", mapOf("github" to setOf(github)))
        logAppender.list.clear()

        // when
        ledger.record(".mcp.json", mapOf("github" to setOf(github)))
        ledger.record(".vscode/mcp.json", emptyMap())

        // then
        assertThat(infos()).isEmpty()
    }

    /**
     * A ledger decides what is removed from a file, so one the engine cannot read in full stops every MCP file of its target rather than being ignored; the message never quotes what the file holds.
     */
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            // - not JSON at all, cut off in the middle
            "{\"version\": 2, \"files\": {\"MARK-9f3\" | not valid JSON",
            // - another version
            "{\"version\": 3, \"files\": {\".mcp.json\": {\"MARK-9f3\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | version",
            // - the version as text rather than a number
            "{\"version\": \"2\", \"files\": {\".mcp.json\": {\"MARK-9f3\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | version",
            // - a list where the files belong
            "{\"version\": 2, \"files\": [\"MARK-9f3\"]} | files",
            // - the entries of a file as a list of ids, which records no fingerprint
            "{\"version\": 2, \"files\": {\".mcp.json\": [\"MARK-9f3\"]}} | fingerprint",
            // - an entry without a fingerprint
            "{\"version\": 2, \"files\": {\".mcp.json\": {\"MARK-9f3\": null}}} | fingerprint",
            // - a fingerprint of another form
            "{\"version\": 2, \"files\": {\".mcp.json\": {\"a\": \"md5:MARK-9f3\"}}} | fingerprint",
            // - an entry that is not a manifest id
            "{\"version\": 2, \"files\": {\".mcp.json\": {\"MARK-9f3/..\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | entries",
            // - an entry holding a control character
            "{\"version\": 2, \"files\": {\".mcp.json\": {\"MARK\\n9f3\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | entries",
            // - an absolute path
            "{\"version\": 2, \"files\": {\"/etc/MARK-9f3\": {\"a\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | path",
            // - a path climbing out of the target
            "{\"version\": 2, \"files\": {\"../MARK-9f3\": {\"a\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | path",
            // - a path holding a control character
            "{\"version\": 2, \"files\": {\"MARK\\u001b9f3\": {\"a\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | path",
            // - an entry holding a format character, which a terminal would render
            "{\"version\": 2, \"files\": {\".mcp.json\": {\"MARK\\u202e9f3\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | entries",
            // - a path holding a format character
            "{\"version\": 2, \"files\": {\"MARK\\u200b9f3\": {\"a\": \"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}} | path",
            // - an entry with an empty list of fingerprints
            "{\"version\": 2, \"files\": {\".mcp.json\": {\"MARK-9f3\": []}}} | fingerprint",
            // - an entry with a list holding a fingerprint of another form
            "{\"version\": 2, \"files\": {\".mcp.json\": {\"a\": [\"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\", \"md5:MARK-9f3\"]}}} | fingerprint",
            // - a key the ledger does not have
            "{\"version\": 2, \"files\": {}, \"MARK-9f3\": 1} | MARK-9f3",
        ],
    )
    fun `should refuse a ledger it cannot read in full, naming the file and never quoting it`(
        content: String,
        expected: String,
    ) {
        // given
        ledgerFile.parentFile.mkdirs()
        ledgerFile.writeText(content)

        // when / then
        val thrown = assertThatThrownBy { McpLedger.load(TargetRoot.Project(projectDir), ExportService()) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(ledgerFile.absolutePath)
            .hasMessageContaining(expected)
        if (expected != "MARK-9f3") thrown.hasMessageNotContaining("MARK-9f3")
    }

    @Test
    fun `should refuse a ledger of version 1, naming the file and the version, since it records no fingerprint of what the engine wrote`() {
        // given
        ledgerFile.parentFile.mkdirs()
        ledgerFile.writeText("{\"version\": 1, \"files\": {\".mcp.json\": [\"MARK-9f3\"]}}")

        // when / then
        assertThatThrownBy { McpLedger.load(TargetRoot.Project(projectDir), ExportService()) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(ledgerFile.absolutePath)
            .hasMessageContaining("version 1")
            .hasMessageNotContaining("MARK-9f3")
    }

    @Test
    fun `should name an unknown key of a ledger with its line breaks escaped, so it can never forge a line of its own`() {
        // given
        ledgerFile.parentFile.mkdirs()
        ledgerFile.writeText("{\"version\": 2, \"files\": {}, \"x\\nERROR forged\": 1}")

        // when / then
        assertThatThrownBy { McpLedger.load(TargetRoot.Project(projectDir), ExportService()) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining("'x\\u000AERROR forged'")
            .hasMessageNotContaining("\n")
    }

    @Test
    fun `should refuse a ledger nested deeper than the limit, naming the file and the limit, instead of overflowing the stack`() {
        // given
        ledgerFile.parentFile.mkdirs()
        ledgerFile.writeText("{\"version\": 2, \"files\": {}, \"deep\": " + "[".repeat(10_000) + "]".repeat(10_000) + "}")

        // when / then
        assertThatThrownBy { McpLedger.load(TargetRoot.Project(projectDir), ExportService()) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(ledgerFile.absolutePath)
            .hasMessageContaining("512")
    }

    @Test
    fun `should name the recorded files and entries in its log line, never their fingerprints`() {
        // given
        val ledger = McpLedger.load(TargetRoot.Project(projectDir), ExportService(DryRunArtifactSink))

        // when
        ledger.record(".mcp.json", mapOf("github" to setOf(github)))

        // then
        assertThat(infos()).singleElement().satisfies({
            assertThat(it).contains("the MCP ledger {.mcp.json=[github]}").doesNotContain(github)
        })
    }

    @Test
    fun `should refuse a ledger directory that links outside the project`() {
        // given
        val outside = tempDir.resolve("outside").also { it.mkdirs() }
        Files.createSymbolicLink(projectDir.resolve(".ai-tools").toPath(), outside.toPath())

        // when / then
        assertThatThrownBy { McpLedger.load(TargetRoot.Project(projectDir), ExportService()) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(ledgerFile.absolutePath)
            .hasMessageContaining(outside.canonicalPath)
    }

    @Test
    fun `should follow a ledger directory of the home that links anywhere`() {
        // given
        val home = tempDir.resolve("home").also { it.mkdirs() }
        val dotfiles = tempDir.resolve("dotfiles/ai-tools").also { it.mkdirs() }
        Files.createSymbolicLink(home.resolve(".ai-tools").toPath(), dotfiles.toPath())
        val ledger = McpLedger.load(TargetRoot.UserHome(home), ExportService())

        // when
        ledger.record(".claude.json", mapOf("github" to setOf(github)))

        // then
        assertThat(Json.parseToJsonElement(dotfiles.resolve("mcp-ledger.json").readText()))
            .isEqualTo(Json.parseToJsonElement("""{"version": 2, "files": {".claude.json": {"github": "$github"}}}"""))
    }

    private fun infos() = logAppender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }

    private fun fingerprint(digit: Char) = "sha256:" + digit.toString().repeat(64)
}
