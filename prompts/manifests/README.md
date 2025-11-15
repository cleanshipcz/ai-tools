# Manifest Creation Prompts

Prompts for generating YAML manifests for the ai-tools repository.

## Overview

These prompts help you create well-structured, validated YAML manifests for various components of the ai-tools system. Each prompt follows the repository's conventions and JSON Schema requirements.

## Available Prompts

### create-project

**Purpose:** Generate a complete project.yml manifest

**Use When:** Setting up a new project configuration with tech stack, commands, conventions, and AI tool preferences.

**Example Usage:**

```bash
npm run use-prompt manifests/create-project
```

**Variables:**

- `project_id` (required): Kebab-case project identifier
- `project_name` (required): Human-readable project name
- `description` (required): Brief project description (10-500 chars)
- `overview`: Detailed project background
- `purpose`: Primary goal of the project
- `languages`: Programming languages used
- `tech_stack`: Technology stack details
- `preferred_agents`: Preferred agent IDs

---

### create-feature

**Purpose:** Generate a feature.yml manifest with context and snippets

**Use When:** Documenting a feature with its architecture, files, code snippets, and recipe bindings.

**Example Usage:**

```bash
npm run use-prompt manifests/create-feature
```

**Variables:**

- `feature_id` (required): Kebab-case feature identifier
- `feature_name` (required): Human-readable feature name
- `description` (required): Brief feature description (10-500 chars)
- `overview`: Detailed feature overview
- `architecture`: Architecture and design notes
- `entry_points`: Main entry point files
- `key_files`: Important related files
- `recipe_id`: Recipe to bind (e.g., feature-delivery)
- `acceptance_criteria`: Acceptance criteria

---

### create-agent

**Purpose:** Generate an agent.yml manifest defining an AI agent

**Use When:** Creating a new AI agent with specific expertise, rulepacks, and behavioral constraints.

**Example Usage:**

```bash
npm run use-prompt manifests/create-agent
```

**Variables:**

- `agent_id` (required): Kebab-case agent identifier
- `purpose` (required): Agent's purpose (10-500 chars)
- `persona`: Detailed persona and expertise
- `rulepacks`: Rulepack IDs to include
- `capabilities`: Required MCP capabilities
- `temperature`: Temperature setting (0.0-2.0)
- `tools`: Tool/skill IDs the agent can use

---

### create-prompt

**Purpose:** Generate a prompt.yml manifest with variables and templates

**Use When:** Creating atomic, reusable prompts with Mustache templating support.

**Example Usage:**

```bash
npm run use-prompt manifests/create-prompt
```

**Variables:**

- `prompt_id` (required): Kebab-case prompt identifier
- `description` (required): What the prompt does (10-500 chars)
- `category`: Prompt category (refactor, qa, docs, planning, tickets, manifests)
- `tags`: Tags for categorization
- `variables`: Variables the prompt needs
- `output_format`: Expected output format (markdown, json, yaml, code, text)

---

### create-rulepack

**Purpose:** Generate a rulepack.yml manifest defining coding rules and standards

**Use When:** Creating reusable sets of coding rules, best practices, or standards for specific languages or domains.

**Example Usage:**

```bash
npm run use-prompt manifests/create-rulepack
```

**Variables:**

- `rulepack_id` (required): Kebab-case rulepack identifier
- `description` (required): Rulepack purpose (10-500 chars)
- `rules` (required): Description of rules to include
- `extends`: Parent rulepack IDs to extend
- `category`: Category (coding, security, testing, documentation)
- `language`: Programming language if applicable

---

### create-skill

**Purpose:** Generate a skill.yml manifest for executable commands or MCP tools

**Use When:** Defining tool integrations, command-line executables, or MCP tool references.

**Example Usage:**

```bash
npm run use-prompt manifests/create-skill
```

**Variables:**

- `skill_id` (required): Kebab-case skill identifier
- `description` (required): What the skill does (10-500 chars)
- `command_program`: Executable program name
- `command_args`: Command-line arguments
- `mcp_tool`: MCP tool ID reference
- `timeout`: Timeout in seconds
- `working_dir`: Working directory

---

## Workflow

### Creating a New Project

1. Run the create-project prompt:

   ```bash
   npm run use-prompt manifests/create-project
   ```

2. Fill in the required variables

3. Save the output to `projects/local/<project-id>/project.yml`

4. Validate:
   ```bash
   npm run validate
   npm run project:list
   npm run project:generate <project-id>
   ```

### Creating a Feature

1. Ensure project exists first

2. Run the create-feature prompt:

   ```bash
   npm run use-prompt manifests/create-feature
   ```

3. Fill in feature details

4. Save to `projects/*/features/<feature-id>/feature.yml`

5. Validate and generate:
   ```bash
   npm run validate
   npm run project:generate <project-id>
   ```

### Creating an Agent

1. Run the create-agent prompt:

   ```bash
   npm run use-prompt manifests/create-agent
   ```

2. Define agent purpose and persona

3. Select appropriate rulepacks

4. Save to `agents/<agent-id>.yml`

5. Build and validate:
   ```bash
   npm run validate
   npm run build
   ```

### Creating a Prompt

1. Run the create-prompt prompt:

   ```bash
   npm run use-prompt manifests/create-prompt
   ```

2. Define prompt purpose and variables

3. Write the prompt template with Mustache syntax

4. Save to `prompts/<category>/<prompt-id>.yml`

5. Validate and rebuild:

   ```bash
   npm run validate
   npm run build
   npm run prompt-library
   ```

### Creating a Rulepack

1. Run the create-rulepack prompt:

   ```bash
   npm run use-prompt manifests/create-rulepack
   ```

2. Define the rules and standards

3. Optionally extend existing rulepacks

4. Save to `rulepacks/<rulepack-id>.yml`

5. Validate and build:

   ```bash
   npm run validate
   npm run build
   ```

### Creating a Skill

1. Run the create-skill prompt:

   ```bash
   npm run use-prompt manifests/create-skill
   ```

2. Define command or MCP tool reference

3. Specify constraints and outputs

4. Save to `skills/<skill-id>.yml`

5. Validate and build:

   ```bash
   npm run validate
   npm run build
   npm run skills
   ```

## Naming Conventions

- **IDs:** kebab-case (e.g., `user-authentication`, `create-project`)
- **Variables:** snake_case (e.g., `project_id`, `feature_name`)
- **Versions:** Semantic versioning (e.g., `1.0.0`)

## Validation

All manifests must pass JSON Schema validation:

```bash
npm run validate
```

Common validation issues:

- ID doesn't match kebab-case pattern `^[a-z0-9]+(-[a-z0-9]+)*$`
- Version doesn't match semver pattern `^\d+\.\d+\.\d+$`
- Description not between 10-500 characters
- Missing required fields
- Variable names not in snake_case

## Related Agent

Use the **manifest-builder** agent for interactive manifest creation:

```bash
# In your AI tool with agent support
As the manifest-builder agent, help me create a new project manifest...
```

The manifest-builder agent provides:

- Interactive guidance through manifest creation
- Real-time validation
- Best practice recommendations
- Schema compliance checking
- Helpful examples and suggestions

## Best Practices

1. **Keep Prompts Atomic:** One clear purpose per prompt
2. **Use Mustache Syntax:** `{{variable}}` for interpolation, `{{#var}}...{{/var}}` for conditionals
3. **Include Examples:** Show practical usage in prompt definitions
4. **Add Metadata:** Author, created date, and tags for all manifests
5. **Reference Shared Files:** Use includes for common components
6. **Validate Early:** Run validation before committing
7. **Document Thoroughly:** Clear descriptions and comprehensive context
8. **Follow Conventions:** Use the right directory structure and naming

## Schema References

- **Project Schema:** `schemas/project.schema.json`
- **Feature Schema:** `schemas/feature.schema.json`
- **Agent Schema:** `schemas/agent.schema.json`
- **Prompt Schema:** `schemas/prompt.schema.json`

## See Also

- [Main README](../../README.md) - Repository overview
- [Prompts Guide](../README.md) - General prompt documentation
- [Agents Guide](../../docs/AGENTS.md) - Agent system documentation
- [Projects Guide](../../projects/README.md) - Project system documentation
