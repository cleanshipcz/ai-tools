# Using the Prompt Engineer Agent

The `prompt-engineer` agent is a specialized assistant for creating high-quality prompts and agents for the ai-tools repository.

## What It Does

The prompt-engineer agent:

- ✅ Creates new prompt manifests following the repository format
- ✅ Designs new agent manifests with proper structure
- ✅ Ensures schema compliance (validates against JSON Schema)
- ✅ Follows naming conventions (kebab-case IDs, snake_case variables)
- ✅ Applies best practices for prompt engineering
- ✅ Uses Mustache templating correctly
- ✅ Suggests appropriate rulepacks and capabilities

## When to Use It

Use the prompt-engineer agent when you need to:

- Create a new prompt for a specific refactoring task
- Design a new agent for a specialized role
- Improve existing prompts or agents
- Learn prompt engineering patterns
- Ensure your manifests follow conventions

## Example Sessions

### Creating a New Prompt

**You:** Create a prompt for adding error handling to a function

**Agent Response:** _(Creates a complete prompt manifest)_

````yaml
id: add-error-handling
version: 1.0.0
description: Add comprehensive error handling to a function with try-catch blocks and proper logging
tags:
  - refactor
  - reliability
  - error-handling
variables:
  - name: code
    required: true
    description: 'The function code to add error handling to'
  - name: language
    required: true
    description: 'Programming language of the code'
  - name: error_strategy
    required: false
    description: 'Error handling strategy: throw, return-null, callback'
    default: 'throw'
includes:
  - ../shared/constraints.md
rules:
  - 'Preserve existing behavior for happy path'
  - 'Add meaningful error messages'
  - 'Use appropriate error types for the language'
  - 'Add logging at appropriate levels'
  - 'Include JSDoc/docstring for error cases'
content: |
  Add comprehensive error handling to the following {{language}} code:

  ```{{language}}
  {{code}}
````

{{#error_strategy}}
Error handling strategy: {{error_strategy}}
{{/error_strategy}}

Requirements:

- Identify all potential failure points
- Add try-catch blocks or equivalent
- Create meaningful error messages
- Add appropriate logging
- Document error cases in comments
- Consider edge cases and validation
- Use language-specific error types

{{> constraints}}

outputs:
format: code
metadata:
author: 'AI Tools Team'
created: '2025-11-15'

````

**Save this to:** `prompts/refactor/add-error-handling.yml`

### Creating a New Agent

**You:** Create an agent specialized in documentation - writing clear, comprehensive docs for code

**Agent Response:** *(Creates a complete agent manifest)*

```yaml
id: doc-writer
version: 1.0.0
purpose: Create comprehensive, clear documentation for code, APIs, and projects
description: |
  A specialized agent for writing high-quality technical documentation.
  Analyzes code to generate API docs, user guides, tutorials, and README files.
  Focuses on clarity, completeness, and accessibility for different audiences.
rulepacks:
  - base
capabilities:
  - mcp:filesystem
  - mcp:git
defaults:
  temperature: 0.5
  style: conversational
  model: claude-4-5-sonnet
prompt:
  system: |
    You are a technical documentation specialist focused on creating clear,
    comprehensive, and accessible documentation.

    Your documentation style:
    - Clear and concise language
    - Structured with logical flow
    - Examples that illuminate concepts
    - Multiple audience levels (beginner, intermediate, advanced)
    - Proper code formatting and syntax highlighting
    - Visual aids when helpful (diagrams, tables, etc.)

    Documentation types you create:
    - API documentation (parameters, returns, examples)
    - User guides (step-by-step instructions)
    - README files (overview, setup, usage)
    - Architecture docs (design decisions, structure)
    - Tutorial content (learning-focused)
    - Inline code comments (when needed)

    Best practices:
    - Start with a clear purpose statement
    - Provide working examples
    - Document edge cases and errors
    - Keep it up-to-date with code
    - Use consistent terminology
    - Link related documentation

  user_template: |
    {{#doc_type}}
    Documentation type: {{doc_type}}
    {{/doc_type}}

    {{#code}}
    Code to document:
    ```
    {{code}}
    ```
    {{/code}}

    {{#context}}
    Additional context:
    {{context}}
    {{/context}}

    {{#audience}}
    Target audience: {{audience}}
    {{/audience}}

    Create comprehensive documentation that:
    1. Explains the purpose clearly
    2. Describes all parameters/options
    3. Provides practical examples
    4. Documents edge cases and errors
    5. Links to related documentation
    6. Uses appropriate formatting

tools:
  - read-file
  - search-code
constraints:
  - 'Use clear, accessible language'
  - 'Include working code examples'
  - 'Document all public APIs'
  - 'Explain the "why" not just the "what"'
  - 'Keep documentation in sync with code'
  - 'Use proper Markdown formatting'
metadata:
  author: 'AI Tools Team'
  created: '2025-11-15'
  tags:
    - documentation
    - writing
    - technical-writing
````

**Save this to:** `agents/doc-writer.yml`

## How to Use in Different Tools

### In Windsurf

1. Build the adapters:

   ```bash
   npm run build
   ```

2. Import the agent:
   - Open Windsurf Settings (⚙️)
   - Navigate to Cascade → Rules
   - Import: `adapters/windsurf/rules/prompt-engineer.json`

3. Use in chat:
   ```
   I need a prompt for extracting constants from code.
   Make it support Python, TypeScript, and Java.
   ```

### In Cursor

1. Build the adapters:

   ```bash
   npm run build
   ```

2. Access the agent:
   - Type `@prompt-engineer` in chat

3. Ask for help:
   ```
   @prompt-engineer Create an agent for database migration reviews
   ```

### In Claude Code

1. Build the adapters:

   ```bash
   npm run build
   ```

2. Use the generated agent config:

   ```bash
   cat adapters/claude-code/agents/prompt-engineer.json
   ```

3. Copy the system prompt or full config

## Tips for Working with Prompt Engineer

### Be Specific

❌ "Create a prompt for refactoring"
✅ "Create a prompt for extracting duplicate code into a shared utility function"

### Provide Context

Good request:

```
Create a prompt for adding TypeScript types to untyped code.
Include variables for: code, strict_mode (boolean), and existing_types (optional).
The prompt should handle React components specifically.
```

### Request Complete Manifests

The agent will provide complete YAML manifests ready to save. Review:

- Schema compliance (id, version, description)
- Variable naming (snake_case)
- ID format (kebab-case)
- Mustache syntax (`{{variable}}`)
- Included files from `prompts/shared/`

### Iterate and Refine

First pass:

```
Create a prompt for code optimization
```

Refinement:

```
Improve the optimization prompt to include:
- Memory optimization option
- Speed optimization option
- Code size reduction option
```

## Common Patterns

### Creating Refactoring Prompts

Always include:

- `code` variable (required)
- `language` variable (required)
- Preservation of behavior rules
- Test requirements
- Reference to `constraints.md`

### Creating Review Agents

Always include:

- `reviewer` rulepack
- Low temperature (0.2-0.3)
- Structured output format
- Critical/Important/Minor categorization
- Actionable feedback requirement

### Creating Language-Specific Agents

Always include:

- Appropriate coding-\* rulepack
- Language-specific tools/skills
- Idiomatic patterns for that language
- Common pitfalls section

## Validation

After the agent creates a manifest:

1. Save the file
2. Run validation:
   ```bash
   npm run validate
   ```
3. Build adapters:
   ```bash
   npm run build
   ```
4. Test with your tool

## Advanced Usage

### Chain Multiple Requests

1. "Create a base prompt for function extraction"
2. "Now create language-specific variants for Python, Java, and Kotlin"
3. "Create an agent that uses these prompts for automated refactoring"

### Reference Existing Manifests

```
Create a prompt similar to extract-method.yml but for
extracting classes instead of methods
```

### Ask for Explanations

```
Explain the difference between using 'content' vs 'system'
and 'user' fields in a prompt manifest
```

## Learning Resources

- **Prompt Schema:** `schemas/prompt.schema.json`
- **Agent Schema:** `schemas/agent.schema.json`
- **Existing Prompts:** Browse `prompts/` directory
- **Existing Agents:** Browse `agents/` directory
- **Style Guide:** `docs/STYLE_GUIDE.md`

## Next Steps

1. Try creating a simple prompt
2. Validate and build it
3. Test in your preferred tool
4. Share useful prompts with the team
5. Create specialized agents for your workflow

---

**Need Help?** The prompt-engineer agent is designed to guide you through
the entire process with detailed, schema-compliant outputs.
