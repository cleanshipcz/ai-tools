package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectManifest(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val context: ProjectContext,
    val deploy: ProjectDeploy,
) : VersionedManifest

@Serializable
data class ProjectContext(
    val rules: List<String> = emptyList(),
    val overview: String? = null,
    val documentation: ProjectDocumentation,
)

@Serializable
data class ProjectDocumentation(
    val readme: String? = null,
    @SerialName("per_topic")
    val perTopic: Map<String, Map<String, String>> = emptyMap(),
    val additional: List<ProjectDocumentationItem> = emptyList(),
)

@Serializable
data class ProjectDocumentationItem(
    val path: String,
    val description: String? = null,
)

@Serializable
data class ProjectDeploy(
    val directory: String,
    val prompts: ProjectPrompts = ProjectPrompts(),
    val agents: ProjectAgents = ProjectAgents(),
    val features: ProjectFeatures = ProjectFeatures(),
    val rulesets: ProjectRulesets = ProjectRulesets(),
)

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
data class ProjectRulesets(
    val filter: ProjectFilter? = null,
)

@Serializable
sealed class ProjectFilter {
    @Serializable
    @SerialName("tags")
    data class ByTags(val tags: List<String>) : ProjectFilter()

    @Serializable
    @SerialName("whitelist")
    data class ByWhitelistedIds(val ids: List<String>) : ProjectFilter()

    @Serializable
    @SerialName("blacklist")
    data class ByBlacklistedIds(val ids: List<String>) : ProjectFilter()
}
