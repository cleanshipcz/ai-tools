# Agents

Complete AI assistants with bundled personas, prompts, and rulepacks. These manifests define specialized AI behaviors for the `ai-tools` engine.

## 🎯 Purpose

Agents are **complete packages** that define specialized AI behaviors for specific tasks. They combine:

- ✅ **Persona**: Defines the agent's identity, role, and overarching goal.
- ✅ **Rulepacks**: A collection of coding guidelines and standards.
- ✅ **Process/Prompt**: The core instructions and methodology the agent follows.
- ✅ **Constraints**: Strict boundaries and "must-dos" for the agent's behavior.

**Think of agents as:** Pre-configured AI assistants that know exactly how to handle specialized engineering tasks like debugging, architectural planning, or code documentation.

## 📁 File Structure

Agents are defined in YAML files using the `AgentManifest` schema.

```yaml
id: agent-id           # Unique identifier for the agent
description: summary   # Brief description of the agent's purpose
persona: |             # Defines the identity and role
  Role definition...
rulepacks:             # List of rulepack IDs to include
  - base
  - specialized-rulepack
prompt: |              # The core methodology or process
  Step-by-step instructions...
constraints:           # Strict behavioral rules
  - 'Do X always'
  - 'Never do Y'
metadata:              # Manifest metadata
  version: 1.0.0
  author: AI Tools Team
  tags: [tag1, tag2]
```

## 🎨 Design Patterns

### Single Responsibility
Each agent should have ONE clear purpose. Instead of creating a "generic developer" agent, we create specialized agents like `bug-fixer`, `code-reviewer`, or `project-planner`.

### Composition via Rulepacks
Rather than listing hundreds of rules in every agent, they are composed from reusable **Rulepacks**. This ensures consistency across different agents.

### Process-Oriented Prompts
The `prompt` field should define a clear methodology or step-by-step process. This gives the agent a "way of working" that leads to more predictable and high-quality results.

### Persona-Driven Behavior
The `persona` defines "who" the agent is. A `code-reviewer` should feel like a senior engineer, while a `bug-fixer` should act like a forensic investigator.

## 🔍 Validation & Building

The agents are validated against the `AgentManifest` schema during the build process.

```bash
# Validate agents
npm run validate

# Build specialized tool configs
npm run build
```

---

*Note: For a list of available agents and their details, check the source files in this directory.*
