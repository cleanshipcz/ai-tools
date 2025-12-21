package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.PromptOutput
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PromptPrinterTest {

    @Test
    fun `should print prompt manifest`() {
        // given
        val prompt = PromptManifest(
            id = "id#1",
            description = """
                Multiline
                description#1
            """.trimIndent(),
            variables = listOf(
                PromptVariable(
                    name = "var1", required = true, description = """
                    Multiline
                    description#2
                """.trimIndent()
                ),
                PromptVariable(
                    name = "var2", required = false, description = """
                    Multiline
                    description#3
                """.trimIndent()
                ),
            ),
            rules = listOf("rule1", "rule2"),
            content = """
                Multiline
                content#3
            """.trimIndent(),
            outputs = PromptOutput(format = "format1", examples = listOf("example1", "example2")),
            metadata = ManifestMetadata(
                version = Version("1.2.3"),
                author = "Test Author",
                created = "2025-01-01",
            ),
        )
        val output = StringOutput()

        // when
        output.use {
            PromptPrinter().print(prompt, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(
            """
            # id#1
            
            Multiline
            description#1
            
            ## Variables
            
            - `{{var1}}` (required): Multiline
              description#2
            - `{{var2}}`: Multiline
              description#3
            
            ## Rules
            
            - rule1
            - rule2
            
            ## Prompt
            
            Multiline
            content#3
            
        """.trimIndent()
        )
    }

}