package cz.cleanship.aitools.engine.models

data class AllManifests(
    val agents: Map<String, AgentManifest>,
    val prompts: Map<String, PromptManifest>,
    val rulesets: Map<String, RulesetManifest>,
    val fragments: Map<String, FragmentManifest>,
    val skills: Map<String, SkillManifest>,
    /**
     * The folder each skill's relative companion files are copied from, by skill id: the source folder of a pointer skill, or the directory of a directory-based skill.
     */
    val skillSourceDirs: Map<String, java.io.File> = emptyMap(),
    /**
     * The manifest file each skill of [skills] was read from, by skill id.
     */
    val skillManifestFiles: Map<String, java.io.File> = emptyMap(),
    /**
     * Every MCP server of the run by id, each with its transport and variables resolved from its `source` when it is a pointer.
     */
    val mcps: Map<String, McpServer> = emptyMap(),
    val projects: Map<String, ProjectManifest>,
    val userDeployments: Map<String, UserDeploymentManifest> = emptyMap(),
    val features: Map<ProjectManifest, Map<String, FeatureManifest>>,
    /**
     * Id collisions that cost only the deployment(s) they belong to. The affected projects and user deployments are deliberately absent from [projects] and [userDeployments] - they cannot be exported - while every other one is still there to be exported.
     */
    val duplicates: List<DuplicateManifestId> = emptyList(),
) {
    /**
     * The source folder of every pointer skill of [skills].
     */
    val pointerSourceDirs: List<java.io.File>
        get() = skills.values.filter { it.source != null }.mapNotNull { skillSourceDirs[it.id] }
}
