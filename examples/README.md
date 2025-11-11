# Examples Directory

This directory contains complete, working examples of how to use the AI Tools repository.

## What's Included

Each subdirectory contains a self-contained example with:
- Source manifests (YAML files)
- Expected generated outputs
- Usage instructions
- Comments explaining key concepts

## Examples

### basic-workflow/
Learn the fundamentals:
- Creating a simple rulepack
- Defining an agent with the rulepack
- Building and using the generated configs

### prompt-with-variables/
Advanced prompt features:
- Using variables
- Including shared snippets
- Conditional content with Mustache templates

### agent-composition/
Composing complex agents:
- Extending multiple rulepacks
- Configuring capabilities and tools
- Setting appropriate defaults

### eval-suite/
Testing and evaluation:
- Creating test datasets
- Writing eval suites with checks
- Setting budgets and baselines

### mcp-integration/
MCP server usage:
- Configuring MCP servers
- Creating presets
- Security considerations

## How to Use These Examples

1. Read through the example directory
2. Study the source YAML files
3. Run the build to see generated output
4. Modify the examples to experiment
5. Copy patterns to your own manifests

## Running Examples

From the repository root:

```bash
# Validate example manifests
npm run validate

# Build adapters (includes examples)
npm run build

# Check generated files for examples
ls adapters/windsurf/rules/
```

## Learning Path

1. Start with `basic-workflow/`
2. Move to `prompt-with-variables/`
3. Explore `agent-composition/`
4. Study `eval-suite/`
5. Review `mcp-integration/`

Each example builds on previous concepts, so following this order gives you a solid foundation.
