package cz.cleanship.aitools.engine.models

import cz.cleanship.aitools.engine.services.LoaderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class PromptManifestTest {

    private val loaderService = LoaderService()

    @Test
    fun `should deserialize prompt correctly`() {
        // given
        val file = File(javaClass.getResource("/prompts/summarize-pr.yml")!!.toURI())

        // when
        val prompt = loaderService.loadPrompt(file)

        // then
        assertThat(prompt.id).isEqualTo("summarize-pr")
        assertThat(prompt.version).isEqualTo(Version("1.0.0"))
        assertThat(prompt.description).isEqualTo("Generate a concise summary of a pull request")
        assertThat(prompt.tags).containsExactlyInAnyOrder("docs", "pr", "git")
        assertThat(prompt.variables).containsExactlyInAnyOrder(
            PromptVariable(name = "diff", required = true, description = "The git diff to summarize"),
            PromptVariable(name = "context", required = false, description = "Additional context about the PR")
        )
        assertThat(prompt.rules).containsExactlyInAnyOrder(
            "Be concise but comprehensive.",
            "Highlight breaking changes prominently.",
            "Group related changes together.",
            "Use bullet points for clarity.",
        )
        assertThat(prompt.content.trim()).isEqualTo(
            """
            Analyze this pull request and provide a clear summary:
            
            ```diff
            {{diff}}
            ```
            
            {{#context}}
            Context: {{context}}
            {{/context}}
            
            Provide:
            1. **Overview**: One-line summary of what this PR does
            2. **Changes**: Bullet points of key changes
            3. **Breaking Changes**: Any backward-incompatible changes (if any)
            4. **Testing**: What testing was done or is needed
            5. **Impact**: Areas of the codebase affected
            
            Format as markdown suitable for a PR description.
            """.trimIndent()
        )
        assertThat(prompt.outputs).isEqualTo(
            PromptOutput(
                format = "markdown",
                examples = emptyList(),
            )
        )
        assertThat(prompt.metadata).isEqualTo(
            ManifestMetadata(
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = null,
                tags = emptySet(),
            )
        )
    }

}
