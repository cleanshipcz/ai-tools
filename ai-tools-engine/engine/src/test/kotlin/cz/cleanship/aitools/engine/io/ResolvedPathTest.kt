package cz.cleanship.aitools.engine.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

class ResolvedPathTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var real: File

    @BeforeEach
    fun setUp() {
        real = tempDir.toPath().toRealPath().toFile()
    }

    @Test
    fun `should give an existing file its real path`() {
        // given
        val file = real.resolve("project/.mcp.json").also { it.parentFile.mkdirs() }.also { it.writeText("{}") }

        // when
        val resolved = file.resolvedPath()

        // then
        assertThat(resolved).isEqualTo(file)
    }

    @Test
    fun `should give a file below a linked directory the path below the directory the link leads to`() {
        // given
        val project = real.resolve("project").also { it.mkdirs() }
        val link = real.resolve("link").toPath()
        Files.createSymbolicLink(link, project.toPath())

        // when
        val resolved = link.toFile().resolve(".codex/config.toml").resolvedPath()

        // then
        assertThat(resolved).isEqualTo(project.resolve(".codex/config.toml"))
    }

    @Test
    fun `should resolve the nearest existing directory and keep the missing rest of the path`() {
        // when
        val resolved = real.resolve("missing/./deeper/../file.json").resolvedPath()

        // then
        assertThat(resolved).isEqualTo(real.resolve("missing/file.json"))
    }

    @Test
    fun `should keep the normalized absolute path of a link that leads nowhere`() {
        // given
        val dangling = real.resolve("dangling").toPath()
        Files.createSymbolicLink(dangling, real.resolve("nowhere").toPath())

        // when
        val resolved = dangling.toFile().resolve("file.json").resolvedPath()

        // then
        assertThat(resolved).isEqualTo(real.resolve("dangling/file.json"))
    }
}
