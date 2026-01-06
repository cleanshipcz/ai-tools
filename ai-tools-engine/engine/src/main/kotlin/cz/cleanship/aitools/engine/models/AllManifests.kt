package cz.cleanship.aitools.engine.models

data class AllManifests(
    val agents: Map<String, AgentManifest>,
    val prompts: Map<String, PromptManifest>,
    val rulesets: Map<String, RulesetManifest>,
    val projects: Map<String, ProjectManifest>,
    val features: Map<ProjectManifest, Map<String, FeatureManifest>>,
)
