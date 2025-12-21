package cz.cleanship.aitools.engine.models

import cz.cleanship.aitools.engine.services.LoaderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class RulepackManifestTest {

    private val loaderService = LoaderService()

    @Test
    fun `should deserialize rulepack correctly`() {
        // given
        val file = File(javaClass.getResource("/rulepacks/base.yml")!!.toURI())

        // when
        val rulepack = loaderService.loadRulepack(file)

        // then
        assertThat(rulepack.id).isEqualTo("base")
        assertThat(rulepack.version).isEqualTo(Version("1.1.0"))
        assertThat(rulepack.description).isEqualTo("Base rules applicable to all agents and prompts")
        assertThat(rulepack.rules).containsExactlyInAnyOrder(
            "Be precise and accurate in your responses.",
            "Follow the user's requirements carefully and to the letter.",
            "If you are unsure, ask for clarification instead of guessing.",
            "Break complex tasks into smaller, manageable steps.",
            "Verify your work before presenting it.",
        )
        assertThat(rulepack.extends).isEmpty()
        assertThat(rulepack.metadata).isEqualTo(
            ManifestMetadata(
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = "2025-11-16",
                tags = setOf("foundation", "general"),
            )
        )
    }

}
