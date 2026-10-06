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
    val pointerSourceDirs: List<java.io.File>,
    /**
     * The MCP servers this project selects, in the order they are rendered; empty when it selects none, and then an MCP config file is changed only to remove the entries the MCP ledger records for it.
     */
    val mcps: Map<String, McpServer> = emptyMap(),
    /**
     * The id of every MCP server manifest of the run, whether this project selects it or not - see [cz.cleanship.aitools.engine.tools.mcp.McpOwnership.ownedServerEntries] for the entries of an MCP config file the engine owns.
     */
    val ownedMcpIds: Set<String> = emptySet(),
)
