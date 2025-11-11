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

### End-to-End Workflow: Using a Prompt with GPT-5

**Scenario**: You want to use the "extract-method" refactoring prompt with GPT-5.

#### Step 1: Find or Create a Prompt

```bash
# Browse available prompts
ls prompts/refactor/
# Output: extract-method.yml, add-null-safety.yml

# Or create your own
cat prompts/refactor/my-prompt.yml
```

#### Step 2: Build Adapters

```bash
npm run build
```

This generates tool-specific configs in `adapters/`:

- `adapters/windsurf/` - For Windsurf IDE
- `adapters/claude-code/` - For Claude Code
- `adapters/cursor/` - For Cursor IDE

#### Step 3: Use the Prompt

**Option A: Copy the Generated Prompt Text**

```bash
# View the built prompt
cat adapters/claude-code/prompts/extract-method.json
```

Copy the `content` field and paste it into:

- ChatGPT (GPT-5)
- Claude.ai
- Any LLM interface

Replace variables like `{{code}}` with your actual code.

**Option B: Use with Your AI Tool**

```bash
# For Windsurf
# Import: adapters/windsurf/rules/extract-method.json

# For Cursor
# Import: adapters/cursor/recipes.json
# Then select "extract-method" recipe

# For Claude Code
# Import: adapters/claude-code/prompts/extract-method.json
```

**Option C: Use Programmatically**

```typescript
import fs from 'fs';

// Read the generated prompt
const prompt = JSON.parse(
  fs.readFileSync('adapters/claude-code/prompts/extract-method.json', 'utf-8')
);

// Fill in variables
const finalPrompt = prompt.content
  .replace('{{code}}', myCode)
  .replace('{{language}}', 'TypeScript');

// Send to your LLM API
const response = await openai.chat.completions.create({
  model: 'gpt-5',
  messages: [{ role: 'user', content: finalPrompt }],
});
```

#### Step 4: Use with Agents (Advanced)

If you want rules and context bundled:

```bash
# Check agent configs
cat adapters/windsurf/rules/code-reviewer.json

# This includes:
# - All rulepacks (base, security, etc.)
# - System prompts
# - Default settings
# - MCP capabilities
```

Import the agent config into your tool, and it will automatically apply all rules and prompts.

### Workflow Diagram

```
1. Create/Edit YAML manifests
   ↓
2. npm run validate (check for errors)
   ↓
3. npm run build (generate adapters)
   ↓
4. Use adapters in your AI tool
   OR
   Copy prompt text to ChatGPT/Claude
   OR
   Call LLM API programmatically
```

### Quick Usage Examples

**Using an existing prompt manually:**

```bash
# 1. View available prompts
ls prompts/*/

# 2. Read the prompt
cat prompts/refactor/extract-method.yml

# 3. Copy the 'content' section
# 4. Paste into ChatGPT/Claude
# 5. Replace {{variables}} with your data
```

**Using with Windsurf IDE:**

```bash
# 1. Build adapters
npm run build

# 2. In Windsurf, go to Settings > Rules
# 3. Import: /path/to/ai-tools/adapters/windsurf/rules/code-reviewer.json
# 4. Chat with AI - rules are automatically applied
```

**Using with Cursor IDE:**

```bash
# 1. Build adapters
npm run build

# 2. In Cursor, open Command Palette (Cmd/Ctrl+Shift+P)
# 3. Search: "Cursor: Import Custom Rules"
# 4. Import: /path/to/ai-tools/adapters/cursor/recipes.json
# 5. Use recipes in chat via @recipe-name
```

**Programmatic usage:**

```python
# Python example
import json
import os
from openai import OpenAI

# Read generated prompt
with open('adapters/claude-code/prompts/write-tests.json') as f:
    prompt_config = json.load(f)

# Fill variables
code = """
def calculate_total(items):
    return sum(item['price'] for item in items)
"""

final_prompt = prompt_config['content'].replace('{{code}}', code)

# Call GPT-5
client = OpenAI(api_key=os.environ['OPENAI_API_KEY'])
response = client.chat.completions.create(
    model='gpt-5',
    messages=[{'role': 'user', 'content': final_prompt}]
)

print(response.choices[0].message.content)
```

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
