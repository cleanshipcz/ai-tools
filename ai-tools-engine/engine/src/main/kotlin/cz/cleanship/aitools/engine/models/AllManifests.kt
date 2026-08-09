package cz.cleanship.aitools.engine.models

data class AllManifests(
    val agents: Map<String, AgentManifest>,
    val prompts: Map<String, PromptManifest>,
    val rulesets: Map<String, RulesetManifest>,
    val fragments: Map<String, FragmentManifest>,
    val skills: Map<String, SkillManifest>,
    val skillSourceDirs: Map<String, java.io.File> = emptyMap(),
    val projects: Map<String, ProjectManifest>,
    val features: Map<ProjectManifest, Map<String, FeatureManifest>>,
    /**
     * Id collisions that cost only the project(s) they belong to. The affected projects are deliberately absent
     * from [projects] - they cannot be exported - while every other project is still there to be exported.
     */
    val duplicates: List<DuplicateManifestId> = emptyList(),
)
