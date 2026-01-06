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
        // given
        val manifests = listOf(
            createManifest("m1", setOf("tag1", "common")),
            createManifest("m2", setOf("tag2", "common")),
            createManifest("m3", setOf("tag3")),
        )
        val filter = ProjectFilter.ByTags(listOf("tag1", "tag2"))

        // when
        val result = filterService.filter(manifests, filter)

        // then
        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m2")
    }

    @Test
    fun `should filter by whitelisted ids`() {
        // given
        val manifests = listOf(
            createManifest("m1"),
            createManifest("m2"),
            createManifest("m3"),
        )
        val filter = ProjectFilter.ByWhitelistedIds(listOf("m1", "m3"))

        // when
        val result = filterService.filter(manifests, filter)

        // then
        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m3")
    }

    @Test
    fun `should filter by blacklisted ids`() {
        // given
        val manifests = listOf(
            createManifest("m1"),
            createManifest("m2"),
            createManifest("m3"),
        )
        val filter = ProjectFilter.ByBlacklistedIds(listOf("m2"))

        // when
        val result = filterService.filter(manifests, filter)

        // then
        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m3")
    }

    @Test
    fun `should return all when filter is null`() {
        // given
        val manifests = listOf(
            createManifest("m1"),
            createManifest("m2"),
        )

        // when
        val result = filterService.filter(manifests, null)

        // then
        assertThat(result).hasSize(2)
        assertThat(result)
            .extracting("id")
            .containsExactlyInAnyOrder("m1", "m2")
    }

    @Test
    fun `should handle empty manifest list`() {
        // given
        val manifests = emptyList<VersionedManifest>()
        val filter = ProjectFilter.ByTags(listOf("tag1"))

        // when
        val result = filterService.filter(manifests, filter)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `should work with mocked manifests`() {
        // given
        val m1 = mockk<VersionedManifest> {
            every { id } returns "mock1"
            every { metadata } returns ManifestMetadata(version = Version("1.0.0"), tags = setOf("mock-tag"))
        }
        val m2 = mockk<VersionedManifest> {
            every { id } returns "mock2"
            every { metadata } returns ManifestMetadata(version = Version("1.0.0"), tags = emptySet())
        }
        val filter = ProjectFilter.ByTags(listOf("mock-tag"))

        // when
        val result = filterService.filter(listOf(m1, m2), filter)

        // then
        assertThat(result).containsExactly(m1)
    }
}
