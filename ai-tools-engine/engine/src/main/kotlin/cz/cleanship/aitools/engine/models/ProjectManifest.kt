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

/**
 * @param directory where the generated artifacts of this project land. A relative value resolves against the
 * `--working-dir` of the run - the directory holding `config.yml`, the same base its `locations.*` paths use - so
 * `.` means that directory itself and the value does not shift with the working directory of the JVM process.
 * An absolute value is used exactly as written, which is what a project outside the manifest repository wants.
 * @param replace whether a deploy may delete the directories it generates before writing them again
 */
@Serializable
data class ProjectDeploy(
    val directory: String,
    val replace: Boolean = false,
    val prompts: ProjectPrompts = ProjectPrompts(),
    val agents: ProjectAgents = ProjectAgents(),
    val features: ProjectFeatures = ProjectFeatures(),
    val rulesets: ProjectRulesets = ProjectRulesets(),
    val fragments: ProjectFragments = ProjectFragments(),
    val skills: ProjectSkills = ProjectSkills(),
)

@Serializable
data class ProjectPrompts(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectAgents(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectFeatures(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectRulesets(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectFragments(
    val filter: List<ProjectFilter> = emptyList(),
)

@Serializable
data class ProjectSkills(
    val filter: List<ProjectFilter> = emptyList(),
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
