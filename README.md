# AI Tools Repository

> **A unified mono-repository for AI agent configuration**  
> Define once in YAML, deploy everywhere

A comprehensive solution for managing AI agent prompts, rules, skills, and tool configurations. Keep your "source of truth" in small, typed YAML manifests and automatically generate tool-specific artifacts for Windsurf, Claude Code, Cursor, and other AI coding assistants.

[![CI](https://github.com/cleanshipcz/ai-tools/actions/workflows/ci.yml/badge.svg)](https://github.com/cleanshipcz/ai-tools/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 📑 Table of Contents

- [AI Tools Repository](#ai-tools-repository)
  - [📑 Table of Contents](#-table-of-contents)
  - [Quick Start](#quick-start)
    - [For Casual Users (Just Want Prompts)](#for-casual-users-just-want-prompts)
    - [For Developers (Managing Configs)](#for-developers-managing-configs)
  - [📝 Prompts](#-prompts)
    - [What Are Prompts?](#what-are-prompts)
    - [Usage](#usage)
      - [For Casual Users (ChatGPT, Claude.ai, etc.)](#for-casual-users-chatgpt-claudeai-etc)
      - [For Tool Users (Windsurf, Cursor, etc.)](#for-tool-users-windsurf-cursor-etc)
    - [Creating Custom Prompts](#creating-custom-prompts)
  - [🤖 Agents](#-agents)
    - [What Are Agents?](#what-are-agents)
    - [Usage](#usage-1)
      - [With AI Coding Tools](#with-ai-coding-tools)
      - [Manually](#manually)
    - [Creating Custom Agents](#creating-custom-agents)
  - [📋 Rulepacks](#-rulepacks)
    - [What Are Rulepacks?](#what-are-rulepacks)
    - [Usage](#usage-2)
    - [Creating Custom Rulepacks](#creating-custom-rulepacks)
  - [🛠️ Skills](#️-skills)
    - [What Are Skills?](#what-are-skills)
    - [Quick Usage](#quick-usage)
  - [🔌 MCP (Model Context Protocol)](#-mcp-model-context-protocol)
    - [What Is MCP?](#what-is-mcp)
    - [Quick Usage](#quick-usage-1)
  - [🧪 Evaluation Framework](#-evaluation-framework)
    - [What Is It?](#what-is-it)
    - [Quick Usage](#quick-usage-2)
  - [🏗️ Project Structure](#️-project-structure)
  - [🚀 Available Commands](#-available-commands)
    - [For Users (Using Prompts)](#for-users-using-prompts)
    - [For Developers (Managing Manifests)](#for-developers-managing-manifests)
  - [📚 Documentation](#-documentation)
    - [User-Friendly Resources](#user-friendly-resources)
    - [Developer Resources](#developer-resources)
  - [⚙️ Configuration](#️-configuration)
    - [Provider Setup (For Evals)](#provider-setup-for-evals)
    - [Budget Configuration](#budget-configuration)
  - [🔒 Security](#-security)
  - [🤝 Contributing](#-contributing)
  - [🔄 CI/CD](#-cicd)
    - [Required GitHub Secrets](#required-github-secrets)
  - [📝 Best Practices](#-best-practices)
    - [Manifest Design](#manifest-design)
    - [Security](#security)
    - [Testing](#testing)
  - [📦 Architecture](#-architecture)
  - [📄 License](#-license)
  - [💬 Support](#-support)
  - [🎯 Quick Tool Integration](#-quick-tool-integration)

---

## Quick Start

### For Casual Users (Just Want Prompts)

```bash
# Clone and install
git clone https://github.com/cleanshipcz/ai-tools.git
cd ai-tools
npm install

# Generate interactive HTML browser
npm run prompt-html
# Open PROMPT_LIBRARY.html in your browser!

# Or use the CLI tool
npm run use-prompt
npm run use-prompt write-tests
```

### For Developers (Managing Configs)

```bash
# Validate and build all configs
npm run validate
npm run build

# Import into tools
ls adapters/windsurf/          # Windsurf rules
ls adapters/cursor/            # Cursor recipes
ls adapters/claude-code/       # Claude Code skills & prompts
ls adapters/github-copilot/    # GitHub Copilot instructions
```

---

## 📝 Prompts

> **📖 [Detailed Guide](prompts/README.md)** - Complete documentation with step-by-step instructions for Windsurf, Cursor, Claude Code, and more.

### What Are Prompts?

Prompts are reusable instructions you give to AI models. Think of them as templates that solve common coding tasks:

- **extract-method** - Refactor code into clean functions
- **add-null-safety** - Add proper null/error handling
- **write-tests** - Generate unit tests for your code
- **summarize-pr** - Explain pull request changes

### Usage

#### For Casual Users (ChatGPT, Claude.ai, etc.)

**Option 1: Interactive HTML Browser (Recommended)**

```bash
# Generate interactive web page
npm run prompt-html

# Open in browser
open PROMPT_LIBRARY.html
```

Features:

- 🔍 **Search and filter** prompts by name/tag
- 📝 **Fill variables** in interactive form
- �️ **Live preview** - see prompt update as you type
- �📋 **One-click copy** - copies the filled prompt
- � **Example data** - auto-fill with sample values
- 🎨 **Beautiful, responsive** UI

**Option 2: Interactive CLI Tool**

```bash
# List available prompts
npm run use-prompt

# Use a specific prompt (it will ask for variables)
npm run use-prompt write-tests
```

The CLI will:

1. Ask for required variables
2. Fill them into the prompt
3. Output ready-to-paste text

**Option 3: Static Markdown Library**

```bash
# Generate markdown file
npm run prompt-library

# Open the file
open PROMPT_LIBRARY.md
```

Browse prompts, click to expand, copy the text, replace `{{variables}}` manually.

**Manual Method:**

```bash
# Build tool adapters
npm run build

# View a prompt
cat adapters/claude-code/prompts/extract-method.json
```

Copy the `user` field, fill in variables, paste into ChatGPT/Claude.

#### For Tool Users (Windsurf, Cursor, etc.)

```bash
# Build tool-specific configs
npm run build

# Import into your tool:
# Windsurf: Settings → Rules → Import adapters/windsurf/rules/*.json
# Cursor: Command Palette → Import adapters/cursor/recipes.json
# Claude Code: Import from adapters/claude-code/
```

Your AI assistant will automatically have access to all prompts.

### Creating Custom Prompts

````yaml
# prompts/refactor/my-prompt.yml
id: my-prompt
version: 1.0.0
description: What this prompt does (10-500 chars)
tags:
  - refactor
  - cleanup
variables:
  - name: code
    required: true
    description: 'The code to process'
  - name: language
    required: true
    description: 'Programming language'
rules:
  - 'Keep behavior identical'
  - 'Add helpful comments'
content: |
  Refactor this {{language}} code:

  ```{{language}}
  {{code}}
  ```

  Make it cleaner and more maintainable.
outputs:
  format: code
````

**Then:**

```bash
npm run validate  # Check for errors
npm run build     # Generate adapters
npm run prompt-library  # Update prompt library
```

---

## 🤖 Agents

> **📖 [Detailed Guide](agents/README.md)** - Complete documentation with step-by-step instructions for Windsurf, Cursor, Claude Code, GitHub Copilot, and more.

### What Are Agents?

Agents are complete AI assistants with bundled prompts, rules, and settings. They're designed for specific tasks and roles.

**Included Agents:**

- **code-reviewer** - Reviews code for quality and security
- **bug-fixer** - Helps debug and fix issues
- **feature-builder** - Implement features from requirements
- **tdd-navigator** - Guides test-driven development
- **kotlin-style-enforcer** - Enforces Kotlin coding standards

### Usage

#### With AI Coding Tools

```bash
# Build agent configs
npm run build

# Import into tool:
# Windsurf: Import adapters/windsurf/rules/code-reviewer.json
# Cursor: Import adapters/cursor/recipes.json
# GitHub Copilot: Copy adapters/github-copilot/.github/ to your repo
```

**GitHub Copilot** - Agents become invokable prompts:

```
/agent-code-reviewer
/agent-feature-builder
/agent-bug-fixer
```

You can also **combine multiple prompts**:

```
/agent-code-reviewer /summarize-pr
```

This loads both the agent persona and task template together.

#### Manually

Agents bundle multiple components, so you'd typically use them through tools. But you can extract individual parts:

```bash
# View agent config
cat agents/code-reviewer.yml

# See what it includes
cat adapters/windsurf/rules/code-reviewer.json
```

### Creating Custom Agents

```yaml
# agents/my-agent.yml
id: my-agent
version: 1.0.0
purpose: Brief description of agent's role
rulepacks:
  - base # Inherit rules from rulepacks
  - security
capabilities:
  - mcp:git # What tools can it use?
  - mcp:filesystem
defaults:
  temperature: 0.3
  model: claude-3-5-sonnet
prompt:
  system: |
    You are a specialized assistant for...
  user_template: |
    {{task_description}}
```

**Then validate and build:**

```bash
npm run validate
npm run build
```

---

## 📋 Rulepacks

> **📖 [Detailed Guide](rulepacks/README.md)** - Complete documentation on how rulepacks work, composition patterns, and usage in different tools.

### What Are Rulepacks?

Rulepacks are reusable collections of coding guidelines and best practices. They're designed to be mixed and matched.

**Included Rulepacks:**

- **base** - Universal coding standards (naming, formatting, etc.)
- **security** - Security best practices (input validation, secrets)
- **coding-python** - Python-specific conventions
- **coding-kotlin** - Kotlin-specific conventions
- **reviewer** - Code review guidelines

### Usage

Rulepacks are typically consumed through **agents** or **tool configs**, not used directly.

```yaml
# In an agent
rulepacks:
  - base
  - security
  - coding-python
```

When you build, these rules get merged into the agent configuration.

### Creating Custom Rulepacks

```yaml
# rulepacks/my-rules.yml
id: my-rules
version: 1.0.0
description: My team's coding standards
extends:
  - base # Inherit from other rulepacks
rules:
  - 'Use meaningful variable names'
  - 'Functions under 50 lines'
  - 'Test all public APIs'
  - 'No console.log in production'
```

**Validation:**

```bash
npm run validate  # Checks for issues
npm run build     # Bundles into agents/configs
```

---

## 🛠️ Skills

> **📖 [Detailed Guide](skills/README.md)** - Complete documentation on the hybrid architecture, how skills work in different tools, and creating custom skills.

### What Are Skills?

Skills are executable commands that AI agents can run - like running tests, linters, or searching the codebase.

**Hybrid Architecture:** YAML manifests → Generated `SKILL.md` for native Anthropic support  
**Included Skills:** run-pytest, run-gradle-tests, run-detekt, run-ktlint, search-repo

### Quick Usage

```bash
# Build all adapters (includes Anthropic SKILL.md generation)
npm run build

# Skills are bundled into agents automatically
# Windsurf/Cursor: Import agent configs
# Claude Code: Use generated adapters/claude-code/skills/
```

**Best of both worlds:** Tool-agnostic YAML source + native Anthropic Agent Skills format.

---

## 🔌 MCP (Model Context Protocol)

> **📖 [Detailed Guide](mcp/README.md)** - Complete documentation on MCP servers, presets, security considerations, and tool-specific configuration.

### What Is MCP?

MCP servers provide AI models with access to external tools and data sources - filesystems, git, databases, APIs.

**Servers:** filesystem, git, http, shell  
**Presets:** base (safe defaults), secure (extra restricted)

### Quick Usage

```yaml
# In agents/my-agent.yml
capabilities:
  - mcp:git
  - mcp:filesystem
  # Or use preset:
  - mcp-preset:base
```

When you build, agents get access to those MCP capabilities.

---

## 🧪 Evaluation Framework

> **📖 [Detailed Guide](evals/README.md)** - Complete documentation on running evals, creating test suites, understanding results, and best practices.

### What Is It?

Test your prompts and agents with real datasets to ensure quality and track regressions.

**Included Eval Suites:** code-refactor, safety-guardrails

### Quick Usage

```bash
# Run all evaluations
npm run eval

# Run specific suite
npm run eval -- --suite code-refactor

# Reports in evals/reports/
```

---

## 📦 Projects & Features

> **📖 [Detailed Guide](projects/README.md)** - Complete documentation on project system, features, and external projects.

### What Are Projects?

Projects allow you to maintain project-specific AI configurations that bundle together relevant prompts, agents, rules, and custom context.

**Key Capabilities:**

- **Project Manifests:** Define tech stack, conventions, documentation links
- **Feature System:** Create feature-specific context and code snippets
- **External Projects:** Link projects in other repositories via `.cleanship-ai-tools` folder
- **Multi-Tool Support:** Generate configs for GitHub Copilot, Windsurf, Cursor, Claude Code

### Project Quick Start

```bash
# Option 1: Create a managed project (in this repo)
npm run project:create my-app

# Option 2: Initialize AI tools in an existing external project
npm run project:init /path/to/my-project --alias my-app

# Add features to your project
# Create features/user-auth/feature.yml in your project directory

# Generate and deploy
npm run project:deploy my-app

# Deploy all projects at once (useful after updating global rules)
npm run project:deploy-all
```

**Example Project Structure:**

```
projects/
├── global/                    # Shared projects (versioned)
│   └── my-app/
│       ├── project.yml       # Project manifest
│       ├── deploy.yml        # Deployment config
│       └── features/         # Feature-specific context
│           └── user-auth/
│               └── feature.yml
├── local/                     # Your personal projects (gitignored)
└── projects.global.yml       # External project registry (versioned)
└── projects.local.yml        # External project registry (gitignored)
```

---

## 🏗️ Project Structure

```
ai-tools/
├── prompts/             # Reusable prompt templates
│   ├── refactor/
│   ├── docs/
│   ├── qa/
│   └── shared/          # Shared snippets
├── agents/              # Complete AI assistants
├── rulepacks/           # Reusable rule collections
├── skills/              # Executable commands
├── mcp/                 # MCP server configs
│   ├── servers/
│   └── presets/
├── schemas/             # JSON Schemas for validation
├── scripts/             # Automation scripts
├── evals/               # Evaluation framework
│   ├── datasets/
│   ├── suites/
│   └── reports/
├── adapters/            # Generated configs (gitignored)
│   ├── windsurf/
│   ├── claude-code/
│   └── cursor/
├── config/              # Configuration templates
├── projects/            # Project-specific configurations
│   ├── global/          # Versioned projects
│   └── local/           # Gitignored user projects
├── templates/           # Project output templates
└── docs/                # Documentation
```

---

## 📁 Projects (NEW!)

> **📖 [Complete Guide: projects/README.md](projects/README.md)**

Generate and deploy project-specific AI tool configurations with your tech stack, conventions, and commands.

### What Are Projects?

Projects let you create customized AI configurations for specific codebases. Each project defines:
- Tech stack (languages, frameworks)
- Conventions (naming, patterns, testing)
- Commands (dev, build, test, deploy)
- Project-specific rules for AI tools

### Quick Start

```bash
# 1. Create a new project
npm run project:create my-project -- -d "Brief description"

# 2. Edit configuration
code projects/local/my-project/project.yml
code projects/local/my-project/deploy.yml

# 3. Deploy (automatically generates outputs)
npm run project:deploy my-project

# 4. List all projects
npm run project:list
```

### Example Project Manifest

```yaml
id: my-api
version: 1.0.0
name: "My REST API"
description: "Node.js REST API with TypeScript"

tech_stack:
  languages: [typescript]
  backend: [node.js, express]
  database: [postgresql]

conventions:
  naming:
    - "Use camelCase for functions"
    - "Use PascalCase for classes"
  patterns:
    - "Use TypeScript strict mode"
    - "API responses: { success, data, error }"

ai_tools:
  preferred_agents: [code-reviewer, feature-builder]
  preferred_rulepacks: [base, coding-typescript, security]
  custom_rules:
    - "Always use our logger utility (src/utils/logger.ts)"
```

### What Gets Generated?

- **GitHub Copilot**: `.github/copilot-instructions.md` with project context
- **Windsurf**: `.windsurf/rules/project-rules.json`
- **Cursor**: `.cursor/project-rules.json`
- **Claude Code**: `.claude/project-context.json`

### Features

✅ **Global & Local Projects** - Share team standards or keep projects private  
✅ **Auto-deployment** - Deploy to multiple projects with one command  
✅ **Version Control** - Track changes to project configurations  
✅ **Tool Agnostic** - Works with all supported AI coding tools  
✅ **Validation** - Schema validation ensures correctness

**[→ Read the full guide](projects/README.md)** for detailed instructions, examples, and best practices.

---

## 🚀 Available Commands

### For Users (Using Prompts)

```bash
npm run prompt-html      # Generate interactive HTML browser (⭐ Recommended)
npm run use-prompt       # Interactive CLI tool to fill & copy prompts
npm run prompt-library   # Generate markdown library
```

### For Projects (Project-Specific Configs)

```bash
npm run project:create           # Create new project from template
npm run project:init             # Initialize AI tools in external project
npm run project:list             # List available projects (including external)
npm run project:generate         # Generate project configurations
npm run project:generate-features # Generate feature-specific snippets
npm run project:deploy           # Deploy to target project
npm run project:deploy-all       # Deploy all projects at once
npm run project:rollback         # Rollback deployment
npm run project:external         # Manage external projects (add/remove/list)
```

### For Developers (Managing Manifests)

```bash
npm run validate         # Validate all manifests
npm run build            # Generate tool adapters
npm run docs             # Generate documentation
npm run eval             # Run evaluation suites
npm run diff             # Compare outputs A/B
npm run ci               # Run full CI pipeline
npm run lint             # Check code formatting
npm run format           # Auto-format code
npm run clean            # Remove generated files
```

---

## 📚 Documentation

### User-Friendly Resources

- **[PROMPT_LIBRARY.html](PROMPT_LIBRARY.html)** - 🌐 Interactive prompt browser (generated)
- **[PROMPT_LIBRARY.md](PROMPT_LIBRARY.md)** - 📄 Markdown prompt library (generated)

### Developer Resources

- **[QUICKREF.md](QUICKREF.md)** - Quick reference guide
- **[docs/TOOLS.md](docs/TOOLS.md)** - Tool integration reference (Windsurf, Cursor, Claude Code, Copilot)
- **[docs/STYLE_GUIDE.md](docs/STYLE_GUIDE.md)** - Writing effective prompts
- **[docs/AGENTS.md](docs/AGENTS.md)** - Agent documentation (generated)
- **[docs/CHANGELOG.md](docs/CHANGELOG.md)** - Version history
- **[IMPLEMENTATION.md](IMPLEMENTATION.md)** - Implementation summary

---

## ⚙️ Configuration

### Provider Setup (For Evals)

```bash
cp config/providers.example.yml config/providers.yml
# Edit with your API keys (never commit this file!)
```

```yaml
# config/providers.yml
providers:
  openai:
    api_key: ${OPENAI_API_KEY}
    default_model: gpt-4-turbo-preview
  anthropic:
    api_key: ${ANTHROPIC_API_KEY}
    default_model: claude-3-5-sonnet-20241022
```

### Budget Configuration

Edit `config/budgets.yml` to control eval costs:

```yaml
global:
  max_tokens_per_suite: 500000
  max_cost_usd_per_suite: 10.0
```

---

## 🔒 Security

- **Never commit secrets** - Use `${ENV_VAR}` placeholders
- **Validation catches secrets** - Pre-commit hooks prevent leaks
- **MCP security** - Disable shell access by default
- **Red team tests** - Security test cases in `redteam/`

---

## 🤝 Contributing

1. Create a branch
2. Add/modify YAML manifests
3. Run `npm run ci` to validate
4. Commit and open PR
5. CI validates automatically

---

## 🔄 CI/CD

GitHub Actions workflow with 7 jobs:

- **validate** - Schema validation, security checks
- **build** - Generate all adapters
- **docs** - Auto-generate documentation
- **eval** - Run test suites
- **lint** - Code formatting
- **security** - Dependency audit
- **release** - Auto-release on version changes

### Required GitHub Secrets

- `OPENAI_API_KEY` (optional, for evals)
- `ANTHROPIC_API_KEY` (optional, for evals)

---

## 📝 Best Practices

### Manifest Design

- **Stable IDs**: Use kebab-case, never change after publishing
- **Semantic versioning**: MAJOR.MINOR.PATCH
- **Clear descriptions**: 10-500 characters
- **Document variables**: Always describe what they do
- **Reuse rulepacks**: Don't duplicate rules

### Security

- Use environment variables for secrets
- Run validation before committing
- Include red team test cases
- Limit MCP access appropriately

### Testing

- Write eval suites for important prompts
- Set budget limits
- Track baseline performance
- Review diff outputs

---

## 📦 Architecture

```
┌─────────────────┐
│ YAML Manifests  │  (Source of Truth)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Validation      │  (Schema + Security)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Build System    │  (Compose + Generate)
└────────┬────────┘
         │
         ├─────────┬─────────┬─────────┐
         ▼         ▼         ▼         ▼
    Windsurf  Cursor  Claude   Library.md
     (JSON)   (JSON)  (JSON)   (Markdown)
```

## 📄 License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

## 💬 Support

- **Issues**: [GitHub Issues](https://github.com/cleanshipcz/ai-tools/issues)
- **Discussions**: [GitHub Discussions](https://github.com/cleanshipcz/ai-tools/discussions)

---

## 🎯 Quick Tool Integration

See **[docs/TOOLS.md](docs/TOOLS.md)** for complete tool-specific instructions.

| Tool               | Format   | Location                   | Import Method                       | Usage                         |
| ------------------ | -------- | -------------------------- | ----------------------------------- | ----------------------------- |
| **Windsurf**       | JSON     | `adapters/windsurf/`       | Rules panel → Import                | Auto-applied                  |
| **Cursor**         | JSON     | `adapters/cursor/`         | Copy to project root                | Auto-applied                  |
| **Claude Code**    | SKILL.md | `adapters/claude-code/`    | Copy to `~/.config/claude/skills/`  | `claude skill <name>`         |
| **GitHub Copilot** | Markdown | `adapters/github-copilot/` | Copy `.github/` folder to your repo | `/agent-name` or `/task-name` |

**TL;DR:** `npm run build` → Import files → Done! ✅

**GitHub Copilot Pro Tip:** Compose multiple prompts: `/agent-code-reviewer /summarize-pr`

---

Built with ❤️ for the AI coding community
