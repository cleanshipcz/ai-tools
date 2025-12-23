package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectManifest(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val directory: String,
    val prompts: ProjectPrompts = ProjectPrompts(),
    val agents: ProjectAgents = ProjectAgents(),
    val features: ProjectFeatures = ProjectFeatures(),
) : VersionedManifest

@Serializable
data class ProjectPrompts(
    val filter: ProjectFilter? = null,
)

@Serializable
data class ProjectAgents(
    val filter: ProjectFilter? = null,
)

@Serializable
data class ProjectFeatures(
    val filter: ProjectFilter? = null,
)

@Serializable
sealed class ProjectFilter {
    @SerialName("tags")
    data class ByTags(val tags: List<String>) : ProjectFilter()

    @SerialName("whitelist")
    data class ByWhitelistedIds(val ids: List<String>) : ProjectFilter()

    @SerialName("blacklist")
    data class ByBlacklistedIds(val ids: List<String>) : ProjectFilter()
}
