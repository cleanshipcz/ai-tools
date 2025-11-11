# AI Tools Repository

> **A unified mono-repository for AI agent configuration**  
> Define once in YAML, deploy everywhere

A comprehensive solution for managing AI agent prompts, rules, skills, and tool configurations. Keep your "source of truth" in small, typed YAML manifests and automatically generate tool-specific artifacts for Windsurf, Claude Code, Cursor, and other AI coding assistants.

[![CI](https://github.com/cleanshipcz/ai-tools/actions/workflows/ci.yml/badge.svg)](https://github.com/cleanshipcz/ai-tools/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 🎯 What is This?

Instead of maintaining separate configurations for each AI tool you use, define your agents, prompts, and rules **once** in version-controlled YAML files, then automatically generate tool-specific configs.

```
Write YAML → Validate → Build → Get configs for Windsurf, Claude, Cursor, etc.
```

## ✨ Features

- 📝 **Single Source of Truth**: Define agents, prompts, and rules once in YAML
- 🔄 **Multi-Tool Support**: Generate configs for Windsurf, Claude Code, Cursor, and more
- 🛡️ **Type-Safe**: JSON Schema validation for all manifests
- 🧩 **Composable**: Rulepacks extend and combine for maximum reuse
- 📦 **Versioned**: Semantic versioning for all components
- 🧪 **Evaluated**: Built-in eval framework with budget tracking
- 🔒 **Secure**: Automatic checks for secrets and security issues
- ⚡ **Automated**: Full CI/CD with GitHub Actions

**What's Included:**

- 21 validated manifests (6 rulepacks, 4 agents, 4 prompts, 5 skills)
- 5 comprehensive JSON schemas
- 5 build and automation scripts
- Complete GitHub Actions workflow
- Evaluation framework with test datasets
- Red team security test cases
- Full documentation and examples

## Quick Start

### Installation

```bash
# Clone the repository
git clone https://github.com/cleanshipcz/ai-tools.git
cd ai-tools

# Install dependencies
npm install

# Validate manifests
npm run validate

# Build tool-specific adapters
npm run build

# Generate documentation
npm run docs
```

### Project Structure

```
ai-tools/
├── agents/              # Tool-agnostic agent definitions
├── prompts/             # Atomic prompts with metadata
│   ├── refactor/
│   ├── docs/
│   ├── qa/
│   └── shared/          # Shared snippets
├── rulepacks/           # Reusable rulesets
├── skills/              # Executable tools and commands
├── mcp/                 # MCP server configurations
│   ├── servers/
│   └── presets/
├── schemas/             # JSON Schemas for validation
├── scripts/             # Build, validation, and eval scripts
├── evals/               # Evaluation datasets and suites
│   ├── datasets/
│   ├── suites/
│   └── reports/
├── redteam/             # Security and safety tests
├── adapters/            # Generated tool configs (gitignored)
│   ├── windsurf/
│   ├── claude-code/
│   └── cursor/
├── config/              # Configuration templates
└── docs/                # Documentation
```

## 🚀 How to Use This Repository

### What Are These Prompts For?

This repository helps you **manage reusable prompts for AI coding assistants**. Think of it as a library of battle-tested instructions you can give to AI tools while coding.

**Example prompts included:**

- `extract-method` - "Take this code block and make it a clean function"
- `add-null-safety` - "Add proper null checks to this code"
- `write-tests` - "Generate unit tests for this function"
- `summarize-pr` - "Explain what changed in this pull request"

Instead of typing these instructions from scratch every time, you define them once in YAML, then use them anywhere.

### Two Ways to Use Prompts

#### 1. **Automated** (Recommended): Import into Your AI Coding Tool

Your AI coding assistant (Windsurf, Cursor, etc.) automatically uses the prompts while you work.

```bash
# Build the configs
npm run build

# This creates tool-specific files in adapters/
ls adapters/windsurf/rules/     # For Windsurf
ls adapters/cursor/recipes.json # For Cursor
ls adapters/claude-code/        # For Claude Code
```

**Then import into your tool:**

- **Windsurf**: Settings → Rules → Import `adapters/windsurf/rules/code-reviewer.json`
- **Cursor**: Command Palette → "Import Rules" → `adapters/cursor/recipes.json`
- **Claude Code**: Import skills/prompts from `adapters/claude-code/`

Now when you chat with your AI assistant, it automatically knows these prompts and can use them.

#### 2. **Manual**: Copy Prompt Text for Any LLM

Use the prompt text with ChatGPT, Claude.ai, or any LLM interface.

```bash
# View a built prompt
cat adapters/claude-code/prompts/extract-method.json
```

**What you'll see:**

```json
{
  "id": "extract-method",
  "user": "Extract the following {{language}} code into a separate function:\n\n```{{language}}\n{{code}}\n```\n\n...",
  "variables": [
    {"name": "code", "required": true},
    {"name": "language", "required": true}
  ]
}
```

**How to use:**

1. Copy the `user` field text (the actual prompt)
2. Replace `{{code}}` with your actual code
3. Replace `{{language}}` with your programming language
4. Paste the final text into ChatGPT, Claude, or any chat interface

**Example:**

```
Extract the following Python code into a separate function:

```python
total = 0
for item in items:
    if item['active']:
        total += item['price']
```
```

That's it! The LLM will refactor your code according to the prompt's instructions.

### Complete Workflow

```
┌─────────────────────────────────────┐
│ 1. Browse/Create Prompts (YAML)    │
│    prompts/refactor/extract-method  │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 2. Build Adapters                   │
│    npm run build                    │
└──────────────┬──────────────────────┘
               │
               ├─────────────────┬──────────────────┐
               ▼                 ▼                  ▼
    ┌──────────────────┐  ┌─────────────┐  ┌──────────────┐
    │ Import to Tool   │  │ Copy & Paste│  │ Use in Code  │
    │ (Windsurf/Cursor)│  │ (ChatGPT)   │  │ (API calls)  │
    └──────────────────┘  └─────────────┘  └──────────────┘
```

### Real-World Usage Examples

**Example 1: Quick Refactoring with ChatGPT**

```bash
# 1. See what's in the prompt
cat adapters/claude-code/prompts/extract-method.json

# 2. Copy the "user" text and fill in variables:
```

_Paste into ChatGPT:_

```
Extract the following JavaScript code into a separate function:

```js
if (user.isPremium && user.credits > 0) {
  user.credits -= cost;
  return true;
}
return false;
```
```

**Example 2: Use in Windsurf While Coding**

```bash
# Build configs
npm run build

# Import into Windsurf: Settings → Rules → Import
# File: adapters/windsurf/rules/code-reviewer.json
```

Now when you select code and chat with AI in Windsurf, it automatically applies code review rules.

**Example 3: Automated Script**

```python
import json

# Load the prompt template
with open('adapters/claude-code/prompts/write-tests.json') as f:
    prompt = json.load(f)

# Fill in your code
my_code = """
def calculate_discount(price, percent):
    return price * (1 - percent / 100)
"""

# Replace variables in the prompt text
final_prompt = prompt['user'].replace('{{code}}', my_code)

# Send to any LLM API (OpenAI, Anthropic, etc.)
# ... your API call here ...
```

### What's in the Repository?

**Ready-to-use prompts:**

- **Refactoring**: `extract-method`, `add-null-safety`
- **QA**: `write-tests`
- **Documentation**: `summarize-pr`

**Agents** (bundles of prompts + rules):

- `code-reviewer` - Reviews code for quality and security
- `bug-fixer` - Helps debug and fix issues
- `tdd-navigator` - Guides test-driven development

**Rulepacks** (reusable rules):

- `base` - Universal coding best practices
- `security` - Security-focused rules
- `coding-python` / `coding-kotlin` - Language-specific rules

### Key Point: YAML vs JSON

- **YAML files** (in `prompts/`, `agents/`, etc.) = Source code (human-readable, version controlled)
- **JSON files** (in `adapters/`) = Built output (machine-readable, tool-specific)

You **edit YAML**, run `npm run build`, then **use JSON** in tools or copy the text for manual use.

**Don't paste YAML into chat** - it's just the source format. The `npm run build` command extracts the actual prompt text into JSON.

## Creating Your First Agent

### 1. Create a Rulepack

```yaml
# rulepacks/my-coding-rules.yml
id: my-coding-rules
version: 1.0.0
description: My team's coding standards
extends:
  - base
rules:
  - 'Use meaningful variable names'
  - 'Keep functions under 50 lines'
  - 'Write tests for all public APIs'
```

### 2. Create an Agent

````yaml
# agents/my-reviewer.yml
id: my-reviewer
version: 1.0.0
purpose: Review code according to team standards
rulepacks:
  - base
  - my-coding-rules
  - security
capabilities:
  - mcp:git
  - mcp:filesystem
defaults:
  temperature: 0.2
  model: claude-3-5-sonnet
prompt:
  system: |
    You are a code reviewer for our team.
    Review code for correctness, style, and security.
  user_template: |
    Review this code:
    ```
    {{code}}
    ```
````

### 3. Build and Use

```bash
# Validate your manifests
npm run validate

# Build adapters
npm run build

# Check generated configs
ls adapters/windsurf/rules/my-reviewer.json
ls adapters/claude-code/prompts/
```

## Available Commands

```bash
npm run validate    # Validate all manifests against schemas
npm run build       # Generate tool-specific adapters
npm run eval        # Run evaluation suites
npm run docs        # Generate documentation
npm run diff        # Compare outputs (A/B testing)
npm run ci          # Run full CI pipeline locally
npm run lint        # Check code formatting
npm run format      # Auto-format code
npm run clean       # Remove generated files
```

## Creating Prompts

Prompts are atomic, reusable templates with variables:

````yaml
# prompts/refactor/simplify-logic.yml
id: simplify-logic
version: 1.0.0
description: Simplify complex conditional logic
tags:
  - refactor
  - readability
variables:
  - name: code
    required: true
    description: 'Code with complex logic'
  - name: language
    required: true
    description: 'Programming language'
includes:
  - ../shared/constraints.md
  - ../shared/acceptance_criteria.md
rules:
  - 'Preserve behavior exactly'
  - 'Reduce nesting depth'
  - 'Extract complex conditions to named functions'
content: |
  Simplify the following {{language}} code:

  ```{{language}}
  {{code}}
````

Focus on:

- Reducing nested if statements
- Using early returns
- Extracting complex conditions

{{> constraints}}
{{> acceptance_criteria}}
outputs:
format: code

````

## Creating Skills

Skills are executable tools that agents can use:

```yaml
# skills/run-linter.yml
id: run-linter
version: 1.0.0
description: Run project linter
command:
  program: "npm"
  args:
    - "run"
    - "lint"
  cwd: "."
timeout_sec: 120
outputs:
  stdout: true
  stderr: true
  exit_code: true
tags:
  - lint
  - quality
````

## Evaluation Framework

Create eval suites to test your agents and prompts:

```yaml
# evals/suites/my-test.yml
suite: my-test
version: 1.0.0
description: Test my custom prompts
targets:
  - type: prompt
    id: simplify-logic
    dataset: datasets/complex_code.jsonl
checks:
  - name: compiles
    type: command
    cmd: 'python -m py_compile output.py'
    required: true
  - name: improved
    type: llm-judge
    judge_prompt: 'Rate the improvement from 1-10'
    weight: 1.0
budgets:
  max_tokens: 100000
  max_cost_usd: 2.0
```

Run evaluations:

```bash
npm run eval                    # Run all suites
npm run eval -- --suite my-test # Run specific suite
```

## Consuming Generated Configs

### Windsurf

1. Build adapters: `npm run build`
2. Point Windsurf to `adapters/windsurf/presets/base.json`
3. Or use specific agent: `adapters/windsurf/rules/code-reviewer.json`

### Claude Code

1. Build adapters: `npm run build`
2. Import `adapters/claude-code/skills.json`
3. Use prompts from `adapters/claude-code/prompts/`

### Cursor

1. Build adapters: `npm run build`
2. Import recipes from `adapters/cursor/recipes.json`

## Configuration

### Provider Setup

```bash
# Copy example config
cp config/providers.example.yml config/providers.yml

# Edit with your API keys
# IMPORTANT: Never commit config/providers.yml!
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

Edit `config/budgets.yml` to control costs:

```yaml
global:
  max_tokens_per_suite: 500000
  max_cost_usd_per_suite: 10.0

suites:
  my-test:
    max_tokens: 100000
    max_cost_usd: 2.0
```

## CI/CD

The repository includes a complete GitHub Actions workflow:

- **Validate**: Schema validation, security checks
- **Build**: Generate all adapters
- **Eval**: Run test suites
- **Docs**: Auto-generate documentation
- **Release**: Create GitHub releases with artifacts

### Setting Up CI

1. Push to GitHub
2. Actions run automatically on PR and push
3. Artifacts are uploaded for each run
4. Releases are created on version changes

### Required Secrets

Add these to your GitHub repository secrets:

- `OPENAI_API_KEY` (optional, for evals)
- `ANTHROPIC_API_KEY` (optional, for evals)

## Best Practices

### Manifest Design

- **Keep IDs stable**: Use kebab-case, don't change once published
- **Version semantically**: MAJOR.MINOR.PATCH
- **Write clear descriptions**: 10-500 characters
- **Document variables**: Always describe what they do
- **Use rulepacks**: Don't duplicate rules across agents

### Security

- **Never commit secrets**: Use `${ENV_VAR}` placeholders
- **Run validation**: Pre-commit hooks catch issues
- **Test safety**: Include red team cases
- **Limit MCP access**: Disable shell by default

### Testing

- **Write evals**: Test prompts with real data
- **Set budgets**: Prevent runaway costs
- **Track baselines**: Know when behavior changes
- **Review diffs**: Compare before/after outputs

## Troubleshooting

### Validation Fails

```bash
npm run validate
# Read error messages - they point to specific issues
```

Common issues:

- Missing required fields
- Invalid semver
- Duplicate IDs
- Broken includes
- Secrets in files

### Build Fails

```bash
npm run build
# Check for missing referenced rulepacks or files
```

### TypeScript Errors

The scripts will work with `tsx` even with TypeScript errors during development. Install dependencies first:

```bash
npm install
```

## Contributing

1. Create a new branch
2. Add/modify manifests in source directories
3. Run `npm run ci` to validate
4. Commit changes
5. Open a pull request
6. CI will validate and build

## Documentation

- [Style Guide](docs/STYLE_GUIDE.md) - Writing effective prompts and rules
- [Changelog](docs/CHANGELOG.md) - Version history
- [Agents](docs/AGENTS.md) - Auto-generated agent documentation

## Examples

See the included examples:

- **Rulepacks**: `rulepacks/*.yml`
- **Agents**: `agents/*.yml`
- **Prompts**: `prompts/*/`
- **Skills**: `skills/*.yml`
- **Eval Suites**: `evals/suites/*.yml`
- **Red Team**: `redteam/*.md`

## Architecture

```
┌─────────────────┐
│ Source Manifests│  (YAML)
│  - Agents       │
│  - Prompts      │
│  - Rulepacks    │
│  - Skills       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Validation      │
│  - Schema check │
│  - Security scan│
│  - ID uniqueness│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Build System    │
│  - Compose rules│
│  - Resolve deps │
│  - Generate     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Tool Adapters   │
│  - Windsurf     │
│  - Claude Code  │
│  - Cursor       │
└─────────────────┘
```

## Versioning

Each manifest has independent versioning:

- **Agents**: Track interface changes
- **Prompts**: Track behavior changes
- **Rulepacks**: Track rule additions/changes
- **Skills**: Track command/API changes

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/cleanshipcz/ai-tools/issues)
- **Discussions**: [GitHub Discussions](https://github.com/cleanshipcz/ai-tools/discussions)

## Roadmap

- [ ] More tool adapters (VSCode, Cody)
- [ ] Web UI for manifest editing
- [ ] Prompt marketplace/sharing
- [ ] Advanced eval metrics
- [ ] Cost analytics dashboard
- [ ] Prompt optimization tools

---

Built with ❤️ for the AI coding community
