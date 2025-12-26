package cz.cleanship.aitools.engine.models

data class Project(
    val manifest: ProjectManifest,
    val features: Map<String, FeatureManifest>,
    val agents: Map<String, AgentManifest>,
    val prompts: Map<String, PromptManifest>,
    val rulesets: Map<String, RulesetManifest>,
)
