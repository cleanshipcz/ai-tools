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
 * @param directory where the generated artifacts of this project land. Any `${NAME}` reference it carries is expanded
 * first, from the `env_vars` of the config files of the run or from its environment - see
 * [cz.cleanship.aitools.engine.env.VariableResolver] - which is how a base that differs between machines stays out of
 * a manifest that is shared. A relative value resolves against the `--working-dir` of the run - the directory holding
 * `config.yml`, the same base its `locations.*` paths use - so `.` means that directory itself and the value does not
 * shift with the working directory of the JVM process.
 * An absolute value is used exactly as written, which is what a project outside the manifest repository wants.
 * @param replace whether a deploy may delete the directories it generates before writing them again
 * @param tools which of the tools configured for the run deploy this project. Omitting it - not emptying it - means
 * every configured tool, so a project that says nothing keeps following the tool list of the run, while an empty
 * list deliberately restricts the project to no tool at all. A `tools:` key with no value under it decodes to null
 * and therefore means all of them, like omitting the key. A tool the run does not configure is narrowed away rather
 * than rejected: the same manifest is deployed by runs configuring different tools, so naming one that this run
 * does not build is a difference in scope rather than an authoring error.
 */
@Serializable
data class ProjectDeploy(
    val directory: String,
    val replace: Boolean = false,
    val tools: List<ToolType>? = null,
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
