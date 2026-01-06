package cz.cleanship.aitools.engine.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ConfigServiceTest {

    @Test
    fun `should load default config when local config is missing`(@TempDir tempDir: File) {
        val configFile = File(tempDir, "config.yml")
        configFile.writeText(
            """
            locations:
              agents:
                - "agents_default"
              projects:
                - "projects_default"
            """.trimIndent()
        )

        val service = ConfigService()
        val locations = service.loadConfig(tempDir)

        assertThat(locations.agents).extracting("name").containsExactly("agents_default")
        assertThat(locations.projects).extracting("name").containsExactly("projects_default")
        assertThat(locations.prompts).isEmpty()
        assertThat(locations.rulesets).isEmpty()
    }

    @Test
    fun `should override default config with local config`(@TempDir tempDir: File) {
        val configFile = File(tempDir, "config.yml")
        configFile.writeText(
            """
            locations:
              agents:
                - "agents_default"
              projects:
                - "projects_default"
            """.trimIndent()
        )

        val localConfigFile = File(tempDir, "config.local.yml")
        localConfigFile.writeText(
            """
            locations:
              projects:
                - "projects_local"
              rulesets:
                - "rulesets_local"
            """.trimIndent()
        )

        val service = ConfigService()
        val locations = service.loadConfig(tempDir)

        // Agents should settle on default (not in local)
        assertThat(locations.agents).extracting("name").containsExactly("agents_default")
        
        // Projects should be overridden by local
        assertThat(locations.projects).extracting("name").containsExactly("projects_local")
        
        // Rulesets should take local value (default was null/empty)
        assertThat(locations.rulesets).extracting("name").containsExactly("rulesets_local")
        
        // Prompts should stay empty
        assertThat(locations.prompts).isEmpty()
    }

    @Test
    fun `should handle missing config file gracefully`(@TempDir tempDir: File) {
        val service = ConfigService()
        val locations = service.loadConfig(tempDir)

        assertThat(locations.agents).isEmpty()
        assertThat(locations.projects).isEmpty()
        assertThat(locations.prompts).isEmpty()
        assertThat(locations.rulesets).isEmpty()
    }
}
