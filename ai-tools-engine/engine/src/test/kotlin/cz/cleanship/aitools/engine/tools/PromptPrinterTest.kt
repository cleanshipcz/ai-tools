package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.PromptOutput
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class PromptPrinterTest {

    @Test
    fun `should print prompt manifest`() {
        // given
        val prompt = PromptManifest(
            id = "id#1",
            version = Version("1.2.3"),
            description = """
                Multiline
                description#1
            """.trimIndent(),
            tags = setOf("tag1", "tag2"),
            variables = listOf(
                PromptVariable(name = "var1", required =  true, description = """
                    Multiline
                    description#2
                """.trimIndent()),
                PromptVariable(name = "var2", required =  false, description = """
                    Multiline
                    description#3
                """.trimIndent()),
            ),
            rules = listOf("rule1", "rule2"),
            content = """
                Multiline
                content#3
            """.trimIndent(),
            outputs = PromptOutput(format = "format1", examples = listOf("example1", "example2")),
        )
        val byteArrayOutputStream = ByteArrayOutputStream()
        val output = Output(byteArrayOutputStream)

        // when
        PromptPrinter().print(prompt, output)
        output.close()

        // then
        assertThat(byteArrayOutputStream.toString().trimIndent()).isEqualTo("""
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
              
        """.trimIndent())
    }

}