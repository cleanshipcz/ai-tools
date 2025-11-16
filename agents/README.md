# Agents

Complete AI assistants with bundled prompts, rules, and settings. Ready to import into your AI coding tool.

## 📁 What's Here

- **code-reviewer.yml** - Reviews code for quality, style, and security
- **bug-fixer.yml** - Helps debug and fix issues
- **tdd-navigator.yml** - Guides test-driven development
- **kotlin-style-enforcer.yml** - Enforces Kotlin coding standards
- **prompt-engineer.yml** - Designs and creates high-quality prompts and agents
- **organization-specialist.yml** - Expert in organizing files, folders, documents, and project structures

## 🎯 Purpose

Agents are **complete packages** that combine:

- ✅ Rulepacks (coding guidelines)
- ✅ System prompts (agent personality and goals)
- ✅ Capabilities (what tools they can use - MCP servers)
- ✅ Default settings (temperature, model)

**Think of agents as:** Pre-configured AI assistants for specific tasks.

## 🔧 How to Use in Different Tools

### Windsurf

**Step-by-step:**

1. **Build the configs:**

   ```bash
   cd /path/to/ai-tools
   npm run build
   ```

2. **Open Windsurf**

3. **Access Settings:**
   - Click the gear icon (⚙️) or press `Cmd/Ctrl + ,`

4. **Navigate to Cascade:**
   - In settings, find **"Cascade"** section
   - Click **"Rules"**

5. **Import Agent:**
   - Click **"Add Rule"** or **"Import Rules"**
   - Browse to: `adapters/windsurf/rules/`
   - Select an agent file:
     - `code-reviewer.json`
     - `bug-fixer.json`
     - `tdd-navigator.json`
     - `kotlin-style-enforcer.json`
   - Click **"Open"**

6. **Verify:**
   - The agent's rules should appear in your rules list
   - You may see it as a "Rule Set" or "Custom Rule"

7. **Use in Chat:**
   - Open Cascade (Windsurf's AI chat)
   - The agent's rules are automatically applied
   - Start coding - the AI follows your agent's guidelines

**Multiple Agents:**

- You can import multiple agents
- They'll all be active simultaneously
- Rules from all agents are combined

### Cursor

**Step-by-step:**

1. **Build the configs:**

   ```bash
   npm run build
   ```

2. **Open Cursor**

3. **Access Command Palette:**
   - Press `Cmd/Ctrl + Shift + P`

4. **Find Settings:**
   - Type: **"Cursor: Settings"**
   - Or type: **"Preferences: Open Settings"**

5. **Navigate to Rules/Composer:**
   - Look for **"Rules"**, **"Custom Instructions"**, or **"Composer"** section
   - (Location depends on Cursor version)

6. **Import Rules:**
   - Option A: If there's an "Import" button:
     - Click **"Import Rules"**
     - Select: `adapters/cursor/recipes.json`
   - Option B: Manual import:
     - Open `adapters/cursor/recipes.json`
     - Copy the contents
     - Paste into Cursor's rules/instructions field

7. **Use in Chat:**
   - Open Cursor chat (Cmd/Ctrl + L)
   - Type `@` to see available agents/recipes:
     - `@code-reviewer`
     - `@bug-fixer`
     - `@tdd-navigator`
     - `@kotlin-style-enforcer`
   - Select one to activate its rules

**Example:**

```
@code-reviewer
Please review this function:
[paste your code]
```

### Claude Code

**Step-by-step:**

1. **Build the configs:**

   ```bash
   npm run build
   ```

2. **Locate Agent Files:**

   ```bash
   ls adapters/claude-code/agents/
   ```

   You'll see:
   - `code-reviewer.json`
   - `bug-fixer.json`
   - etc.

3. **Open an Agent File:**

   ```bash
   cat adapters/claude-code/agents/code-reviewer.json
   ```

4. **Import into Claude Code:**
   - Method depends on Claude Code's interface
   - Look for "Custom Instructions" or "System Prompt" settings
   - Copy the `system` field from the JSON
   - Paste into Claude Code's configuration

5. **Or Use Directly:**
   - Copy the entire JSON
   - Use Claude Code's import feature (if available)

### GitHub Copilot (Visual Studio / VS Code)

**What's generated:**

- `.github/copilot-instructions.md` → Lists all available agents (passive)
- `.github/prompts/*.prompt.md` → Agent prompts you can invoke (active)

```bash
npm run build
cp -r adapters/github-copilot/.github ./
```

**Note:** Agents listed for reference. Use prompts to invoke specific agent behaviors.

### VSCode (Manual - Not Recommended)

**As workspace settings:**

1. Create `.vscode/settings.json`:

   ```json
   {
     "aiAssistant.customInstructions": "See .github/copilot-instructions.md"
   }
   ```

2. Extract rules from agent:
   ```bash
   node -e "const agent = require('./adapters/windsurf/rules/code-reviewer.json'); console.log(agent.rules.join('\\n'))" > .github/copilot-instructions.md
   ```

## 📝 Creating Your Own Agents

### Basic Agent

```yaml
# agents/my-agent.yml
id: my-agent
version: 1.0.0
purpose: Brief description of what this agent does

rulepacks:
  - base
  - security

capabilities:
  - mcp:git
  - mcp:filesystem

defaults:
  temperature: 0.3
  model: claude-3-5-sonnet

prompt:
  system: |
    You are a specialized coding assistant focused on [specific task].
    Your goal is to help developers [specific goal].

    Always:
    - [Key behavior 1]
    - [Key behavior 2]
    - [Key behavior 3]

  user_template: |
    {{task_description}}
```

### Advanced Features

**Multiple Rulepacks:**

```yaml
rulepacks:
  - base # Universal standards
  - security # Security checks
  - coding-python # Python conventions
  - my-custom-rules # Your team rules
```

**MCP Capabilities:**

```yaml
capabilities:
  - mcp:filesystem # Read/write files
  - mcp:git # Git operations
  - mcp:http # API calls
  # - mcp:shell     # Shell commands (use carefully!)
```

**User Templates:**

```yaml
prompt:
  user_template: |
    Task: {{task}}

    {{#context}}
    Additional context: {{context}}
    {{/context}}

    {{#files}}
    Files to consider:
    {{#files}}
    - {{.}}
    {{/files}}
    {{/files}}
```

## 🔍 Validation & Building

```bash
# Validate
npm run validate

# Build
npm run build

# Check generated files
ls -la adapters/windsurf/rules/
ls -la adapters/cursor/
ls -la adapters/claude-code/agents/
```

## 📋 Available Agents

### code-reviewer (v1.0.0)

**Purpose:** Reviews code for quality, style, security, and best practices

**Includes:**

- Rulepacks: `base`, `reviewer`, `security`
- Capabilities: `mcp:git`, `mcp:filesystem`
- Temperature: 0.2 (deterministic)

**Use for:**

- Pull request reviews
- Code quality checks
- Security audits
- Style enforcement

### bug-fixer (v1.0.0)

**Purpose:** Helps identify and fix bugs

**Includes:**

- Rulepacks: `base`
- Capabilities: `mcp:git`, `mcp:filesystem`
- Temperature: 0.3

**Use for:**

- Debugging issues
- Finding root causes
- Suggesting fixes
- Test case generation

### tdd-navigator (v1.0.0)

**Purpose:** Guides test-driven development workflow

**Includes:**

- Rulepacks: `base`, `reviewer`
- Capabilities: `mcp:git`, `mcp:filesystem`
- Temperature: 0.3

**Use for:**

- Writing tests first
- TDD workflow guidance
- Test coverage improvement
- Refactoring with tests

### kotlin-style-enforcer (v1.0.0)

**Purpose:** Enforces Kotlin coding standards

**Includes:**

- Rulepacks: `base`, `coding-kotlin`, `security`
- Capabilities: `mcp:git`, `mcp:filesystem`
- Temperature: 0.2

**Use for:**

- Kotlin code reviews
- Style consistency
- Null safety checks
- Idiomatic Kotlin patterns

### prompt-engineer (v1.0.0)

**Purpose:** Designs and creates high-quality prompts and agents for the ai-tools repository

**Includes:**

- Rulepacks: `base`
- Capabilities: `mcp:filesystem`, `mcp:git`
- Temperature: 0.6

**Use for:**

- Creating new prompt manifests
- Designing new agent manifests
- Improving existing prompts/agents
- Understanding prompt engineering patterns
- Ensuring schema compliance
- Building reusable prompt components

## 🎨 Design Patterns

### Single Responsibility

Each agent should have ONE clear purpose:

- ✅ `code-reviewer` - Reviews code
- ✅ `bug-fixer` - Fixes bugs
- ❌ `code-reviewer-bug-fixer-refactorer` - Too broad

### Composition

Build agents from rulepacks:

```yaml
# Instead of:
rules:
  - 200+ rules listed here...

# Do:
rulepacks:
  - base
  - security
  - coding-python
```

### Temperature Tuning

- **Low (0.0-0.3):** Deterministic, consistent
  - Code review
  - Security checks
  - Style enforcement

- **Medium (0.4-0.7):** Balanced
  - Bug fixing
  - Refactoring
  - General assistance

- **High (0.8-1.0):** Creative
  - Brainstorming
  - Exploring alternatives
  - Novel solutions

## 🔗 Related

- [Rulepacks](../rulepacks/README.md) - Guidelines agents use
- [Prompts](../prompts/README.md) - Atomic prompts (agents can reference these)
- [MCP](../mcp/README.md) - Tools agents can access
- [Skills](../skills/README.md) - Commands agents can execute

## 💡 Tips

### Choosing the Right Agent

**For Code Review:**

- Use `code-reviewer`
- Import into Windsurf/Cursor
- Open PRs and ask for review

**For Debugging:**

- Use `bug-fixer`
- Paste error messages
- Share relevant code context

**For TDD:**

- Use `tdd-navigator`
- Start with failing tests
- Follow the red-green-refactor cycle

**For Language-Specific:**

- Use language-specific agents (`kotlin-style-enforcer`)
- Or create your own (e.g., `python-style-enforcer`)

### Testing Your Agent

1. Build and import into your tool
2. Test with real scenarios
3. Check if rules are followed
4. Adjust temperature if needed
5. Iterate on rulepacks

### Switching Agents

**Windsurf:**

- Import multiple agents as different rule sets
- Enable/disable as needed

**Cursor:**

- Use `@agent-name` to invoke specific agent
- Switch between agents with `@`

## 📚 Further Reading

- [Main README](../README.md) - Full project documentation
- [STYLE_GUIDE.md](../docs/STYLE_GUIDE.md) - Writing effective agents
