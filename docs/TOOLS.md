# Tool Integration Reference

Quick reference for importing AI Tools configs into different coding assistants.

## Contents

- [Tool Integration Reference](#tool-integration-reference)
  - [Contents](#contents)
  - [Format Overview](#format-overview)
  - [Windsurf](#windsurf)
  - [Cursor](#cursor)
  - [Claude Code (CLI)](#claude-code-cli)
  - [GitHub Copilot](#github-copilot)
    - [1. Global Instructions (Auto-Applied)](#1-global-instructions-auto-applied)
    - [2. Path-Specific Instructions (Auto-Applied by Pattern)](#2-path-specific-instructions-auto-applied-by-pattern)
    - [3. Prompt Files (Manual Invoke)](#3-prompt-files-manual-invoke)
      - [Task Prompts](#task-prompts)
      - [Agent Prompts](#agent-prompts)
    - [Task-Based Usage (Invoke Agent Personas)](#task-based-usage-invoke-agent-personas)
      - [How It Works](#how-it-works)
      - [Prompt Composition](#prompt-composition)
      - [What `@workspace` Does](#what-workspace-does)
      - [Scope of Path-Specific Instructions](#scope-of-path-specific-instructions)
      - [Combining Approaches](#combining-approaches)
  - [Anthropic Skills (Native)](#anthropic-skills-native)
  - [Comparison](#comparison)
  - [Use Cases](#use-cases)
  - [Further Reading](#further-reading)

## Format Overview

```bash
npm run build  # Generates all adapters
```

| Tool             | Format                  | Location                       |
| ---------------- | ----------------------- | ------------------------------ |
| Windsurf         | `.windsurfrules` (JSON) | `adapters/windsurf/`           |
| Cursor           | `.cursorrules` (JSON)   | `adapters/cursor/`             |
| Claude Code      | `SKILL.md` (Markdown)   | `adapters/claude-code/skills/` |
| GitHub Copilot   | `.md` (Markdown)        | `adapters/github-copilot/`     |
| Anthropic Skills | `SKILL.md` (Markdown)   | `adapters/anthropic/`          |

---

## Windsurf

**Format:** JSON per-agent  
**Import:** Rules panel → Import file

```bash
adapters/windsurf/code-reviewer.windsurfrules
```

**Differences:**

- Per-agent JSON files
- Rulepacks auto-merged into flat rule list

---

## Cursor

**Format:** JSON per-agent  
**Import:** Copy to project root or `~/.cursor/`

```bash
adapters/cursor/code-reviewer.cursorrules
```

**Differences:**

- Similar to Windsurf, different metadata
- Project root = project-specific, `~/.cursor/` = global

---

## Claude Code (CLI)

**Format:** SKILL.md per-skill (Anthropic official format)  
**Import:** `~/.config/claude/skills/[skill-name]/SKILL.md`

```bash
adapters/claude-code/skills/run-pytest/SKILL.md
```

**Differences:**

- Skills only (no agents/prompts)
- Executable CLI commands with typed parameters
- Invoked via: `claude skill run-pytest`

---

## GitHub Copilot

**3 File Types - Each for Different Purpose:**

### 1. Global Instructions (Auto-Applied)

**File:** `.github/copilot-instructions.md`  
**Purpose:** Project basics - what, how to build, available components  
**Applied:** Always, to all files

### 2. Path-Specific Instructions (Auto-Applied by Pattern)

**Files:** `.github/instructions/*.instructions.md`  
**Purpose:** Rules for specific files/folders  
**Applied:** Only when editing matched files

**Example:**

```markdown
## <!-- .github/instructions/kotlin-backend.instructions.md -->

## applyTo: "backend/\*_/_.kt"

Use Spring Boot conventions, inject dependencies via constructor
```

When you edit `backend/api/UserService.kt` → these rules apply  
When you edit `frontend/App.tsx` → these rules DON'T apply

### 3. Prompt Files (Manual Invoke)

**Files:** `.github/prompts/*.prompt.md`  
**Purpose:** Reusable tasks AND agent personas you explicitly invoke  
**Applied:** Only when you attach/reference them

**Two types of prompts:**

#### Task Prompts

Reusable task templates:

```markdown
<!-- .github/prompts/write-tests.prompt.md -->

Generate unit tests with mocking for this code
```

#### Agent Prompts

Full agent personas with system prompts:

```markdown
<!-- .github/prompts/agent-code-reviewer.prompt.md -->

# Agent: code-reviewer

**Purpose:** Structured code review with actionable findings

## Persona

You are a senior software engineer conducting a thorough code review...
[Full system prompt with focus areas, constraints, etc.]
```

**Usage in VS Code:**

- Task: Type `/write-tests` in chat
- Agent: Type `/agent-code-reviewer` in chat
- Or: Attach via paperclip → Prompt files → select prompt

### Task-Based Usage (Invoke Agent Personas)

**Solution:** Agents are prompts! Invoke them explicitly when needed.

#### How It Works

**Global instructions** = Small listing of available agents (just names)  
**Agent prompts** = Full agent definitions (system prompt, constraints, etc.)  
**You invoke** = Attach agent prompt when needed

**Example workflow:**

1. **Global instructions lists:**

   ```markdown
   ## Agents

   - **code-reviewer**: Structured code review with actionable findings

   _Example: `/agent-code-reviewer` - Review this code_
   ```

2. **Agent prompt contains full definition:**

   ```markdown
   # Agent: code-reviewer

   ## Persona

   You are a senior software engineer conducting thorough code review.
   Focus on: correctness, security, performance, maintainability...

   ## Constraints

   - Do not approve code with security vulnerabilities
   - Flag missing test coverage
   ```

3. **You invoke in chat:**

   ```
   /agent-code-reviewer

   Review this UserService.kt. Focus on Spring Boot best practices.
   ```

4. **Compose multiple prompts together:**

   ```
   /agent-code-reviewer /summarize-pr

   Review this code and provide a PR summary.
   ```

5. **Copilot:**
   - Loads agent-code-reviewer.prompt.md (sees full persona)
   - Applies that role to the task
   - Uses your custom instructions ("Focus on Spring Boot...")

#### Prompt Composition

**You can combine multiple prompts in a single invocation:**

```
/agent-code-reviewer /summarize-pr
```

VS Code will:

1. Load the agent-code-reviewer persona (system prompt, constraints, rules)
2. Load the summarize-pr task template
3. Apply both to your request

**Common patterns:**

- `/agent-code-reviewer` - Just the agent persona
- `/summarize-pr` - Just the task template
- `/agent-code-reviewer /summarize-pr` - Agent persona + task format
- `/scaffold-feature` - Quick boilerplate generation
- `/agent-feature-builder` - Full feature implementation
- `/scaffold-feature /agent-feature-builder` - Scaffold then implement

**Agent vs Task Prompts:**

- **Agents** (comprehensive): Full personas with system prompts, constraints, expanded rulepacks
  - Examples: `agent-code-reviewer`, `agent-feature-builder`, `agent-bug-fixer`
- **Tasks** (lightweight): Focused templates for specific operations
  - Examples: `summarize-pr`, `write-tests`, `scaffold-feature`, `extract-method`

They're complementary, not duplicates. Agents provide comprehensive context, tasks provide focused templates.

#### What `@workspace` Does

- Includes **all workspace context**: files, global instructions, path-specific instructions (for current file)
- Does NOT "run" agents - just makes context available
- Use `/agent-name` to explicitly invoke agent personas

#### Scope of Path-Specific Instructions

**Critical:** Path-specific instructions ONLY apply to files matching their pattern.

```markdown
## <!-- .github/instructions/backend.instructions.md -->

## applyTo: "backend/\*_/_.kt"

Use Spring Boot conventions
```

- ✅ Editing `backend/api/UserService.kt` → Rules apply automatically
- ❌ Editing `frontend/App.tsx` → Rules DO NOT apply
- ❌ You CANNOT "invoke" these rules from frontend code

**If you want rules everywhere** → Put them in global instructions (`.github/copilot-instructions.md`)

#### Combining Approaches

```
/agent-code-reviewer /write-tests

Generate tests for UserService.kt following the code-reviewer standards.
Skip integration tests (DB setup complex). Focus on unit tests with mocks.
```

- `/agent-code-reviewer` → Loads agent persona with full system prompt
- `/write-tests` → Loads task prompt template
- Your text → Adds custom task-specific instructions

---

**Key Difference:**

- **Instructions** = Passive (Copilot follows automatically)
- **Prompts** = Active (you invoke them explicitly)
- **Task-based chat** = Flexible (compose agents + custom instructions on-the-fly)

**What We Generate:**

```bash
npm run build
# Creates:
adapters/github-copilot/.github/
├── copilot-instructions.md           # Template (customize for your project)
├── instructions/*.instructions.md    # Per-language patterns
└── prompts/*.prompt.md               # Reusable tasks
```

Copy to your repo: `cp -r adapters/github-copilot/.github ./`

---

## Anthropic Skills (Native)

**Format:** SKILL.md (same as Claude Code)  
**Import:** Use with Anthropic API/Desktop

```bash
adapters/anthropic/run-pytest/SKILL.md
```

**Differences:**

- Identical to Claude Code format (official Anthropic spec)

---

## Comparison

| Feature         | Windsurf/Cursor | Claude Code | Copilot    |
| --------------- | --------------- | ----------- | ---------- |
| **Granularity** | Per-agent       | Per-skill   | All-in-one |
| **Format**      | JSON            | SKILL.md    | Markdown   |
| **Agents**      | ✅               | ❌           | ✅ (docs)   |
| **Prompts**     | ✅               | ❌           | ✅ (docs)   |
| **Skills**      | ❌               | ✅ (exec)    | ✅ (docs)   |
| **Rulepacks**   | ✅ (merged)      | ❌           | ✅ (docs)   |
| **Scope**       | Project/global  | Global CLI  | Repository |
| **Execution**   | IDE             | CLI         | AI assist  |

---

## Use Cases

**Windsurf/Cursor:** IDE coding with agents + rules  
**Claude Code:** CLI automation with executable skills  
**Copilot:** Repository-wide context with flexible file organization (global, path-specific, prompts)

---

## Further Reading

- [agents/README.md](../agents/README.md) - Agent documentation with tool-specific sections
- [prompts/README.md](../prompts/README.md) - Prompt documentation
- [skills/README.md](../skills/README.md) - Skills documentation with MCP integration
- [rulepacks/README.md](../rulepacks/README.md) - Rulepack composition
