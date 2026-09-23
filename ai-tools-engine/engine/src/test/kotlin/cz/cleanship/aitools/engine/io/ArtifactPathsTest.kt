package cz.cleanship.aitools.engine.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path

class ArtifactPathsTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var owned: File
    private lateinit var outside: File

    @BeforeEach
    fun setUp() {
        owned = tempDir.resolve("home/.claude/skills").toFile()
        owned.mkdirs()
        outside = tempDir.resolve("outside").toFile()
        outside.mkdirs()
        outside.resolve("notes.md").writeText("Kept outside.\n")
    }

    @Test
    fun `should delete the artifact directory and everything it holds`() {
        // given
        val artifact = owned.resolve("a-skill")
        artifact.mkdirs()
        artifact.resolve("SKILL.md").writeText("content")

        // when
        artifact.deleteArtifactDirectoryWithin(owned, describedBy = "skill 'a-skill'")

        // then
        assertThat(artifact).doesNotExist()
        // - the directory holding it is shared with everything the user installed by hand
        assertThat(owned).exists()
    }

    @Test
    fun `should unlink a symbolic link inside the artifact directory rather than follow it`() {
        // given
        // - linking a corpus into a skill bundle is an ordinary thing to do, and the link target is not ours to delete
        val artifact = owned.resolve("a-skill")
        artifact.mkdirs()
        Files.createSymbolicLink(artifact.resolve("corpus").toPath(), outside.toPath())

        // when
        artifact.deleteArtifactDirectoryWithin(owned, describedBy = "skill 'a-skill'")

        // then
        assertThat(artifact).doesNotExist()
        assertThat(outside).exists()
        assertThat(outside.resolve("notes.md")).hasContent("Kept outside.\n")
    }

    @Test
    fun `should unlink a symbolic link to a single file inside the artifact directory`() {
        // given
        val artifact = owned.resolve("a-skill")
        artifact.mkdirs()
        Files.createSymbolicLink(artifact.resolve("notes.md").toPath(), outside.resolve("notes.md").toPath())

        // when
        artifact.deleteArtifactDirectoryWithin(owned, describedBy = "skill 'a-skill'")

        // then
        assertThat(outside.resolve("notes.md")).hasContent("Kept outside.\n")
    }

    @Test
    fun `should name the entry it could not delete when an entry of the artifact directory cannot be deleted`() {
        // given
        // - the folder can be read, but the file in it cannot be removed from it
        val readOnly = owned.resolve("a-skill/read-only")
        readOnly.mkdirs()
        val kept = readOnly.resolve("kept.md")
        kept.writeText("Cannot be deleted.\n")
        readOnly.setWritable(false)
        try {
            // - a superuser deletes regardless of permissions, so the failure cannot be provoked there
            assumeFalse(readOnly.canWrite())

            // when
            val error = runCatching {
                owned.resolve("a-skill").deleteArtifactDirectoryWithin(owned, describedBy = "skill 'a-skill'")
            }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(ArtifactDeleteException::class.java)
            assertThat((error as ArtifactDeleteException).entry).isEqualTo(kept.absolutePath)
            assertThat(error.cause).isInstanceOf(AccessDeniedException::class.java)
            assertThat(kept).hasContent("Cannot be deleted.\n")
        } finally {
            readOnly.setWritable(true)
        }
    }

    @ParameterizedTest
    @CsvSource("../../outside", "..", "../evil")
    fun `should refuse to delete a path that is not inside the owned directory`(relativePath: String) {
        // given
        val artifact = owned.resolve(relativePath)

        // when
        val error = runCatching {
            artifact.deleteArtifactDirectoryWithin(owned, describedBy = "skill 'escaping'")
        }.exceptionOrNull()

        // then
        assertThat(error).isInstanceOf(ArtifactPathException::class.java)
        assertThat(outside.resolve("notes.md")).exists()
    }

    @Test
    fun `should refuse to delete the owned directory itself`() {
        // given
        // - an empty id, or one that is a single dot, resolves straight back to the directory holding every artifact
        val artifact = owned.resolve(".")

        // when
        val error = runCatching {
            artifact.deleteArtifactDirectoryWithin(owned, describedBy = "skill ''")
        }.exceptionOrNull()

        // then
        assertThat(error).isInstanceOf(ArtifactPathException::class.java)
        assertThat(owned).exists()
    }

    @Test
    fun `should accept an artifact directory inside the owned one without touching it`() {
        // given
        // - a dry run runs the guard alone, so the directory it judges has to survive the judgement
        val artifact = owned.resolve("a-skill")
        artifact.mkdirs()
        artifact.resolve("SKILL.md").writeText("content")

        // when
        artifact.checkArtifactDirectoryWithin(owned, describedBy = "skill 'a-skill'")

        // then
        assertThat(artifact.resolve("SKILL.md")).hasContent("content")
    }

    @ParameterizedTest
    @CsvSource("../../outside", "..", "../evil", ".")
    fun `should refuse a path that is not inside the owned directory without deleting anything`(relativePath: String) {
        // given
        val artifact = owned.resolve(relativePath)

        // when
        val error = runCatching {
            artifact.checkArtifactDirectoryWithin(owned, describedBy = "skill 'escaping'")
        }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(ArtifactPathException::class.java)
            .hasMessageContaining("skill 'escaping'")
        assertThat(outside.resolve("notes.md")).exists()
        assertThat(owned).exists()
    }

    @ParameterizedTest
    @CsvSource("true", "false")
    fun `should refuse a replaced path that is a link leading outside the owned directory, advising to remove the link or turn off replace`(
        deletes: Boolean,
    ) {
        // given
        // - a skill installed by hand as a link to a checkout elsewhere, which a replacing user deploy is about to rewrite
        val link = owned.resolve("linked")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        // when
        val error = runCatching {
            if (deletes) {
                link.deleteArtifactDirectoryWithin(owned, describedBy = "skill 'linked'")
            } else {
                link.checkArtifactDirectoryWithin(owned, describedBy = "skill 'linked'")
            }
        }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(ArtifactPathException::class.java)
            .hasMessage(
                "Refusing to replace '${link.absolutePath}' for 'skill 'linked'': it is a symbolic link that leads to '${outside.canonicalPath}', which is not inside '${owned.absolutePath}'. " +
                    "Remove the link, or turn off 'replace' for that deployment.",
            )
        assertThat(Files.isSymbolicLink(link.toPath())).isTrue()
        assertThat(outside.resolve("notes.md")).hasContent("Kept outside.\n")
    }

    @ParameterizedTest
    @CsvSource(
        // - a skill installed as a link to its development copy beside it in the skills folder
        "home/.claude/skills/linked-dev",
        // - a link left behind after its checkout was removed, which leads nowhere
        "outside/removed-checkout",
    )
    fun `should unlink a replaced path that is a link leading inside the owned directory or leading nowhere, keeping what it leads to`(
        destination: String,
    ) {
        // given
        val target = tempDir.resolve(destination).toFile()
        if (target.startsWith(owned)) {
            target.mkdirs()
            target.resolve("SKILL.md").writeText("Development copy.\n")
        }
        val link = owned.resolve("linked")
        Files.createSymbolicLink(link.toPath(), target.toPath())

        // when
        link.deleteArtifactDirectoryWithin(owned, describedBy = "skill 'linked'")

        // then
        assertThat(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)).isFalse()
        if (target.startsWith(owned)) {
            assertThat(target.resolve("SKILL.md")).hasContent("Development copy.\n")
        } else {
            assertThat(target).doesNotExist()
        }
        assertThat(outside.resolve("notes.md")).hasContent("Kept outside.\n")
    }

    @Test
    fun `should advise an id naming a single directory when a path that is not a link lies outside the owned directory`() {
        // given
        val artifact = owned.resolve("../evil")

        // when
        val error = runCatching {
            artifact.checkArtifactDirectoryWithin(owned, describedBy = "skill 'escaping'")
        }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(ArtifactPathException::class.java)
            .hasMessage("Refusing to replace '${artifact.absolutePath}' for 'skill 'escaping'': it is not inside '${owned.absolutePath}'. Give the manifest an id that names a single directory.")
    }

    @Test
    fun `should name the file of a file system failure as the entry that could not be deleted`() {
        // given
        val failure = AccessDeniedException("/project/.claude/locked/a.md")

        // when
        val entry = failure.failedEntry(Path.of("/project/.claude"))

        // then
        assertThat(entry).isEqualTo("/project/.claude/locked/a.md")
    }

    @ParameterizedTest
    @CsvSource("true", "false")
    fun `should name the directory being deleted as the entry when the failure names no file`(
        fileSystemFailure: Boolean,
    ) {
        // given
        val failure = if (fileSystemFailure) FileSystemException(null) else IOException("Stream closed")

        // when
        val entry = failure.failedEntry(Path.of("/project/.claude"))

        // then
        assertThat(entry).isEqualTo("/project/.claude")
    }

    @Test
    fun `should describe a failed delete by the entry and the type of its cause`() {
        // given
        val cause = AccessDeniedException("/project/.claude/locked/a.md")

        // when
        val error = ArtifactDeleteException("/project/.claude/locked/a.md", cause)

        // then
        assertThat(error.message).isEqualTo("deleting '/project/.claude/locked/a.md' failed (AccessDeniedException)")
        assertThat(error.cause).isSameAs(cause)
    }

    @Test
    fun `should not treat a sibling whose name starts with the owned name as being inside it`() {
        // given
        val sibling = owned.parentFile.resolve("skills-evil")
        sibling.mkdirs()

        // when
        val error = runCatching {
            sibling.deleteArtifactDirectoryWithin(owned, describedBy = "skill 'sibling'")
        }.exceptionOrNull()

        // then
        assertThat(error).isInstanceOf(ArtifactPathException::class.java)
        assertThat(sibling).exists()
    }
}
