package cz.cleanship.aitools.engine.models

import cz.cleanship.aitools.engine.services.LoaderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class AgentManifestTest {

    private val loaderService = LoaderService()

    @Test
    fun `should deserialize agent correctly`() {
        // given
        val file = File(javaClass.getResource("/agents/code-reviewer.yml")!!.toURI())

        // when
        val agent = loaderService.loadAgent(file)

        // then
        assertThat(agent.id).isEqualTo("code-reviewer")
        assertThat(agent.version).isEqualTo(Version("2.1.0"))
        assertThat(agent.purpose).isEqualTo("Structured code review with actionable findings")
        assertThat(agent.description?.trim()).isEqualTo(
            """
            A senior code reviewer agent that analyzes code changes, identifies issues,
            and provides constructive, actionable feedback.
            """.trimIndent()
        )
        assertThat(agent.rulepacks).containsExactlyInAnyOrder("base", "reviewer", "security")
        assertThat(agent.capabilities).containsExactlyInAnyOrder("mcp:git", "mcp:filesystem")
        assertThat(agent.defaults).isEqualTo(
            AgentDefaults(
                temperature = 0.2,
                style = "terse",
            )
        )
        assertThat(agent.prompt.system.trim()).isEqualTo(
            """
            You are a senior software engineer conducting a thorough code review.
            Your goal is to identify defects, security risks, performance issues,
            and areas for improvement while being constructive and educational.

            Focus on:
            - Correctness and logic errors
            - Security vulnerabilities
            - Performance bottlenecks
            - Code maintainability
            - Test coverage
            - Documentation quality

            Provide specific, actionable feedback with examples where possible.
            """.trimIndent()
        )
        assertThat(agent.prompt.userTemplate?.trim()).isEqualTo(
            """
            Review the following changes:

            Repository context:
            {{context}}

            Diff:
            ```diff
            {{diff}}
            ```

            {{#focus}}
            Special focus areas: {{focus}}
            {{/focus}}

            Provide a structured review with:
            1. Critical issues (must fix)
            2. Important suggestions (should fix)
            3. Minor improvements (nice to have)
            4. Positive observations
            """.trimIndent()
        )
        assertThat(agent.tools).containsExactlyInAnyOrder("git-diff", "read-file", "search-code")
        assertThat(agent.constraints).containsExactlyInAnyOrder(
            "Do not approve code with security vulnerabilities.",
            "Flag missing test coverage for new functionality.",
            "Suggest specific improvements, not just criticism.",
        )
        assertThat(agent.metadata).isEqualTo(
            ManifestMetadata(
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = null,
                tags = setOf("review", "quality"),
            )
        )
    }

}
