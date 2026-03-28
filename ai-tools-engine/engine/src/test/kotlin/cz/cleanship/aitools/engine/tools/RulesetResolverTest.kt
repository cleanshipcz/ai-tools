package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RulesetResolverTest {

    private val resolver = RulesetResolver()

    private fun createRuleset(id: String, rules: List<String> = listOf("Rule from $id")) = RulesetManifest(
        id = id,
        description = "Description for $id",
        rules = rules,
        metadata = ManifestMetadata(version = Version("1.0.0")),
    )

    @Nested
    inner class ExactMatching {

        @Test
        fun `should match ruleset by exact id`() {
            // given
            val rulesets = mapOf(
                "base" to createRuleset("base"),
                "coding-java" to createRuleset("coding-java"),
            )

            // when
            val result = resolver.resolve(listOf("base"), rulesets)

            // then
            assertThat(result).extracting("id").containsExactly("base")
        }
    }

    @Nested
    inner class RegexPatternMatching {

        @Test
        fun `should match rulesets using regex wildcard`() {
            // given
            val rulesets = mapOf(
                "base" to createRuleset("base"),
                "coding-java" to createRuleset("coding-java"),
                "coding-kotlin" to createRuleset("coding-kotlin"),
            )

            // when
            val result = resolver.resolve(listOf("coding-.*"), rulesets)

            // then
            assertThat(result)
                .extracting("id")
                .containsExactlyInAnyOrder("coding-java", "coding-kotlin")
        }

        @Test
        fun `should match rulesets combining exact and regex patterns`() {
            // given
            val rulesets = mapOf(
                "base" to createRuleset("base"),
                "coding-java" to createRuleset("coding-java"),
                "coding-kotlin" to createRuleset("coding-kotlin"),
            )

            // when
            val result = resolver.resolve(listOf("base", "coding-.*"), rulesets)

            // then
            assertThat(result)
                .extracting("id")
                .containsExactlyInAnyOrder("base", "coding-java", "coding-kotlin")
        }

        @Test
        fun `should not duplicate rulesets when patterns overlap`() {
            // given
            val rulesets = mapOf(
                "coding-java" to createRuleset("coding-java"),
            )

            // when
            val result = resolver.resolve(listOf("coding-java", "coding-.*"), rulesets)

            // then
            assertThat(result).extracting("id").containsExactly("coding-java")
        }

        @Test
        fun `should match using standard regex quantifier`() {
            // given
            val rulesets = mapOf(
                "base-v" to createRuleset("base-v"),
                "base-v1" to createRuleset("base-v1"),
            )

            // pattern "base-v[0-9]*" should match "base-v" (0 digits) and "base-v1" (1 digit)
            // Current glob implementation transforms * -> .* results in "base-v[0-9].*" which requires at least one char after v that is a digit?
            // Actually [0-9].* matches a digit then anything. So "base-v" would fail to match [0-9].

            // when
            val result = resolver.resolve(listOf("base-v[0-9]*"), rulesets)

            // then
            assertThat(result)
                .extracting("id")
                .containsExactlyInAnyOrder("base-v", "base-v1")
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `should throw when pattern matches no rulesets`() {
            // given
            val rulesets = mapOf(
                "base" to createRuleset("base"),
            )

            // when/then
            assertThatThrownBy {
                resolver.resolve(listOf("nonexistent-.*"), rulesets)
            }.isInstanceOf(RulesetResolvingException::class.java)
                .hasMessageContaining("nonexistent-.*")
        }

        @Test
        fun `should throw when exact id does not exist`() {
            // given
            val rulesets = mapOf(
                "base" to createRuleset("base"),
            )

            // when/then
            assertThatThrownBy {
                resolver.resolve(listOf("missing-ruleset"), rulesets)
            }.isInstanceOf(RulesetResolvingException::class.java)
                .hasMessageContaining("missing-ruleset")
        }

        @Test
        fun `should include requestedBy in error message`() {
            // given
            val rulesets = mapOf(
                "base" to createRuleset("base"),
            )

            // when/then
            assertThatThrownBy {
                resolver.resolve(listOf("missing"), rulesets, requestedBy = "agent 'developer-feature'")
            }.isInstanceOf(RulesetResolvingException::class.java)
                .hasMessageContaining("required by agent 'developer-feature'")
        }

        @Test
        fun `should include available rulesets in error message`() {
            // given
            val rulesets = mapOf(
                "base" to createRuleset("base"),
                "coding-java" to createRuleset("coding-java"),
            )

            // when/then
            assertThatThrownBy {
                resolver.resolve(listOf("missing"), rulesets)
            }.isInstanceOf(RulesetResolvingException::class.java)
                .hasMessageContaining("[base, coding-java]")
        }

        @Test
        fun `should detect rulesets excluded by project filter`() {
            // given
            val filteredRulesets = mapOf(
                "base" to createRuleset("base"),
            )
            val allRulesets = mapOf(
                "base" to createRuleset("base"),
                "coding-kotlin" to createRuleset("coding-kotlin"),
            )

            // when/then
            assertThatThrownBy {
                resolver.resolve(
                    listOf("coding-kotlin"),
                    filteredRulesets,
                    requestedBy = "agent 'dev'",
                    allRulesets = allRulesets,
                )
            }.isInstanceOf(RulesetResolvingException::class.java)
                .hasMessageContaining("excluded by project filter")
                .hasMessageContaining("coding-kotlin")
        }

        @Test
        fun `should suggest similar rulesets`() {
            // given
            val rulesets = mapOf(
                "coding-java" to createRuleset("coding-java"),
                "coding-kotlin" to createRuleset("coding-kotlin"),
                "testing-java" to createRuleset("testing-java"),
            )

            // when/then
            assertThatThrownBy {
                resolver.resolve(listOf("coding-python"), rulesets)
            }.isInstanceOf(RulesetResolvingException::class.java)
                .hasMessageContaining("Similar available: [coding-java, coding-kotlin]")
        }
    }

    @Nested
    inner class Suggestions {

        @Test
        fun `should find suggestions by substring match`() {
            // given
            val availableIds = setOf("coding-java", "coding-kotlin", "testing-java", "base")

            // when
            val result = resolver.findSuggestions("coding", availableIds)

            // then
            assertThat(result).containsExactly("coding-java", "coding-kotlin")
        }

        @Test
        fun `should find suggestions by part match`() {
            // given
            val availableIds = setOf("coding-java", "coding-kotlin", "testing-java", "base")

            // when
            val result = resolver.findSuggestions("kotlin", availableIds)

            // then
            assertThat(result).containsExactly("coding-kotlin")
        }

        @Test
        fun `should return empty for pure regex patterns`() {
            // given
            val availableIds = setOf("coding-java", "base")

            // when
            val result = resolver.findSuggestions(".*", availableIds)

            // then
            assertThat(result).isEmpty()
        }

        @Test
        fun `should be case insensitive`() {
            // given
            val availableIds = setOf("Coding-Java", "base")

            // when
            val result = resolver.findSuggestions("coding", availableIds)

            // then
            assertThat(result).containsExactly("Coding-Java")
        }
    }
}
