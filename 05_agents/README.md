# Agents

Complete AI assistants with bundled personas, prompts, and rulesets. These manifests define specialized AI behaviors for the `ai-tools` engine.

## 🎯 Purpose

Agents are **complete packages** that define specialized AI behaviors for specific tasks. They combine:

- ✅ **Persona**: Defines the agent's identity, role, and overarching goal.
- ✅ **Rulesets**: Reusable sets of coding guidelines and standards (referenced by ID).
- ✅ **Rules**: Custom rules specific to the given agent.
- ✅ **Process/Prompt**: The core instructions and methodology the agent follows.
- ✅ **Constraints**: Strict boundaries and "must-dos" for the agent's behavior.
- ✅ **MCP servers**: The MCP servers the agent uses, named by the id of their manifest in `07_mcp/` (optional).

**Think of agents as:** Pre-configured AI assistants that know exactly how to handle specialized engineering tasks like debugging, architectural planning, or code documentation.

## 📁 File Structure

Agents are defined in YAML files using the `AgentManifest` schema.

```yaml
id: agent-id           # Unique identifier for the agent
description: summary   # Brief description of the agent's purpose
persona: |             # Defines the identity and role
  Role definition...
rulesets:             # Reusable sets of rules (Ruleset IDs)
  - base
  - specialized-ruleset
rules:                 # Custom rules specific for this given agent
  - 'Do X always'
  - 'Follow convention Y'
prompt: |              # The core methodology or process
  Step-by-step instructions...
constraints:           # Strict behavioral boundaries
  - 'Never delete X'
mcps:                  # Optional: ids of the MCP servers the agent uses
  - github
metadata:              # Manifest metadata
  version: 1.0.0
  author: AI Tools Team
  tags: [tag1, tag2]
```

## 🎨 Design Patterns

### Single Responsibility
Each agent should have ONE clear purpose. Instead of creating a "generic developer" agent, we create specialized agents like `bug-fixer`, `code-reviewer`, or `project-planner`.

### Composition via Rulesets & Rules
Rather than listing hundreds of rules in every agent, they are composed from reusable **Rulesets** for consistency, while allowing for **Rules** to define agent-specific behavior.

### Process-Oriented Prompts
The `prompt` field should define a clear methodology or step-by-step process. This gives the agent a "way of working" that leads to more predictable and high-quality results.

### Persona-Driven Behavior
The `persona` defines "who" the agent is. A `code-reviewer` should feel like a senior engineer, while a `bug-fixer` should act like a forensic investigator.

## 🔌 MCP Servers

`mcps` lists the MCP servers an agent uses, by the id of their manifest in `07_mcp/`.
It is optional and empty by default.

- An id that no MCP server manifest declares fails loading, naming the agent manifest, and the whole run stops.
- Every deployment that deploys the agent must select each of its servers in its `mcps` block. A deployment that does not is not exported at all, and the run exits non-zero naming the deployment, the agent, and the server.
- Claude Code gets the ids as `mcpServers` in the frontmatter of the agent file, such as `mcpServers: [github]`. The agent shares the connection of the session to each server.
- GitHub Copilot, Codex, Cursor, Windsurf, and Antigravity attach no server to an agent. The run logs a warning for them, and the agent is still deployed. A Copilot agent file never carries `tools`: without it, the agent already gets every configured server, and a `tools` list would remove its built-in tools.
- When several agents name unknown ids, one failure lists them all.

The servers themselves come from the MCP config file of the deployment. See [Attaching servers to an agent](../07_mcp/README.md#attaching-servers-to-an-agent) for the details.

## 🔍 Validation & Building

The schema of an agent is `AgentManifest.kt` in `ai-tools-engine/engine/src/main/kotlin/cz/cleanship/aitools/engine/models/`. Every run decodes each agent strictly against it: an unknown key or a missing required field fails the run, naming the file.

```bash
# Validate every manifest, agents included, without writing anything
./deploy.sh --dry-run
```

---

*Note: For a list of available agents and their details, check the source files in this directory.*
