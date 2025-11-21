# AI Tools Repository

A unified mono-repository for AI agent configuration - define once in YAML, deploy everywhere

**Default Model for this Project:** claude-sonnet-4.5

*This overrides agent defaults but can be overridden by feature-specific model settings.*

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

- Use Vitest as the test framework for TypeScript code
- Test files mirror source structure: 11_scripts/foo.ts → 11_scripts/foo.test.ts
- Run tests via: npm test (will run vitest when implemented)
- All TypeScript scripts in 11_scripts/ should have corresponding .test.ts files
- Test coverage target: 80% for new code
- Run npm run validate before committing
- Ensure all schemas pass validation
- Test generated outputs with npm run build
- Verify tool-specific adapters are correct

### Project Structure

- 03_prompts/ - Organized by category (refactor/, qa/, docs/)
- 04_agents/ - Agent manifests defining roles and behaviors
- 01_rulepacks/ - Coding standards and style rules
- 02_skills/ - Tool integrations and capabilities
- 05_recipes/ - Multi-agent workflow definitions
- 06_projects/ - Project-specific configurations (global/ and local/)
- 10_schemas/ - JSON Schema definitions for validation
- 11_scripts/ - Build and utility scripts in TypeScript
- 12_templates/ - Template files for generation
- 20_evals/ - Evaluation suites and test cases
- 90_docs/ - Documentation files
- adapters/ - Generated tool-specific outputs (gitignored)

## Project-Specific Rules

- Always use chalk for colored terminal output
- Use Ajv with ajv-formats for JSON Schema validation
- Export classes from scripts for reusability (e.g., ProjectGenerator)
- Use import.meta.url guards for CLI entry points
- Follow the existing manifest structure - id, version, description are required
- Generate tool-specific outputs in adapters/ directory (gitignored)
- Keep source manifests in 03_prompts/, 04_agents/, 01_rulepacks/, 02_skills/, 05_recipes/
- Use TypeScript strict mode with ES modules
- Prefer async/await over callbacks
- Use js-yaml for YAML parsing and dumping
- All new features must include automated tests before being considered complete
- Test infrastructure setup (Vitest) is a prerequisite for any feature work
- No feature PRs without automated tests

## Documentation

- **readme**: README.md
- **prompts**: 03_prompts/README.md
- **agents**: AGENTS.md
- **skills**: 02_skills/README.md
- **projects**: 06_projects/README.md
- **recipes**: 05_recipes/README.md
- **tools**: 90_docs/TOOLS.md
- **user_tools**: 90_docs/USER_TOOLS.md
- **testing**: 90_docs/TESTING.md
- **style_guide**: 90_docs/STYLE_GUIDE.md
- **changelog**: 90_docs/CHANGELOG.md
- **planned_features**: 90_docs/PLANNED_FEATURES.md
- **implementation**: IMPLEMENTATION.md
- **prompt**: 10_schemas/prompt.schema.json
- **agent**: 10_schemas/agent.schema.json
- **rulepack**: 10_schemas/rulepack.schema.json
- **skill**: 10_schemas/skill.schema.json
- **project**: 10_schemas/project.schema.json
- **feature**: 10_schemas/feature.schema.json
- **recipe**: 10_schemas/recipe.schema.json
- **eval**: 10_schemas/eval.schema.json

---

## Available AI Tools

This project uses ai-tools for enhanced AI assistance. See the base configurations for available agents, prompts, and skills.
