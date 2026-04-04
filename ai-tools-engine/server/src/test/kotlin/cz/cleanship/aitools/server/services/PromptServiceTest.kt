package cz.cleanship.aitools.server.services

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PromptServiceTest {

    private fun createPrompt(
        id: String,
        content: String,
        variables: List<PromptVariable> = emptyList(),
        tags: Set<String> = emptySet(),
        rules: List<String> = emptyList(),
    ) = PromptManifest(
        id = id,
        description = "Description for $id",
        content = content,
        variables = variables,
        rules = rules,
        metadata = ManifestMetadata(version = Version("1.0.0"), tags = tags),
    )

    private val service = PromptService(
        mapOf(
            "test-prompt" to createPrompt(
                id = "test-prompt",
                content = "Hello {{name}}, welcome to {{place}}!",
                variables = listOf(
                    PromptVariable(name = "name", required = true, description = "User name"),
                    PromptVariable(name = "place", required = false, description = "Location"),
                ),
                tags = setOf("greeting", "test"),
                rules = listOf("Be friendly"),
            ),
            "conditional-prompt" to createPrompt(
                id = "conditional-prompt",
                content = "Start. {{#context}}Context: {{context}}{{/context}} End.",
            ),
            "no-vars" to createPrompt(
                id = "no-vars",
                content = "Static content with no variables.",
            ),
        ),
    )

    @Test
    fun `listPrompts returns all prompts`() {
        val result = service.listPrompts()

        assertThat(result).hasSize(3)
        assertThat(result.map { it.id }).containsExactlyInAnyOrder("test-prompt", "conditional-prompt", "no-vars")
    }

    @Test
    fun `listPrompts includes tags and variables`() {
        val prompt = service.listPrompts().first { it.id == "test-prompt" }

        assertThat(prompt.tags).containsExactlyInAnyOrder("greeting", "test")
        assertThat(prompt.variables).hasSize(2)
        assertThat(prompt.variables.first { it.name == "name" }.required).isTrue()
        assertThat(prompt.variables.first { it.name == "place" }.required).isFalse()
    }

    @Test
    fun `getPrompt returns detail for existing id`() {
        val result = service.getPrompt("test-prompt")

        assertThat(result).isNotNull
        assertThat(result!!.content).contains("{{name}}")
        assertThat(result.rules).containsExactly("Be friendly")
        assertThat(result.version).isEqualTo("1.0.0")
    }

    @Test
    fun `getPrompt returns null for unknown id`() {
        assertThat(service.getPrompt("nonexistent")).isNull()
    }

    @Test
    fun `fillPrompt returns null for unknown id`() {
        assertThat(service.fillPrompt("nonexistent", emptyMap())).isNull()
    }

    @Nested
    inner class FillLogic {

        @Test
        fun `replaces simple variables`() {
            val result = service.fillPrompt("test-prompt", mapOf("name" to "Alice", "place" to "Wonderland"))

            assertThat(result).isEqualTo("Hello Alice, welcome to Wonderland!")
        }

        @Test
        fun `leaves unreplaced variables as-is when not provided`() {
            val result = service.fillPrompt("test-prompt", mapOf("name" to "Alice"))

            assertThat(result).isEqualTo("Hello Alice, welcome to {{place}}!")
        }

        @Test
        fun `conditional block included when value is present`() {
            val result = service.fillPrompt("conditional-prompt", mapOf("context" to "important stuff"))

            assertThat(result).isEqualTo("Start. Context: important stuff End.")
        }

        @Test
        fun `conditional block removed when value is blank`() {
            val result = service.fillPrompt("conditional-prompt", mapOf("context" to ""))

            assertThat(result).isEqualTo("Start.  End.")
        }

        @Test
        fun `orphaned conditional blocks are cleaned up`() {
            val result = service.fillPrompt("conditional-prompt", emptyMap())

            assertThat(result).isEqualTo("Start.  End.")
        }

        @Test
        fun `dollar signs in content are preserved`() {
            val result = service.fill("Price: {{amount}}", mapOf("amount" to "$100"))

            assertThat(result).isEqualTo("Price: $100")
        }

        @Test
        fun `dollar signs in conditional block content are preserved`() {
            val result = service.fill("{{#price}}Cost: {{price}}{{/price}}", mapOf("price" to "$50"))

            assertThat(result).isEqualTo("Cost: $50")
        }

        @Test
        fun `regex metacharacters in variable keys are handled safely`() {
            val result = service.fill("Value: {{key.name}}", mapOf("key.name" to "safe"))

            assertThat(result).isEqualTo("Value: safe")
        }

        @Test
        fun `matching open and close tags required for cleanup`() {
            val result = service.fill("{{#a}}content a{{/a}} {{#b}}content b{{/b}}", emptyMap())

            assertThat(result).isEqualTo(" ")
        }

        @Test
        fun `mismatched tags are not cleaned up`() {
            val result = service.fill("{{#a}}content{{/b}}", emptyMap())

            assertThat(result).isEqualTo("{{#a}}content{{/b}}")
        }
    }
}
