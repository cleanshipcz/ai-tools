# AI Tools Repository

A unified mono-repository for AI agent configuration - define once in YAML, deploy everywhere

## Project Overview

A comprehensive solution for managing AI agent prompts, rules, skills, and tool configurations.
Keep your "source of truth" in small, typed YAML manifests and automatically generate
tool-specific artifacts for Windsurf, Claude Code, Cursor, GitHub Copilot, and other AI coding assistants.

The repository provides a build system that transforms unified YAML manifests into
tool-specific formats, ensuring consistency across different AI coding environments.
It includes prompts for common tasks, agents for specific roles, rulepacks for coding standards,
skills for tool integrations, and a project system for per-project configurations.

**Purpose**: Enable teams to manage AI coding assistant configurations as code, with version control, validation, and automated deployment

## Tech Stack

**Languages**: typescript, yaml, markdown
**Backend**: node.js
**Infrastructure**: github-actions, git

## Key Commands

### Validation
- `npm run validate` - all
- `npm run lint` - lint
- `npm run format` - format

### Build
- `npm run build` - all
- `npm run docs` - docs
- `npm run skills` - skills

### Generation
- `npm run prompt-library` - prompt_library
- `npm run prompt-html` - prompt_html
- `npm run use-prompt` - use_prompt

### Projects
- `npm run project:create <name>` - create
- `npm run project:list` - list
- `npm run project:generate <project-id>` - generate
- `npm run project:deploy <project-id>` - deploy

### Testing
- `npm test` - validate_and_build
- `npm run ci` - full_ci
- `npm run eval` - eval

### Cleanup
- `npm run clean` - clean


## Conventions

### Naming

- Use kebab-case for manifest IDs (e.g., write-tests, code-reviewer)
- Use kebab-case for file names (e.g., extract-method.yml)
- Use camelCase for TypeScript functions and variables
- Use PascalCase for TypeScript classes

### Patterns

- All manifests must be YAML files with .yml extension
- Follow JSON Schema definitions in schemas/ directory
- Use semantic versioning for manifest versions (semver)
- Include comprehensive descriptions (10-500 characters)
- Organize manifests by category in appropriate directories

### Testing

- Run npm run validate before committing
- Ensure all schemas pass validation
- Test generated outputs with npm run build
- Verify tool-specific adapters are correct

### Project Structure

- prompts/ - Organized by category (refactor/, qa/, docs/)
- agents/ - Agent manifests defining roles and behaviors
- rulepacks/ - Coding standards and style rules
- skills/ - Tool integrations and capabilities
- projects/ - Project-specific configurations (global/ and local/)
- schemas/ - JSON Schema definitions for validation
- scripts/ - Build and utility scripts in TypeScript
- adapters/ - Generated tool-specific outputs (gitignored)

## Project-Specific Rules

- Always use chalk for colored terminal output
- Use Ajv with ajv-formats for JSON Schema validation
- Export classes from scripts for reusability (e.g., ProjectGenerator)
- Use import.meta.url guards for CLI entry points
- Follow the existing manifest structure - id, version, description are required
- Generate tool-specific outputs in adapters/ directory (gitignored)
- Keep source manifests in prompts/, agents/, rulepacks/, skills/
- Use TypeScript strict mode with ES modules
- Prefer async/await over callbacks
- Use js-yaml for YAML parsing and dumping

## Documentation

- **readme**: README.md
- **prompts**: prompts/README.md
- **agents**: docs/AGENTS.md
- **skills**: skills/README.md
- **projects**: docs/PROJECTS.md
- **tools**: docs/TOOLS.md
- **user_tools**: docs/USER_TOOLS.md
- **style_guide**: docs/STYLE_GUIDE.md
- **changelog**: docs/CHANGELOG.md
- **planned_features**: docs/PLANNED_FEATURES.md
- **prompt**: schemas/prompt.schema.json
- **agent**: schemas/agent.schema.json
- **rulepack**: schemas/rulepack.schema.json
- **skill**: schemas/skill.schema.json
- **project**: schemas/project.schema.json
- **eval**: schemas/eval.schema.json

---

## Available AI Tools

This project uses ai-tools for enhanced AI assistance. See the base configurations for available agents, prompts, and skills.

**Preferred Agents for this project**:
- code-reviewer
- feature-builder
