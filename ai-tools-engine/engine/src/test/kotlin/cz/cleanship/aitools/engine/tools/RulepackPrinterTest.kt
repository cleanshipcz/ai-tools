package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.RulepackManifest
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class RulepackPrinterTest {

    @Test
    fun `should print rulepack manifest`() {
        // given
        val rulepack = RulepackManifest(
            id = "test-rulepack",
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
                version = Version("1.2.3"),
                author = "Test Author",
                created = "2025-01-01",
            ),
        )
        val output = StringOutput()

        // when
        output.use {
            RulepackPrinter().print(rulepack, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo("""
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
