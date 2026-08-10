package cz.cleanship.aitools.engine.models

/**
 * A [UserDeploymentManifest] together with the manifests its filters selected - the user-scope counterpart of
 * [Project]. It carries no features, which belong to a project, and no destination, which each adapter derives
 * from the home base of the run.
 */
data class UserDeployment(
    val manifest: UserDeploymentManifest,
    val agents: Map<String, AgentManifest>,
    val prompts: Map<String, PromptManifest>,
    val rulesets: Map<String, RulesetManifest>,
    val fragments: Map<String, FragmentManifest>,
    val skills: Map<String, SkillManifest>,
    val skillSourceDirs: Map<String, java.io.File> = emptyMap(),
)
