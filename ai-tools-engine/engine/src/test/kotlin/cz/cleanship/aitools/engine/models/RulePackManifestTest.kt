package cz.cleanship.aitools.engine.models

import cz.cleanship.aitools.engine.services.LoaderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class RulePackManifestTest {

    private val loaderService = LoaderService()

    @Test
    fun `should deserialize rulepack correctly`() {
        // given
        val file = File(javaClass.getResource("/rulepacks/base.yml")!!.toURI())

        // when
        val rulepack = loaderService.loadRulepack(file)

        // then
        assertThat(rulepack.id).isEqualTo("base")
        assertThat(rulepack.version).isEqualTo("1.1.0")
        assertThat(rulepack.description).isEqualTo("Base rules applicable to all agents and prompts")
        assertThat(rulepack.rules).containsExactlyInAnyOrder(
            "Be precise and accurate in your responses.",
            "Follow the user's requirements carefully and to the letter.",
            "If you are unsure, ask for clarification instead of guessing.",
            "Break complex tasks into smaller, manageable steps.",
            "Verify your work before presenting it.",
            "Use clear, concise language.",
            "Preserve existing functionality unless explicitly asked to change it.",
            "Document non-obvious decisions and trade-offs.",
            "Search for up-to-date information and resources.",
            "Reflect changes in the relevant documentation.",
            "Automated tests are mandatory, not optional. Manual testing is insufficient for verification.",
            "Test infrastructure must be in place before implementing features.",
            "All code changes must include corresponding test changes.",
            "When running inside an IDE, prefer using native read/write tools rather than CLI tools.",
            "Never delete or disable problematic functionality to fake solving a bug or other issue. Fix the root cause instead. Same with failing tests.",
        )
        assertThat(rulepack.extends).isEmpty()
        assertThat(rulepack.tags).containsExactlyInAnyOrder("foundation", "general")
        assertThat(rulepack.metadata).isEqualTo(
            ManifestMetadata(
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = "2025-11-16",
                tags = emptySet(),
            )
        )
    }

}
