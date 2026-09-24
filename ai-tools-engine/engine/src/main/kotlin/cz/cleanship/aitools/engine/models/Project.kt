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
     * The MCP servers this project selects, in the order they are rendered; empty when it selects none, and then no MCP config file is read or written.
     */
    val mcps: Map<String, McpServer> = emptyMap(),
    /**
     * The id of every MCP server of the run, whether this project selects it or not: the entries the engine owns in the MCP config files of a project that selects any.
     */
    val ownedMcpIds: Set<String> = emptySet(),
)
