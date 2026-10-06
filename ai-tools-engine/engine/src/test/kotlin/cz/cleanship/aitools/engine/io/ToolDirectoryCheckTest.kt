package cz.cleanship.aitools.engine.io

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.nio.file.Files

class ToolDirectoryCheckTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var root: File
    private lateinit var toolDir: File

    @BeforeEach
    fun setUp() {
        root = tempDir.resolve("root").also { it.mkdirs() }
        toolDir = root.resolve(".codex")
    }

    @ParameterizedTest
    @CsvSource("project", "home")
    fun `should accept a tool directory that does not exist yet or is a directory`(scope: String) {
        // given
        val target = targetRoot(scope)
        val existing = root.resolve(".claude").also { it.mkdirs() }

        // when / then
        assertThatCode {
            target.requireToolDirectory(toolDir, "codex files of test")
            target.requireToolDirectory(existing, "claude files of test")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `should accept a tool directory of a root that does not exist yet`() {
        // when / then
        assertThatCode { TargetRoot.Project(tempDir.resolve("missing")).requireToolDirectory(tempDir.resolve("missing/.codex"), "codex files of test") }
            .doesNotThrowAnyException()
    }

    @ParameterizedTest
    @CsvSource(
        "project, dangling",
        "project, looping",
        "home, dangling",
        "home, looping",
    )
    fun `should fail naming the directory, where it leads and what is not written when it is a link that cannot be followed`(
        scope: String,
        kind: String,
    ) {
        // given
        val leadsTo = if (kind == "dangling") tempDir.resolve("out/missing") else root.resolve("loop").also { Files.createSymbolicLink(it.toPath(), toolDir.toPath()) }
        Files.createSymbolicLink(toolDir.toPath(), leadsTo.toPath())

        // when / then
        assertThatThrownBy { targetRoot(scope).requireToolDirectory(toolDir, "codex files of project 'test'") }
            .isInstanceOf(ToolDirectoryException::class.java)
            .hasMessageContaining("'${toolDir.absolutePath}'")
            .hasMessageContaining("'${leadsTo.absolutePath}'")
            .hasMessageContaining("cannot be followed")
            .hasMessageContaining("writes no codex files of project 'test'")
    }

    @ParameterizedTest
    @CsvSource("project", "home")
    fun `should fail naming the directory when a regular file stands where it belongs`(scope: String) {
        // given
        toolDir.writeText("not a directory\n")

        // when / then
        assertThatThrownBy { targetRoot(scope).requireToolDirectory(toolDir, "codex files of project 'test'") }
            .isInstanceOf(ToolDirectoryException::class.java)
            .hasMessageContaining("'${toolDir.absolutePath}' is not a directory")
            .hasMessageContaining("writes no codex files of project 'test'")
        assertThat(toolDir).hasContent("not a directory\n")
    }

    @Test
    fun `should fail naming the directory, where it leads and the project when it links outside the project`() {
        // given
        val outside = tempDir.resolve("dotfiles/codex").also { it.mkdirs() }
        Files.createSymbolicLink(toolDir.toPath(), outside.toPath())

        // when / then
        assertThatThrownBy { TargetRoot.Project(root).requireToolDirectory(toolDir, "codex files of project 'test'") }
            .isInstanceOf(ToolDirectoryException::class.java)
            .hasMessageContaining("'${toolDir.absolutePath}'")
            .hasMessageContaining("'${outside.canonicalPath}'")
            .hasMessageContaining("outside the project directory '${root.canonicalPath}'")
    }

    @Test
    fun `should accept a tool directory that links inside the project`() {
        // given
        val inside = root.resolve("shared/codex").also { it.mkdirs() }
        Files.createSymbolicLink(toolDir.toPath(), inside.toPath())

        // when / then
        assertThatCode { TargetRoot.Project(root).requireToolDirectory(toolDir, "codex files of project 'test'") }.doesNotThrowAnyException()
    }

    @Test
    fun `should follow a tool directory of the home that links anywhere, as a dotfile repository does`() {
        // given
        val outside = tempDir.resolve("dotfiles/codex").also { it.mkdirs() }
        Files.createSymbolicLink(toolDir.toPath(), outside.toPath())

        // when / then
        assertThatCode { TargetRoot.UserHome(root).requireToolDirectory(toolDir, "codex files of user deployment 'globals'") }.doesNotThrowAnyException()
    }

    @Test
    fun `should fail when a tool directory of the home links to a regular file`() {
        // given
        val outside = tempDir.resolve("dotfiles/codex").also { it.parentFile.mkdirs() }
        outside.writeText("a file\n")
        Files.createSymbolicLink(toolDir.toPath(), outside.toPath())

        // when / then
        assertThatThrownBy { TargetRoot.UserHome(root).requireToolDirectory(toolDir, "codex files of user deployment 'globals'") }
            .isInstanceOf(ToolDirectoryException::class.java)
            .hasMessageContaining("'${toolDir.absolutePath}' is not a directory")
    }

    private fun targetRoot(scope: String): TargetRoot = if (scope == "project") TargetRoot.Project(root) else TargetRoot.UserHome(root)
}
