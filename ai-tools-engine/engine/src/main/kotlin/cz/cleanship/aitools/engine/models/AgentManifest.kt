package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

/**
 * An agent: a persona with rules, fragments, a prompt and constraints, rendered as an agent file of every tool.
 */
@Serializable
data class AgentManifest(
    override val id: String,
    override val description: String,
    val rulesets: List<String> = emptyList(),
    val fragments: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    val persona: String,
    val prompt: String,
    val constraints: List<String> = emptyList(),
    /**
     * The ids of the MCP servers this agent uses, in the order they are rendered.
     *
     * YAML key `mcps`, a list of MCP server ids such as `[github, atlassian]`; optional, and an empty list by default. Each id must be the id of an MCP server manifest of the run, or loading fails naming this manifest. Every deployment that deploys this agent must select each of them under its `mcps` block, or that deployment is not exported - see [ProjectMcps]. Claude Code lists them under `mcpServers` of the agent file. GitHub Copilot, Codex, Cursor, Windsurf and Antigravity attach no MCP server to an agent, and the engine reports the agent as skipped for them; a Copilot agent file never carries `tools`, so it keeps every built-in tool and every configured server.
     */
    val mcps: List<String> = emptyList(),
    override val metadata: ManifestMetadata,
) : VersionedManifest
