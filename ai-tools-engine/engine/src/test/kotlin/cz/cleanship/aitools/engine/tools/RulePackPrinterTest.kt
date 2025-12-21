package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.RulepackManifest
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class RulePackPrinterTest {

    @Test
    fun `should print rulepack manifest`() {
        // given
        val rulepack = RulepackManifest(
            id = "test-rulepack",
            version = Version("1.2.3"),
            description = """
                Multiline
                description
            """.trimIndent(),
            rules = listOf(
                "Rule number one.",
                "Rule number two.",
                "Rule number three.",
            ),
            extends = listOf("base", "security"),
            metadata = ManifestMetadata(
                author = "Test Author",
                created = "2025-01-01",
            ),
        )
        val byteArrayOutputStream = ByteArrayOutputStream()
        val output = Output(byteArrayOutputStream)

        // when
        RulePackPrinter().print(rulepack, output)
        output.close()

        // then
        assertThat(byteArrayOutputStream.toString().trimIndent()).isEqualTo("""
            # test-rulepack
            
            Multiline
            description
            
            ## Rules
            
            - Rule number one.
            - Rule number two.
            - Rule number three.
            
        """.trimIndent())
    }

}
