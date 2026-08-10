package cz.cleanship.aitools.engine.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
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
