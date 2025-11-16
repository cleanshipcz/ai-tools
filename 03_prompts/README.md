# Prompts

Reusable prompt templates for AI models. Define once in YAML, use everywhere.

## 📁 What's Here

This folder contains atomic, reusable prompts organized by category:

- **refactor/** - Code refactoring prompts (extract-method, add-null-safety)
- **qa/** - Quality assurance prompts (write-tests)
- **docs/** - Documentation prompts (summarize-pr)
- **shared/** - Reusable snippets included in prompts

## 🎯 Use Cases

### For Casual Users (ChatGPT, Claude, etc.)

**Quick Copy-Paste:**

```bash
# Generate interactive HTML browser
npm run prompt-html
# Open PROMPT_LIBRARY.html in your browser
# Fill variables, copy filled prompt, paste into ChatGPT
```

**Or use CLI:**

```bash
npm run use-prompt write-tests
# Follow interactive prompts
# Copy the final output
```

### For Tool Users (Windsurf, Cursor, Claude Code)

Prompts are bundled into agents and imported into your AI coding tool. See [Agents](../04_agents/README.md) for how to use them.

## 🔧 How to Use in Different Tools

### Windsurf

1. Build the configs:

   ```bash
   npm run build
   ```

2. Open Windsurf

3. Go to **Settings** (Cmd/Ctrl + ,)

4. Navigate to **Cascade** → **Rules**

5. Click **Add Rule** or **Import Rules**

6. Select a file from `adapters/windsurf/rules/`:
   - For agents (includes prompts): `adapters/windsurf/rules/code-reviewer.json`
   - For specific prompts: They're bundled in agent configs

7. The prompts are now available in Cascade chat

**Note:** Windsurf bundles prompts into agent configurations, not as standalone items.

### Cursor

1. Build the configs:

   ```bash
   npm run build
   ```

2. Open Cursor

3. Open Command Palette (Cmd/Ctrl + Shift + P)

4. Search for: **"Cursor: Import Rules"** or **"Cursor: Settings"**

5. Navigate to rules/recipes section

6. Import: `adapters/cursor/recipes.json`

7. In Cursor chat, use prompts via:
   ```
   @extract-method
   @write-tests
   ```

### Claude Code

1. Build the configs:

   ```bash
   npm run build
   ```

2. Prompts are in: `adapters/claude-code/prompts/`

3. Each prompt is a standalone JSON file:

   ```
   extract-method.json
   write-tests.json
   add-null-safety.json
   summarize-pr.json
   ```

4. Import into Claude Code according to their documentation

5. Or copy the `user` field directly for manual use

### GitHub Copilot (Visual Studio / VS Code)

**Generated:**

- `.github/prompts/*.prompt.md` → Reusable prompts (invoke explicitly)
- `.github/copilot-instructions.md` → Lists available prompts

```bash
npm run build
cp -r adapters/github-copilot/.github ./
```

**Usage in VS Code:** Attach via paperclip → Prompt → select prompt  
**Usage in Visual Studio:** Refer to copilot-instructions.md for available prompts

## 📝 Creating Your Own Prompts

### Basic Prompt

````yaml
# prompts/refactor/my-prompt.yml
id: my-prompt
version: 1.0.0
description: Brief description (10-500 chars)
tags:
  - refactor
variables:
  - name: code
    required: true
    description: 'The code to refactor'
  - name: language
    required: true
    description: 'Programming language'
content: |
  Refactor this {{language}} code:

  ```{{language}}
  {{code}}
````

Make it cleaner and more readable.
outputs:
format: code

````

### Advanced Features

**Include shared snippets:**

```yaml
includes:
  - ../shared/constraints.md
  - ../shared/acceptance_criteria.md
content: |
  Your prompt here...

  {{> constraints}}
  {{> acceptance_criteria}}
````

**Optional variables with Mustache:**

```yaml
content: |
  Required: {{code}}

  {{#context}}
  Optional context provided: {{context}}
  {{/context}}
```

**Rules and guidelines:**

```yaml
rules:
  - 'Preserve exact behavior'
  - 'Add type hints'
  - 'Keep under 50 lines'
```

### Validation

```bash
npm run validate
```

Checks for:

- Valid YAML syntax
- Required fields present
- Unique IDs
- Valid semver
- No secrets in files
- Valid includes

### Building

```bash
npm run build
```

Generates tool-specific configs in `adapters/`:

- `adapters/windsurf/rules/` - Agent configs with prompts
- `adapters/cursor/recipes.json` - Cursor recipes
- `adapters/claude-code/prompts/` - Individual prompt files

### Testing

```bash
# Generate user-friendly versions
npm run prompt-library  # Markdown
npm run prompt-html     # Interactive browser

# Test with CLI
npm run use-prompt my-prompt
```

## 📋 Available Prompts

### Refactoring

- **extract-method** (v1.3.0) - Extract code into a pure function
  - Variables: `code`, `language`, `target_name` (optional)
  - Output: Clean, typed function with documentation

- **add-null-safety** (v1.2.0) - Add null/error handling
  - Variables: `code`, `language`
  - Output: Code with proper null checks

### QA/Testing

- **write-tests** (v1.0.0) - Generate unit tests
  - Variables: `code`, `language`
  - Output: Comprehensive test suite

### Documentation

- **summarize-pr** (v1.0.0) - Summarize pull requests
  - Variables: `diff`, `context` (optional)
  - Output: Markdown PR summary

## 🎨 Best Practices

### Prompt Design

1. **Single purpose** - Each prompt does one thing well
2. **Clear variables** - Document what each variable is for
3. **Good defaults** - Use Mustache for optional content
4. **Explicit rules** - State constraints clearly
5. **Example-friendly** - Easy to provide example values

### Organization

- **Group by category** - refactor/, qa/, docs/, etc.
- **Shared snippets** - DRY principle for common sections
- **Semantic versioning** - MAJOR.MINOR.PATCH
- **Stable IDs** - Never change ID after publishing

### Testing

- **Try with real data** - Use actual code samples
- **Test all variables** - Both required and optional
- **Check output format** - Ensure proper formatting
- **Validate edge cases** - Empty input, special characters

## 🔗 Related

- [Agents](../04_agents/README.md) - Complete AI assistants that use prompts
- [Rulepacks](../01_rulepacks/README.md) - Coding guidelines to combine with prompts
- [Skills](../02_skills/README.md) - Executable commands agents can run

## 📚 Further Reading

- [STYLE_GUIDE.md](../90_docs/STYLE_GUIDE.md) - Writing effective prompts
- [USER_TOOLS.md](../90_docs/USER_TOOLS.md) - User-friendly tools guide
- [Main README](../README.md) - Full project documentation
