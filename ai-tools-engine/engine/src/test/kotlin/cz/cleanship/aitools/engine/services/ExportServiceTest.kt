package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.data.ruleset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ExportServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var exportService: ExportService
    private lateinit var targetFile: File

    @BeforeEach
    fun setUp() {
        exportService = ExportService()
        targetFile = tempDir.resolve("nested").resolve("ruleset.md").toFile()
    }

    @Test
    fun `should write exported content when output consumer succeeds`() {
        // when
        exportService.export(ruleset, targetFile) { output ->
            output.appendLine("# header")
            output.appendLine("body")
        }

        // then
        assertThat(targetFile).exists()
        assertThat(targetFile.readText()).isEqualTo("# header\nbody\n")
    }

    @Test
    fun `should replace existing target content when output consumer succeeds`() {
        // given
        // - a stale artifact from a previous export at the same path
        targetFile.parentFile.mkdirs()
        targetFile.writeText("stale content from a previous run")

        // when
        exportService.export(ruleset, targetFile) { output ->
            output.appendLine("fresh")
        }

        // then
        assertThat(targetFile.readText()).isEqualTo("fresh\n")
    }

    @Test
    fun `should not create target file when output consumer fails mid-write`() {
        // when
        val error = runCatching {
            exportService.export(ruleset, targetFile) { output ->
                output.appendLine("---")
                output.appendLine("frontmatter written")
                error("resolution failed while printing the body")
            }
        }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("resolution failed while printing the body")
        assertThat(targetFile).doesNotExist()
    }

    @Test
    fun `should keep previous target content when output consumer fails mid-write`() {
        // given
        // - a complete artifact from a previous successful export
        targetFile.parentFile.mkdirs()
        targetFile.writeText("previous complete content")

        // when
        runCatching {
            exportService.export(ruleset, targetFile) { output ->
                output.appendLine("partial")
                error("resolution failed while printing the body")
            }
        }

        // then
        assertThat(targetFile.readText()).isEqualTo("previous complete content")
    }

    @Test
    fun `should not leave temporary files behind when output consumer fails`() {
        // when
        runCatching {
            exportService.export(ruleset, targetFile) { output ->
                output.appendLine("partial")
                error("resolution failed while printing the body")
            }
        }

        // then
        assertThat(targetFile.parentFile.listFiles()).isEmpty()
    }

    @Test
    fun `should not leave temporary files behind when export succeeds`() {
        // when
        exportService.export(ruleset, targetFile) { output ->
            output.appendLine("content")
        }

        // then
        assertThat(targetFile.parentFile.listFiles()).containsExactly(targetFile)
    }
}
