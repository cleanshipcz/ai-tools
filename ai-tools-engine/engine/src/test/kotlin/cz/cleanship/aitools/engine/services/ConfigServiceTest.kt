package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.ToolType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileNotFoundException

class ConfigServiceTest {

    @Test
    fun `should load default config when local config is missing`(
        @TempDir tempDir: File,
    ) {
        val configFile = File(tempDir, "config.yml")
        configFile.writeText(
            """
            locations:
              agents:
                - "agents_default"
              projects:
                - "projects_default"
            tools:
                - windsurf
            """.trimIndent(),
        )

        val service = ConfigService()
        val (locations, tools) = service.loadConfig(tempDir)

        assertThat(locations.agents).extracting("name").containsExactly("agents_default")
        assertThat(locations.projects).extracting("name").containsExactly("projects_default")
        assertThat(locations.prompts).isEmpty()
        assertThat(locations.rulesets).isEmpty()
        assertThat(tools).containsExactly(ToolType.WINDSURF)
    }

    @Test
    fun `should override default config with local config`(
        @TempDir tempDir: File,
    ) {
        val configFile = File(tempDir, "config.yml")
        configFile.writeText(
            """
            locations:
              agents:
                - "agents_default"
              projects:
                - "projects_default"
            tools:
                - windsurf
            """.trimIndent(),
        )

        val localConfigFile = File(tempDir, "config.local.yml")
        localConfigFile.writeText(
            """
            locations:
              projects:
                - "projects_local"
              rulesets:
                - "rulesets_local"
            tools:
                - antigravity
                - github_copilot
            """.trimIndent(),
        )

        val service = ConfigService()
        val (locations, tools) = service.loadConfig(tempDir)

        // Agents should settle on default (not in local)
        assertThat(locations.agents).extracting("name").containsExactly("agents_default")

        // Projects should be overridden by local
        assertThat(locations.projects).extracting("name").containsExactly("projects_local")

        // Rulesets should take local value (default was null/empty)
        assertThat(locations.rulesets).extracting("name").containsExactly("rulesets_local")

        // Prompts should stay empty
        assertThat(locations.prompts).isEmpty()

        // Tools should be overridden by local
        assertThat(tools).containsExactly(ToolType.ANTIGRAVITY, ToolType.GITHUB_COPILOT)
    }

    @Test
    fun `should throw exception when missing config file`(
        @TempDir tempDir: File,
    ) {
        // given
        val service = ConfigService()

        // when
        val exception = assertThatThrownBy {
            service.loadConfig(tempDir)
        }

        // then
        exception.isInstanceOf(FileNotFoundException::class.java)
    }
}
