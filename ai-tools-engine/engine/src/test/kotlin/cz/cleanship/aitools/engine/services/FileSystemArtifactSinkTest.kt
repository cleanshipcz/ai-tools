package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

class FileSystemArtifactSinkTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var toolDir: File

    private val entity =
        RulesetManifest(id = "base", description = "A ruleset", rules = listOf("A rule."), metadata = ManifestMetadata(version = Version("1.0.0")))

    @BeforeEach
    fun setUp() {
        toolDir = tempDir.resolve("project/.codex")
        toolDir.parentFile.mkdirs()
    }

    /**
     * A config file another program rewrites while the engine merges it, such as `~/.claude.json` under a running Claude Code, is checked once more right before it is replaced, and left as the other program wrote it.
     */
    @ParameterizedTest
    @CsvSource(
        // - the file changed after it was read
        "exactly, changed",
        // - the file appeared after the engine found none
        "absent, appeared",
        // - the file was removed after it was read
        "exactly, removed",
    )
    fun `should refuse to replace a config file that no longer holds what the engine read, naming it and leaving it as it is`(
        expected: String,
        change: String,
    ) {
        // given
        val target = toolDir.resolve("config.toml")
        toolDir.mkdirs()
        if (expected == "exactly") target.writeText("read\n")
        when (change) {
            "changed" -> target.writeText("written by the tool\n")
            "appeared" -> target.writeText("written by the tool\n")
            else -> target.delete()
        }
        val state = if (expected == "exactly") ConfigFileState.Exactly("read\n") else ConfigFileState.Absent

        // when / then
        assertThatThrownBy { FileSystemArtifactSink.writeConfigFile(target, "merged\n", describedBy = "MCP servers [atlassian]", unchangedFrom = state) }
            .isInstanceOf(ConfigFileChangedException::class.java)
            .hasMessageContaining(target.absolutePath)
        if (change == "removed") assertThat(target).doesNotExist() else assertThat(target).hasContent("written by the tool\n")
        assertThat(toolDir.list()).allMatch { it == "config.toml" }
    }

    @Test
    fun `should replace a config file that still holds what the engine read`() {
        // given
        val target = toolDir.resolve("config.toml")
        toolDir.mkdirs()
        target.writeText("read\n")

        // when
        FileSystemArtifactSink.writeConfigFile(target, "merged\n", describedBy = "MCP servers [atlassian]", unchangedFrom = ConfigFileState.Exactly("read\n"))

        // then
        assertThat(target).hasContent("merged\n")
    }

    @Test
    fun `should delete a config file that still holds what the engine read, and refuse one that changed`() {
        // given
        val unchanged = toolDir.resolve("unchanged.json")
        val changed = toolDir.resolve("changed.json")
        toolDir.mkdirs()
        unchanged.writeText("{}\n")
        changed.writeText("{\"new\": 1}\n")

        // when
        FileSystemArtifactSink.deleteConfigFile(unchanged, describedBy = "the MCP ledger", unchangedFrom = ConfigFileState.Exactly("{}\n"))

        // then
        assertThat(unchanged).doesNotExist()
        assertThatThrownBy { FileSystemArtifactSink.deleteConfigFile(changed, describedBy = "the MCP ledger", unchangedFrom = ConfigFileState.Exactly("{}\n")) }
            .isInstanceOf(ConfigFileChangedException::class.java)
            .hasMessageContaining(changed.absolutePath)
        assertThat(changed).hasContent("{\"new\": 1}\n")
    }

    @Test
    fun `should write the rendered artifact to the target file`() {
        // given
        val target = toolDir.resolve("skills/agent-basic/SKILL.md")

        // when
        FileSystemArtifactSink.export(entity, target) { it.appendText("rendered") }

        // then
        assertThat(target).content().isEqualTo("rendered\n")
    }

    /**
     * A target of any name length is written, down to one character, which is shorter than the prefix a temporary file needs.
     */
    @ParameterizedTest
    @CsvSource("export", "copy", "config")
    fun `should write a target whose name is a single character`(write: String) {
        // given
        val target = toolDir.resolve("skills/jira-ticket/a")
        val source = tempDir.resolve("source/a")
        source.parentFile.mkdirs()
        source.writeText("Copied.\n")

        // when
        when (write) {
            "export" -> FileSystemArtifactSink.export(entity, target) { it.appendText("Copied.") }
            "copy" -> FileSystemArtifactSink.copySkillFile(source, target)
            else -> FileSystemArtifactSink.writeConfigFile(target, "Copied.\n", describedBy = "MCP servers [atlassian]")
        }

        // then
        assertThat(target).content().isEqualTo("Copied.\n")
        assertThat(target.parentFile.list()).containsExactly("a")
    }

    /**
     * A tool directory the deploy cannot write through fails the artifact with an [ArtifactWriteException] naming the target, whatever is at the tool directory.
     */
    @ParameterizedTest
    @CsvSource(
        // - a link to a directory that no longer exists
        "dangling",
        // - a regular file where the directory belongs
        "file",
    )
    fun `should fail naming the target file when the directory of an exported artifact cannot be written`(
        kind: String,
    ) {
        // given
        when (kind) {
            "dangling" -> Files.createSymbolicLink(toolDir.toPath(), tempDir.resolve("out/missing").toPath())
            else -> toolDir.writeText("not a directory\n")
        }
        val target = toolDir.resolve("skills/agent-basic/SKILL.md")

        // when / then
        assertThatThrownBy { FileSystemArtifactSink.export(entity, target) { it.appendText("rendered") } }
            .isInstanceOf(ArtifactWriteException::class.java)
            // - the reason of the operating system, such as "Not a directory", follows the class, and no other path does
            .hasMessageMatching("'${Regex.escape(target.absolutePath)}' cannot be written \\(IOException: [^/'()]+\\)")
            .hasCauseInstanceOf(IOException::class.java)
            .satisfies({ assertThat((it as ArtifactWriteException).targetFile).isEqualTo(target) })
        assertThat(tempDir.resolve("out")).doesNotExist()
    }

    @Test
    fun `should fail naming the source and keep the target when a companion file cannot be read`() {
        // given
        // - a source that became unreadable after the export service checked it
        val source = tempDir.resolve("source/locked.md")
        source.parentFile.mkdirs()
        source.writeText("Locked.\n")
        val target = toolDir.resolve("skills/jira-ticket/locked.md")
        target.parentFile.mkdirs()
        target.writeText("Deployed before.\n")
        Files.setPosixFilePermissions(source.toPath(), PosixFilePermissions.fromString("---------"))

        // when
        val error = try {
            // - a user who may read anything, such as root, cannot be refused a read
            assumeTrue(!Files.isReadable(source.toPath()))
            runCatching { FileSystemArtifactSink.copySkillFile(source, target) }.exceptionOrNull()
        } finally {
            Files.setPosixFilePermissions(source.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        }

        // then
        assertThat(error)
            .isInstanceOf(SkillFileResolvingException::class.java)
            .hasMessageStartingWith("Skill file '${source.absolutePath}' cannot be read (")
            .hasMessageNotContaining(target.absolutePath)
        assertThat(target).content().isEqualTo("Deployed before.\n")
    }

    @Test
    fun `should fail naming the source and keep the target when a companion file fails in the middle of the copy`() {
        // given
        // - a directory as the source, which opens on Linux and fails at the first read, after the copy has begun
        val source = tempDir.resolve("source/templates")
        source.mkdirs()
        val target = toolDir.resolve("skills/jira-ticket/templates")
        target.parentFile.mkdirs()
        target.writeText("Deployed before.\n")

        // when
        val error = runCatching { FileSystemArtifactSink.copySkillFile(source, target) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(SkillFileResolvingException::class.java)
            .hasMessageStartingWith("Skill file '${source.absolutePath}' cannot be read (")
            .hasMessageNotContaining(target.absolutePath)
        assertThat(target).content().isEqualTo("Deployed before.\n")
        // - neither the copy nor its temporary file is left beside the target
        assertThat(target.parentFile.list()).containsExactly("templates")
    }

    /**
     * The description of a failure names its class and the reason of the operating system, and never a path or a value its message may carry.
     */
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            // - a plain IOException whose message is a path, or a value
            "plain | /etc/passwd | IOException",
            "plain | token=abc | IOException",
            // - a plain IOException whose message is the reason alone, as the JDK file code sets it
            "plain | Not a directory | IOException: Not a directory",
            // - a subclass whose message may be anything, so none of it is kept
            "subclass | Not a directory | CustomIOException",
            // - the reason a FileNotFoundException gives after its path, and none when it gives only the path
            "not-found | /a/b (Permission denied) | FileNotFoundException: Permission denied",
            "not-found | /a/b | FileNotFoundException",
            // - the reason a FileSystemException carries apart from its paths, and none when that reason is a path
            "no-such-file | No such file or directory | NoSuchFileException: No such file or directory",
            "no-such-file | /etc/shadow | NoSuchFileException",
            "access-denied | | AccessDeniedException",
        ],
    )
    fun `should describe a failure by its class and the reason of the operating system, never a path or a value`(
        kind: String,
        text: String?,
        expected: String,
    ) {
        // given
        val failure = when (kind) {
            "plain" -> IOException(text)
            "subclass" -> CustomIOException(text.orEmpty())
            "not-found" -> FileNotFoundException(text)
            "no-such-file" -> NoSuchFileException("/a", null, text)
            else -> AccessDeniedException("/a")
        }

        // when
        val description = failure.failureDescription()

        // then
        assertThat(description).isEqualTo(expected)
    }

    private class CustomIOException(message: String) : IOException(message)

    @Test
    fun `should fail naming the target file when a companion file cannot be copied`() {
        // given
        val source = tempDir.resolve("source/template.txt")
        source.parentFile.mkdirs()
        source.writeText("Template.\n")
        toolDir.writeText("not a directory\n")
        val target = toolDir.resolve("skills/jira-ticket/template.txt")

        // when / then
        assertThatThrownBy { FileSystemArtifactSink.copySkillFile(source, target) }
            .isInstanceOf(ArtifactWriteException::class.java)
            .hasMessageStartingWith("'${target.absolutePath}' cannot be written (")
            .hasCauseInstanceOf(IOException::class.java)
        assertThat(toolDir).hasContent("not a directory\n")
    }

    @Test
    fun `should move a file into place atomically where the file system can`() {
        // given
        val calls = mutableListOf<CopyOption>()

        // when
        moveIntoPlace(tempDir.resolve("a.tmp").toPath(), tempDir.resolve("a").toPath()) { _, _, option -> calls += option }

        // then
        assertThat(calls).containsExactly(StandardCopyOption.ATOMIC_MOVE)
    }

    @Test
    fun `should replace the file in place when the file system cannot move it atomically`() {
        // given
        val source = tempDir.resolve("a.tmp").toPath()
        val target = tempDir.resolve("a").toPath()
        val calls = mutableListOf<CopyOption>()

        // when
        moveIntoPlace(source, target) { from, to, option ->
            calls += option
            if (option == StandardCopyOption.ATOMIC_MOVE) throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "not supported")
        }

        // then
        assertThat(calls).containsExactly(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    @Test
    fun `should replace an existing config file through the move`() {
        // given
        val target = toolDir.resolve("config.toml")
        toolDir.mkdirs()
        target.writeText("old\n")

        // when
        FileSystemArtifactSink.writeConfigFile(target, "new\n", "config", ConfigFileState.Exactly("old\n"))

        // then
        assertThat(target).hasContent("new\n")
        assertThat(toolDir.listFiles()!!.map { it.name }).containsExactly("config.toml")
    }

    /**
     * A config file of the home, such as `~/.claude.json`, later receives session state and possibly a key from the tool that reads it, so one the engine creates there is readable by its owner only.
     */
    @Test
    fun `should create a missing config file readable and writable by its owner only when asked to`() {
        // given
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        val target = tempDir.resolve("home/.claude.json")

        // when
        FileSystemArtifactSink.writeConfigFile(target, "{}\n", "MCP servers [atlassian]", ConfigFileState.Absent, NewConfigFileMode.OWNER_ONLY)

        // then
        assertThat(target).hasContent("{}\n")
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(target.toPath()))).isEqualTo("rw-------")
    }

    @Test
    fun `should create a missing config file with the mode every artifact gets when not asked otherwise`() {
        // given
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        val target = toolDir.resolve("config.toml")
        val artifact = toolDir.resolve("artifact.md")
        FileSystemArtifactSink.export(entity, artifact) { it.appendText("A rule.") }

        // when
        FileSystemArtifactSink.writeConfigFile(target, "x = 1\n", "MCP servers [atlassian]", ConfigFileState.Absent, NewConfigFileMode.DEFAULT)

        // then
        assertThat(Files.getPosixFilePermissions(target.toPath())).isEqualTo(Files.getPosixFilePermissions(artifact.toPath()))
    }

    @ParameterizedTest
    @CsvSource("rw-r--r--, OWNER_ONLY", "rw-------, DEFAULT", "rw-rw-r--, OWNER_ONLY")
    fun `should keep the mode of an existing config file however a new one would be created`(
        mode: String,
        createdAs: NewConfigFileMode,
    ) {
        // given
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        val target = toolDir.resolve("config.toml")
        toolDir.mkdirs()
        target.writeText("old\n")
        Files.setPosixFilePermissions(target.toPath(), PosixFilePermissions.fromString(mode))

        // when
        FileSystemArtifactSink.writeConfigFile(target, "new\n", "config", ConfigFileState.Exactly("old\n"), createdAs)

        // then
        assertThat(target).hasContent("new\n")
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(target.toPath()))).isEqualTo(mode)
    }
}
