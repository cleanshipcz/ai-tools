package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.AgentDefaults
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.AgentPrompt
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPrinterTest {

    @Test
    fun `should print agent manifest`() {
        // given
        val agent = AgentManifest(
            id = "test-agent",
            description = """
                Multiline
                description
            """.trimIndent(),
            rulepacks = listOf("base", "security"),
            persona = """
                Multiline
                persona
            """.trimIndent(),
            prompt = """
                    Multiline
                    system prompt
                """.trimIndent(),
            constraints = listOf("Constraint one.", "Constraint two."),
            metadata = ManifestMetadata(
                version = Version("1.2.3"),
                author = "Test Author",
                created = "2025-01-01",
            ),
        )
        val output = StringOutput()

        // when
        output.use {
            AgentPrinter().print(agent, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(
            """
            # test-agent

            Multiline
            description
            
            ## Persona
            
            Multiline
            persona
            
            ## Rulepacks
            
            - base
            - security
            
            ## Prompt
            
            Multiline
            system prompt
            
            ## Constraints
            
            - Constraint one.
            - Constraint two.
            
        """.trimIndent()
        )
    }

}
