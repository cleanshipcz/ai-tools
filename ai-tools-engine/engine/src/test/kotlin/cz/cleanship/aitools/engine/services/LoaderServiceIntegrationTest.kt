package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.PromptOutput
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class LoaderServiceIntegrationTest {

    private lateinit var loaderService: LoaderService

    @BeforeEach
    fun setUp() {
        loaderService = LoaderService()
    }

    @Test
    fun `should deserialize agent correctly`() {
        // given
        val file = File(javaClass.getResource("/agents/code-reviewer.yml")!!.toURI())

        // when
        val agent = loaderService.loadAgent(file)

        // then
        assertThat(agent.id).isEqualTo("code-reviewer")
        assertThat(agent.description.trim()).isEqualTo(
            """
            A senior code reviewer agent that analyzes code changes, identifies issues,
            and provides constructive, actionable feedback.
            """.trimIndent()
        )
        assertThat(agent.rulesets).containsExactlyInAnyOrder("base")
        assertThat(agent.persona.trim()).isEqualTo(
            """
            You are a senior software engineer conducting a thorough code review.
            Your goal is to identify defects, security risks, performance issues,
            and areas for improvement while being constructive and educational.
            """.trimIndent()
        )
        assertThat(agent.prompt.trim()).isEqualTo(
            """
            Focus on:
            - Correctness and logic errors
            - Security vulnerabilities
            - Performance bottlenecks
            - Code maintainability
            - Test coverage
            - Documentation quality
            
            Review the changes (to be provided later).

            Provide a structured review with:
            1. Critical issues (must fix)
            2. Important suggestions (should fix)
            3. Minor improvements (nice to have)
            4. Positive observations
            
            Provide specific, actionable feedback with examples where possible.
            """.trimIndent()
        )
        assertThat(agent.constraints).containsExactlyInAnyOrder(
            "Do not approve code with security vulnerabilities.",
            "Flag missing test coverage for new functionality.",
            "Suggest specific improvements, not just criticism.",
        )
        assertThat(agent.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("2.1.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = null,
                tags = setOf("review", "quality"),
            )
        )
    }

    @Test
    fun `should deserialize prompt correctly`() {
        // given
        val file = File(javaClass.getResource("/prompts")!!.toURI()).resolve("git").resolve("summarize-pr.yml")

        // when
        val prompt = loaderService.loadPrompt(file)

        // then
        assertThat(prompt.id).isEqualTo("git-summarize-pr")
        assertThat(prompt.description).isEqualTo("Generate a concise summary of a pull request")
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
        assertThat(prompt.rulesets).containsExactlyInAnyOrder("base")
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
                version = Version("1.0.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = null,
                tags = setOf("docs", "pr", "git"),
            )
        )
    }

    @Test
    fun `should deserialize ruleset correctly`() {
        // given
        val file = File(javaClass.getResource("/rulesets/base.yml")!!.toURI())

        // when
        val ruleset = loaderService.loadRuleset(file)

        // then
        assertThat(ruleset.id).isEqualTo("base")
        assertThat(ruleset.description).isEqualTo("Base rules applicable to all agents and prompts")
        assertThat(ruleset.rules).containsExactlyInAnyOrder(
            "Be precise and accurate in your responses.",
            "Follow the user's requirements carefully and to the letter.",
            "If you are unsure, ask for clarification instead of guessing.",
            "Break complex tasks into smaller, manageable steps.",
            "Verify your work before presenting it.",
        )
        assertThat(ruleset.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("1.1.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = "2025-11-16",
                tags = setOf("foundation", "general"),
            )
        )
    }
}