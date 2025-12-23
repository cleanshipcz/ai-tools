package cz.cleanship.aitools.engine.models

data class AllManifests(
    val agents: Map<String, AgentManifest>,
    val features: Map<String, FeatureManifest>,
    val prompts: Map<String, PromptManifest>,
    val rulesets: Map<String, RulesetManifest>,
)
