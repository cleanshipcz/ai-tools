package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FragmentResolverTest {

    private lateinit var resolver: FragmentResolver

    private val fragmentA = FragmentManifest(
        id = "confluence-guide",
        description = "Confluence formatting guide",
        content = "Use headings for structure.",
        metadata = ManifestMetadata(version = Version("1.0.0")),
    )

    private val fragmentB = FragmentManifest(
        id = "mcp-patterns",
        description = "MCP usage patterns",
        content = "Use MCP tools for integration.",
        metadata = ManifestMetadata(version = Version("1.0.0")),
    )

    private val fragmentC = FragmentManifest(
        id = "confluence-advanced",
        description = "Advanced Confluence features",
        content = "Use macros for dynamic content.",
        metadata = ManifestMetadata(version = Version("1.0.0")),
    )

    private val available = mapOf(
        fragmentA.id to fragmentA,
        fragmentB.id to fragmentB,
        fragmentC.id to fragmentC,
    )

    @BeforeEach
    fun setUp() {
        resolver = FragmentResolver()
    }

    @Test
    fun `should resolve exact fragment ID`() {
        // given
        val patterns = listOf("confluence-guide")

        // when
        val result = resolver.resolve(patterns, available)

        // then
        assertThat(result).containsExactly(fragmentA)
    }

    @Test
    fun `should resolve multiple fragment IDs`() {
        // given
        val patterns = listOf("confluence-guide", "mcp-patterns")

        // when
        val result = resolver.resolve(patterns, available)

        // then
        assertThat(result).containsExactly(fragmentA, fragmentB)
    }

    @Test
    fun `should resolve regex pattern`() {
        // given
        val patterns = listOf("confluence-.*")

        // when
        val result = resolver.resolve(patterns, available)

        // then
        assertThat(result).containsExactlyInAnyOrder(fragmentA, fragmentC)
    }

    @Test
    fun `should deduplicate resolved fragments`() {
        // given
        val patterns = listOf("confluence-guide", "confluence-.*")

        // when
        val result = resolver.resolve(patterns, available)

        // then
        assertThat(result).containsExactlyInAnyOrder(fragmentA, fragmentC)
    }

    @Test
    fun `should throw when pattern matches no fragments`() {
        // given
        val patterns = listOf("nonexistent")

        // when/then
        assertThatThrownBy {
            resolver.resolve(patterns, available, requestedBy = "agent 'test'")
        }.isInstanceOf(FragmentResolvingException::class.java)
            .hasMessageContaining("No fragments match pattern 'nonexistent'")
            .hasMessageContaining("required by agent 'test'")
    }

    @Test
    fun `should return empty list for empty patterns`() {
        // given
        val patterns = emptyList<String>()

        // when
        val result = resolver.resolve(patterns, available)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `should report filtered-out fragments in exception`() {
        // given
        val patterns = listOf("mcp-patterns")
        val filteredAvailable = mapOf(fragmentA.id to fragmentA)

        // when/then
        assertThatThrownBy {
            resolver.resolve(
                patterns = patterns,
                available = filteredAvailable,
                requestedBy = "agent 'test'",
                allFragments = available,
            )
        }.isInstanceOf(FragmentResolvingException::class.java)
            .hasMessageContaining("excluded by project filter")
    }

    @Test
    fun `should suggest similar fragment IDs when pattern does not match`() {
        // given
        val patterns = listOf("confluenc")

        // when/then
        assertThatThrownBy {
            resolver.resolve(patterns, available)
        }.isInstanceOf(FragmentResolvingException::class.java)
            .hasMessageContaining("Similar available:")
    }
}
