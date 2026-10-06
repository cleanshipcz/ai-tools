package cz.cleanship.aitools.engine.tools.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.models.McpToolRestriction
import cz.cleanship.aitools.engine.services.DryRunArtifactSink
import cz.cleanship.aitools.engine.services.ExportService
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

class ClaudeSettingsPermissionsExporterTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var projectDir: File
    private lateinit var settings: File
    private lateinit var exporter: ClaudeSettingsPermissionsExporter

    private val github =
        mapOf("github" to McpToolRestriction(allow = listOf("get_me"), deny = listOf("delete_repository")))

    private val logAppender = ListAppender<ILoggingEvent>()
    private val sinkLogger = LoggerFactory.getLogger(DryRunArtifactSink::class.java) as Logger
    private val warnings = ListAppender<ILoggingEvent>()
    private val ledgerLogger = LoggerFactory.getLogger(McpLedger::class.java) as Logger

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.resolve("project")
        settings = projectDir.resolve(".claude/settings.json")
        exporter = ClaudeSettingsPermissionsExporter(settings, ExportService(), TargetRoot.Project(projectDir))
        logAppender.start()
        sinkLogger.addAppender(logAppender)
        warnings.start()
        ledgerLogger.addAppender(warnings)
    }

    @AfterEach
    fun tearDown() {
        sinkLogger.detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
        ledgerLogger.detachAppender(warnings)
        warnings.stop()
    }

    @Test
    fun `should create the file holding only the denied tools when it does not exist, recording each entry it wrote`() {
        // when
        val entries = deploy(McpPermissionsContext(github))

        // then
        assertThat(Json.parseToJsonElement(settings.readText())).isEqualTo(
            Json.parseToJsonElement("""{"permissions": {"deny": ["mcp__github__delete_repository"]}}"""),
        )
        assertThat(entries.keys).containsExactly("deny:mcp__github__delete_repository")
        assertThat(entries.values).allSatisfy { assertThat(it).matches("sha256:[0-9a-f]{64}") }
    }

    /**
     * An allow entry approves a call without a prompt in every project that has a server of that name, so the engine never writes one: `allow` restricts in Codex, and there is nothing in Claude Code it could restrict.
     */
    @Test
    fun `should never write an allow entry nor touch the allow list, whatever the restriction allows`() {
        // given
        val existing = "{\"permissions\": {\"allow\": [\"mcp__github__*\", \"mcp__github__get_me\"]}}"
        write(existing)

        // when
        val onlyAllowed = exporter.prepare(McpPermissionsContext(mapOf("github" to McpToolRestriction(allow = listOf("get_me", "search")))))
        exporter.export(McpPermissionsContext(github))

        // then
        assertThat(onlyAllowed).isNull()
        assertThat(settings).hasContent("{\"permissions\": {\"allow\": [\"mcp__github__*\", \"mcp__github__get_me\"], \"deny\": [\"mcp__github__delete_repository\"]}}")
    }

    @Test
    fun `should add its entries after the foreign ones and keep every other byte of the file`() {
        // given
        val existing = "{\n  \"model\": \"opus\",\n  \"permissions\": {\n    \"allow\": [\n      \"Bash(git status)\",\n      \"mcp__playwright__browser_click\"\n    ],\n    \"defaultMode\": \"acceptEdits\"\n  },\n  \"env\": {\"A\": \"caf\\u00e9\"}\n}\n"
        write(existing)

        // when
        exporter.export(McpPermissionsContext(github))

        // then
        assertThat(settings).hasContent(
            "{\n  \"model\": \"opus\",\n  \"permissions\": {\n    \"allow\": [\n      \"Bash(git status)\",\n      \"mcp__playwright__browser_click\"\n    ],\n    \"defaultMode\": \"acceptEdits\",\n    \"deny\": [\n      \"mcp__github__delete_repository\"\n    ]\n  },\n  \"env\": {\"A\": \"caf\\u00e9\"}\n}\n",
        )
    }

    @Test
    fun `should replace the entries it recorded in place and remove only them once the restriction goes away`() {
        // given
        // - an entry naming the whole server, one of another server and one of the server added by hand are foreign; only the recorded one is the engine's
        write("{\"permissions\": {\"deny\": [\"mcp__github\", \"mcp__github__old_tool\", \"mcp__githubx__a\", \"Read\"]}}")
        val recorded = deploy(McpPermissionsContext(mapOf("github" to McpToolRestriction(deny = listOf("push")))))
        // - the entry the engine wrote, moved by hand, which keeps it the engine's since its text is unchanged
        write("{\"permissions\": {\"deny\": [\"mcp__github\", \"mcp__github__push\", \"mcp__github__old_tool\", \"mcp__githubx__a\", \"Read\"]}}")

        // when
        val replaced = deploy(McpPermissionsContext(github, recorded.asRecorded()))
        val restricted = settings.readText()
        exporter.export(McpPermissionsContext(emptyMap(), replaced.asRecorded()))

        // then
        assertThat(restricted).isEqualTo(
            "{\"permissions\": {\"deny\": [\"mcp__github\", \"mcp__github__delete_repository\", \"mcp__github__old_tool\", \"mcp__githubx__a\", \"Read\"]}}",
        )
        assertThat(settings).hasContent("{\"permissions\": {\"deny\": [\"mcp__github\", \"mcp__github__old_tool\", \"mcp__githubx__a\", \"Read\"]}}")
    }

    /**
     * A deny rule blocks a call in every scope, so one the user wrote is a security control of theirs: the engine never takes it over, even when it denies the same tool.
     */
    @Test
    fun `should keep the deny rules written by hand for a server it restricts, through the restriction and its removal`() {
        // given
        val existing = "{\"permissions\": {\"deny\": [\"mcp__github__drop_database\", \"mcp__github__delete_repository\"]}}"
        write(existing)

        // when
        val sameTool = exporter.prepare(McpPermissionsContext(github))
        val recorded =
            deploy(McpPermissionsContext(mapOf("github" to McpToolRestriction(deny = listOf("delete_repository", "push")))))
        val restricted = settings.readText()
        exporter.export(McpPermissionsContext(emptyMap(), recorded.asRecorded()))

        // then
        assertThat(sameTool).isNull()
        assertThat(recorded.keys).containsExactly("deny:mcp__github__push")
        assertThat(restricted).isEqualTo("{\"permissions\": {\"deny\": [\"mcp__github__drop_database\", \"mcp__github__delete_repository\", \"mcp__github__push\"]}}")
        assertThat(settings).hasContent(existing)
    }

    @Test
    fun `should neither create nor touch the file when nothing is restricted and it records no entry`() {
        // given
        val other =
            ClaudeSettingsPermissionsExporter(projectDir.resolve("other/settings.json"), ExportService(), TargetRoot.Project(projectDir))
        val existing = "{\"permissions\":{\"deny\":[\"mcp__github__x\"]}}"
        write(existing)

        // when
        exporter.export(McpPermissionsContext(emptyMap()))
        other.export(McpPermissionsContext(emptyMap()))

        // then
        assertThat(settings).hasContent(existing)
        assertThat(other.file).doesNotExist()
    }

    @Test
    fun `should name the file, the servers and every entry it removes by its full text without writing anything in a dry run`() {
        // given
        write("{\"permissions\": {\"deny\": []}}")
        val recorded = deploy(McpPermissionsContext(mapOf("old" to McpToolRestriction(deny = listOf("x", "y")))))
        val deployed = settings.readText()
        logAppender.list.clear()
        val dryRun =
            ClaudeSettingsPermissionsExporter(settings, ExportService(DryRunArtifactSink), TargetRoot.Project(projectDir))

        // when
        dryRun.export(McpPermissionsContext(github, recorded.asRecorded()))

        // then
        assertThat(settings).hasContent(deployed)
        assertThat(infos()).singleElement().satisfies({
            assertThat(it).contains("MCP tool permissions [github]").contains("removing [mcp__old__x, mcp__old__y]").contains(settings.absolutePath)
        })
    }

    @Test
    fun `should delete the file it created once the restriction goes away`() {
        // given
        val recorded = deploy(McpPermissionsContext(github))

        // when
        exporter.export(McpPermissionsContext(emptyMap(), recorded.asRecorded()))

        // then
        assertThat(settings).doesNotExist()
        assertThat(settings.parentFile).exists()
    }

    /**
     * A dotfile setup links `~/.claude/settings.json` into a repository of its own: deleting the emptied file would delete it in that repository and leave the link leading nowhere, so the emptied content is written through the link instead.
     */
    @Test
    fun `should write the emptied content through a linked file rather than delete it at the target of the link`() {
        // given
        val home = tempDir.resolve("home")
        // - the file in the dotfile repository holds nothing yet, so it holds nothing but permissions once the engine restricts a server
        val dotfile = tempDir.resolve("dotfiles/claude/settings.json").also { it.parentFile.mkdirs() }.also { it.writeText("{}\n") }
        val linked = home.resolve(".claude/settings.json").also { it.parentFile.mkdirs() }
        Files.createSymbolicLink(linked.toPath(), dotfile.toPath())
        val inHome = ClaudeSettingsPermissionsExporter(linked, ExportService(), TargetRoot.UserHome(home))
        val recorded = requireNotNull(inHome.prepare(McpPermissionsContext(github))).also { it.commit() }.entries

        // when
        val prepared = requireNotNull(inHome.prepare(McpPermissionsContext(emptyMap(), recorded.asRecorded())))
        prepared.commit()

        // then
        assertThat(prepared.entries).isEmpty()
        assertThat(Files.isSymbolicLink(linked.toPath())).isTrue()
        assertThat(Json.parseToJsonElement(dotfile.readText())).isEqualTo(Json.parseToJsonElement("""{"permissions": {"deny": []}}"""))
        assertThat(linked).hasSameTextualContentAs(dotfile)
    }

    @Test
    fun `should name the file and the entries it would delete in a dry run, and delete nothing`() {
        // given
        val recorded = deploy(McpPermissionsContext(github))
        val created = settings.readText()
        val dryRun =
            ClaudeSettingsPermissionsExporter(settings, ExportService(DryRunArtifactSink), TargetRoot.Project(projectDir))

        // when
        dryRun.export(McpPermissionsContext(emptyMap(), recorded.asRecorded()))

        // then
        assertThat(settings).hasContent(created)
        assertThat(infos()).singleElement().satisfies({
            assertThat(it).startsWith("Would delete").contains("removing [mcp__github__delete_repository]").contains(settings.absolutePath)
        })
    }

    @Test
    fun `should keep a file holding content of its own once the restriction goes away, its emptied list written as empty brackets`() {
        // given
        val existing = "{\n  \"model\": \"opus\"\n}\n"
        write(existing)

        // when
        val recorded = deploy(McpPermissionsContext(github))
        exporter.export(McpPermissionsContext(emptyMap(), recorded.asRecorded()))

        // then
        assertThat(settings).hasContent("{\n  \"model\": \"opus\",\n  \"permissions\": {\n    \"deny\": []\n  }\n}\n")
    }

    @Test
    fun `should never take the entries of another server whose id starts with an owned id followed by two underscores`() {
        // given
        // - mcp__a__b__x is the tool x of a server a__b added by hand, not a tool of the restricted server a
        write("{\"permissions\": {\"deny\": [\"mcp__a__b__x\"]}}")
        val restricted = mapOf("a" to McpToolRestriction(deny = listOf("get")))

        // when
        val recorded = deploy(McpPermissionsContext(restricted))
        val written = settings.readText()
        exporter.export(McpPermissionsContext(emptyMap(), recorded.asRecorded()))

        // then
        assertThat(written).isEqualTo("{\"permissions\": {\"deny\": [\"mcp__a__b__x\", \"mcp__a__get\"]}}")
        assertThat(settings).hasContent("{\"permissions\": {\"deny\": [\"mcp__a__b__x\"]}}")
    }

    @Test
    fun `should remove nothing on the word of a ledger whose fingerprints the entries do not hold, warning with the file and the entry`() {
        // given
        val existing = "{\"permissions\": {\"deny\": [\"mcp__mine__delete_everything\"]}}"
        write(existing)
        val planted = mapOf("deny:mcp__mine__delete_everything" to setOf("sha256:" + "0".repeat(64)))

        // when
        val prepared = exporter.prepare(McpPermissionsContext(emptyMap(), planted))

        // then
        assertThat(prepared).isNull()
        assertThat(settings).hasContent(existing)
        assertThat(warnings.list.filter { it.level == Level.WARN }.map { it.formattedMessage }).singleElement().satisfies({
            assertThat(it).contains(settings.absolutePath).contains("'deny:mcp__mine__delete_everything'")
        })
    }

    @Test
    fun `should read nothing and follow no link at a file the deploy deletes before writing it`() {
        // given
        // - a link that leads nowhere, which the deploy removes with the directory holding it before it writes the file
        settings.parentFile.mkdirs()
        Files.createSymbolicLink(settings.toPath(), tempDir.resolve("missing/settings.json").toPath())
        val dryRun =
            ClaudeSettingsPermissionsExporter(settings, ExportService(DryRunArtifactSink), TargetRoot.Project(projectDir))

        // when
        dryRun.export(McpPermissionsContext(github, mapOf("deny:mcp__old__x" to setOf("sha256:" + "0".repeat(64))), presumedAbsent = true))

        // then
        assertThat(infos()).containsExactly("Would write MCP tool permissions [github] to ${settings.absolutePath}")
    }

    @ParameterizedTest
    @CsvSource(
        // - permissions that are not an object
        "'{\"permissions\": [\"mcp__github__x\"]}', 'permissions'",
        // - a deny list that is not an array
        "'{\"permissions\": {\"deny\": \"mcp__github__x\"}}', 'deny'",
    )
    fun `should refuse a file whose permissions have another shape, naming the file and the key`(
        existing: String,
        key: String,
    ) {
        // given
        write(existing)

        // when / then
        assertThatThrownBy { exporter.export(McpPermissionsContext(github)) }
            .isInstanceOf(McpConfigFileException::class.java)
            .hasMessageContaining(settings.absolutePath)
            .hasMessageContaining(key)
        assertThat(settings).hasContent(existing)
    }

    private fun infos() = logAppender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }

    /**
     * Prepares and commits the edit of [context], and returns the entries the file then holds of the engine, as the ledger records them.
     */
    private fun deploy(context: McpPermissionsContext): Map<String, String> =
        requireNotNull(exporter.prepare(context)).also { it.commit() }.entries

    private fun write(content: String) {
        settings.parentFile.mkdirs()
        settings.writeText(content)
    }
}
