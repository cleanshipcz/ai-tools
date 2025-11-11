# AI Tools - Quick Reference

## Common Commands

```bash
# Setup
npm install                 # Install dependencies
./setup.sh                  # Complete setup with validation

# Development
npm run validate            # Validate all manifests
npm run build               # Build adapters
npm run docs                # Generate documentation
npm run ci                  # Run full CI pipeline locally

# Evaluation
npm run eval                # Run all eval suites
npm run eval -- --suite NAME # Run specific suite

# Comparison
npm run diff -- --before file1 --after file2

# Maintenance
npm run lint                # Check formatting
npm run format              # Auto-format files
npm run clean               # Remove generated files
```

## File Naming Conventions

- **IDs**: kebab-case (e.g., `code-reviewer`, `extract-method`)
- **Files**: Match ID with .yml extension (e.g., `code-reviewer.yml`)
- **Versions**: Semantic versioning (e.g., `1.2.3`)

## Creating a Rulepack

```yaml
id: my-rules
version: 1.0.0
description: Brief description
extends:
  - base # Optional: inherit from other packs
rules:
  - 'Rule 1'
  - 'Rule 2'
```

## Creating an Agent

```yaml
id: my-agent
version: 1.0.0
purpose: What this agent does
rulepacks:
  - base
  - my-rules
capabilities:
  - mcp:filesystem
  - mcp:git
defaults:
  temperature: 0.7
  model: claude-3-5-sonnet
prompt:
  system: |
    System instructions
  user_template: |
    User prompt with {{variables}}
```

## Creating a Prompt

```yaml
id: my-prompt
version: 1.0.0
description: What this prompt does
tags:
  - category
variables:
  - name: input
    required: true
    description: 'What this variable is'
includes:
  - ../shared/constraints.md # Optional shared content
rules:
  - 'Specific rule for this prompt'
content: |
  Your prompt template with {{input}}
outputs:
  format: code # code, markdown, json, yaml, text
```

## Creating a Skill

```yaml
id: my-skill
version: 1.0.0
description: What this skill does
command:
  program: 'command'
  args:
    - 'arg1'
    - 'arg2'
  cwd: '.'
timeout_sec: 300
outputs:
  stdout: true
  stderr: true
  exit_code: true
```

## Creating an Eval Suite

```yaml
suite: my-tests
version: 1.0.0
description: What you're testing
targets:
  - type: prompt # or agent, skill
    id: my-prompt
    dataset: datasets/test.jsonl
checks:
  - name: check-name
    type: regex # regex, command, llm-judge, etc.
    pattern: 'pattern'
    required: true
budgets:
  max_tokens: 100000
  max_cost_usd: 2.0
```

## Variable Syntax

In prompts, use Mustache-style templates:

```yaml
content: |
  {{variable}}                  # Required variable
  {{#optional}}                 # Conditional block
    Content when optional is set
  {{/optional}}
  {{> include_file}}            # Include shared content
```

## Directory Structure

```
├── agents/          # Your agent definitions
├── prompts/         # Your prompts
│   └── shared/      # Shared includes
├── rulepacks/       # Your rulepacks
├── skills/          # Your skills (YAML → SKILL.md)
├── mcp/             # MCP configurations
├── evals/           # Evaluation suites
│   ├── datasets/    # Test data (JSONL)
│   └── suites/      # Suite definitions
├── redteam/         # Security tests
├── config/          # Configuration files
└── adapters/        # Generated (don't edit!)
```

## Common Workflows

### Add a New Agent

1. Create `agents/my-agent.yml`
2. Reference existing rulepacks or create new ones
3. Run `npm run validate`
4. Run `npm run build`
5. Check `adapters/windsurf/rules/my-agent.json`

### Modify a Rulepack

1. Edit `rulepacks/my-rules.yml`
2. Bump version if changing behavior
3. Run `npm run validate`
4. Run `npm run build`
5. Affected agents are automatically rebuilt

### Test a Prompt

1. Create `evals/datasets/my-test.jsonl`
2. Create `evals/suites/my-suite.yml`
3. Run `npm run eval -- --suite my-suite`
4. Check results

### Release a New Version

1. Update version in manifests
2. Update `docs/CHANGELOG.md`
3. Commit and push
4. CI creates GitHub release automatically

## Schema Validation

All YAML files are validated against JSON schemas:

- `schemas/prompt.schema.json`
- `schemas/agent.schema.json`
- `schemas/rulepack.schema.json`
- `schemas/skill.schema.json`
- `schemas/eval.schema.json`

Your editor (with YAML extension) will show errors inline!

## Security

Never commit:

- API keys
- Passwords
- Tokens
- PII

Use environment variables:

```yaml
api_key: ${OPENAI_API_KEY}
```

## Getting Help

- Read [README.md](README.md)
- Check [docs/STYLE_GUIDE.md](docs/STYLE_GUIDE.md)
- View examples in each directory
- Check [docs/AGENTS.md](docs/AGENTS.md) (auto-generated)

## Troubleshooting

**Validation fails**: Read error message, fix the issue
**Build fails**: Check for missing rulepacks or broken includes
**Eval fails**: Ensure datasets exist and are valid JSONL
**TypeScript errors**: Run `npm install` first

## Tips

- Keep IDs stable once published
- Version semantically (MAJOR.MINOR.PATCH)
- Write clear descriptions (10-500 chars)
- Use rulepacks to avoid duplication
- Test with evals before committing
- Set realistic budgets
- Document non-obvious decisions
