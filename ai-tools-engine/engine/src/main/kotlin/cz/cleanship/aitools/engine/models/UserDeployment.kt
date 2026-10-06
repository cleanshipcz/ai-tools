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
    /**
     * The source folder of every pointer skill of the run, whether this deployment selects that skill or not.
     */
    val pointerSourceDirs: List<java.io.File>,
    /**
     * The MCP servers this deployment selects, in the order they are rendered; empty when it selects none, and then an MCP config file of the home is changed only to remove the entries the MCP ledger records for it.
     */
    val mcps: Map<String, McpServer> = emptyMap(),
    /**
     * The id of every MCP server manifest of the run, whether this deployment selects it or not - see [cz.cleanship.aitools.engine.tools.mcp.McpOwnership.ownedServerEntries] for the entries of an MCP config file the engine owns.
     */
    val ownedMcpIds: Set<String> = emptySet(),
) {
    /**
     * Whether this deployment writes anything besides the instructions file. Rulesets and fragments are not counted:
     * they are rendered into the artifacts below rather than deployed as files of their own.
     */
    fun hasArtifacts(): Boolean = agents.isNotEmpty() || prompts.isNotEmpty() || skills.isNotEmpty() || mcps.isNotEmpty()
}
