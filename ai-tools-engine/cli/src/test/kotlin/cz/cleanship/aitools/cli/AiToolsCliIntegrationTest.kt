package cz.cleanship.aitools.cli

import com.github.ajalt.clikt.core.main
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AiToolsCliIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should run tools application when config exists`() {
        // given
        File(tempDir, "config.yml").writeText("{}")
        val cli = AiToolsCli()

        // when
        val error = runCatching {
            cli.main(arrayOf("--working-dir", tempDir.absolutePath))
        }.exceptionOrNull()

        // then
        assertThat(error).isNull()
    }
}
