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
import java.nio.file.Files
import java.nio.file.NoSuchFileException
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
}
