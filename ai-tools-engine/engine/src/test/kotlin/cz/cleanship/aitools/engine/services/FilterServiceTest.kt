package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectFilter
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.models.VersionedManifest
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FilterServiceTest {

    private val filterService = FilterService()

    private fun createManifest(id: String, tags: Set<String> = emptySet()) = object : VersionedManifest {
        override val id: String = id
        override val description: String = "Description for $id"
        override val metadata: ManifestMetadata = ManifestMetadata(
            version = Version("1.0.0"),
            tags = tags,
        )
    }

    @Test
    fun `should filter by tags`() {
        val manifests = listOf(
            createManifest("m1", setOf("tag1", "common")),
            createManifest("m2", setOf("tag2", "common")),
            createManifest("m3", setOf("tag3")),
        )
        val filters = listOf(ProjectFilter.ByTags(listOf("tag1", "tag2")))

        val result = filterService.filter(manifests, filters)

        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m2")
    }

    @Test
    fun `should filter by whitelisted ids`() {
        val manifests = listOf(
            createManifest("m1"),
            createManifest("m2"),
            createManifest("m3"),
        )
        val filters = listOf(ProjectFilter.ByWhitelistedIds(listOf("m1", "m3")))

        val result = filterService.filter(manifests, filters)

        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m3")
    }

    @Test
    fun `should filter by blacklisted ids`() {
        val manifests = listOf(
            createManifest("m1"),
            createManifest("m2"),
            createManifest("m3"),
        )
        val filters = listOf(
            ProjectFilter.ByTags(listOf("any")),
        )

        val result = filterService.filter(
            listOf(
                createManifest("m1", setOf("any")),
                createManifest("m2", setOf("any")),
                createManifest("m3", setOf("any")),
            ),
            listOf(
                ProjectFilter.ByTags(listOf("any")),
                ProjectFilter.ByBlacklistedIds(listOf("m2")),
            ),
        )

        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m3")
    }

    @Test
    fun `should return all when filter list is empty`() {
        val manifests = listOf(
            createManifest("m1"),
            createManifest("m2"),
        )

        val result = filterService.filter(manifests, emptyList())

        assertThat(result).hasSize(2)
        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m2")
    }

    @Test
    fun `should handle empty manifest list`() {
        val manifests = emptyList<VersionedManifest>()
        val filters = listOf(ProjectFilter.ByTags(listOf("tag1")))

        val result = filterService.filter(manifests, filters)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should combine tags then blacklist`() {
        val manifests = listOf(
            createManifest("m1", setOf("kotlin", "coding")),
            createManifest("m2", setOf("kotlin")),
            createManifest("m3", setOf("python")),
        )
        val filters = listOf(
            ProjectFilter.ByTags(listOf("kotlin")),
            ProjectFilter.ByBlacklistedIds(listOf("m2")),
        )

        val result = filterService.filter(manifests, filters)

        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1")
    }

    @Test
    fun `should combine tags then whitelist to add extra items`() {
        val manifests = listOf(
            createManifest("m1", setOf("kotlin")),
            createManifest("m2", setOf("python")),
            createManifest("m3"),
        )
        val filters = listOf(
            ProjectFilter.ByTags(listOf("kotlin")),
            ProjectFilter.ByWhitelistedIds(listOf("m3")),
        )

        val result = filterService.filter(manifests, filters)

        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m3")
    }

    @Test
    fun `should respect order - whitelist then blacklist removes whitelisted item`() {
        val manifests = listOf(
            createManifest("m1"),
            createManifest("m2"),
            createManifest("m3"),
        )
        val filters = listOf(
            ProjectFilter.ByWhitelistedIds(listOf("m1", "m2", "m3")),
            ProjectFilter.ByBlacklistedIds(listOf("m2")),
        )

        val result = filterService.filter(manifests, filters)

        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m3")
    }

    @Test
    fun `should respect order - blacklist then whitelist re-adds blacklisted item`() {
        val manifests = listOf(
            createManifest("m1", setOf("common")),
            createManifest("m2", setOf("common")),
            createManifest("m3", setOf("common")),
        )
        val filters = listOf(
            ProjectFilter.ByTags(listOf("common")),
            ProjectFilter.ByBlacklistedIds(listOf("m2")),
            ProjectFilter.ByWhitelistedIds(listOf("m2")),
        )

        val result = filterService.filter(manifests, filters)

        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m2", "m3")
    }

    @Test
    fun `should work with mocked manifests`() {
        val m1 = mockk<VersionedManifest> {
            every { id } returns "mock1"
            every { metadata } returns ManifestMetadata(version = Version("1.0.0"), tags = setOf("mock-tag"))
        }
        val m2 = mockk<VersionedManifest> {
            every { id } returns "mock2"
            every { metadata } returns ManifestMetadata(version = Version("1.0.0"), tags = emptySet())
        }
        val filters = listOf(ProjectFilter.ByTags(listOf("mock-tag")))

        val result = filterService.filter(listOf(m1, m2), filters)

        assertThat(result).containsExactly(m1)
    }

    @Test
    fun `should combine multiple tag filters additively`() {
        val manifests = listOf(
            createManifest("m1", setOf("kotlin")),
            createManifest("m2", setOf("python")),
            createManifest("m3", setOf("rust")),
        )
        val filters = listOf(
            ProjectFilter.ByTags(listOf("kotlin")),
            ProjectFilter.ByTags(listOf("python")),
        )

        val result = filterService.filter(manifests, filters)

        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m2")
    }
}
