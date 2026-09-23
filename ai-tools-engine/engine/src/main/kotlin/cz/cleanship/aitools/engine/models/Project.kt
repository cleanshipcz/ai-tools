package cz.cleanship.aitools.engine.models

data class Project(
    val manifest: ProjectManifest,
    val features: Map<String, FeatureManifest>,
    val agents: Map<String, AgentManifest>,
    val prompts: Map<String, PromptManifest>,
    val rulesets: Map<String, RulesetManifest>,
    val fragments: Map<String, FragmentManifest>,
    val skills: Map<String, SkillManifest>,
    val skillSourceDirs: Map<String, java.io.File> = emptyMap(),
    /**
     * The source folder of every pointer skill of the run, whether this project selects that skill or not.
     */
    val pointerSourceDirs: List<java.io.File> = emptyList(),
)
