# User-Friendly Prompt Tools

This repository now includes **three easy ways** to use prompts, designed for different preferences and workflows.

## 🌐 Interactive HTML Browser (Recommended)

**Best for:** Visual browsing, quick discovery, one-click copying

### Features

- **Search bar** - Type to find prompts instantly
- **Tag filters** - Filter by category (refactor, qa, docs, etc.)
- **Card view** - Beautiful cards showing prompt details
- **Variable form** - Fill in variables with interactive inputs
- **Live preview** - See prompt update in real-time as you type
- **Example data** - Auto-fill variables with sample data
- **One-click copy** - Copies the filled, ready-to-use prompt
- **Preview modal** - View and edit before copying
- **Responsive design** - Works on desktop and mobile

### Usage

```bash
npm run prompt-html
# Opens PROMPT_LIBRARY.html - double-click to open in browser
```

### What You See

```
┌─────────────────────────────────────────────────┐
│  🎯 AI Prompt Library                           │
│  Copy-paste ready prompts for ChatGPT, Claude   │
├─────────────────────────────────────────────────┤
│  [🔍 Search...]                                 │
│  [refactor] [qa] [docs] [security] [git]        │
├─────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐             │
│  │ extract-     │  │ write-tests  │             │
│  │ method       │  │              │             │
│  │ v1.3.0       │  │ v1.0.0       │             │
│  │              │  │              │             │
│  │ [📋 Copy]    │  │ [📋 Copy]    │             │
│  │ [👁️ View]    │  │ [👁️ View]    │             │
│  └──────────────┘  └──────────────┘             │
└─────────────────────────────────────────────────┘
```

Click "Copy" → Paste into ChatGPT → Done!

## 💻 Interactive CLI Tool

**Best for:** Terminal users, automation, scripting

### Features

- **Interactive prompts** - Asks for each variable
- **Validation** - Ensures required fields are filled
- **Instant output** - Ready-to-paste text
- **List command** - Shows all available prompts
- **Variable substitution** - Handles Mustache templates

### Usage

```bash
# List all prompts
npm run use-prompt

Output:
  REFACTOR:
    • extract-method           - Extract a pure function from code
    • add-null-safety          - Add null safety checks to code
  
  QA:
    • write-tests              - Generate unit tests for code
```

```bash
# Use a specific prompt
npm run use-prompt write-tests

📝 Using prompt: write-tests
📄 Generate comprehensive unit tests for given code

Please provide values for the following variables:

  code (required) - The code to test
  → def add(a, b): return a + b

  language (required) - Programming language
  → Python

✅ Prompt ready! Copy everything below:

═══════════════════════════════════════
Generate comprehensive unit tests for the following code:

```python
def add(a, b): return a + b
```

Include:
- Happy path tests
- Edge cases
- Error conditions
...
═══════════════════════════════════════

💡 Paste this into ChatGPT, Claude, or any LLM
```

## 📄 Static Markdown Library

**Best for:** Documentation, reading, manual copying

### Features

- **Markdown format** - Easy to read and navigate
- **Collapsible sections** - Click to expand prompt text
- **All metadata** - Tags, version, variables, rules
- **Usage examples** - Step-by-step instructions
- **Category organization** - Grouped by type

### Usage

```bash
npm run prompt-library
# Creates PROMPT_LIBRARY.md - open in any text editor
```

### What You Get

```markdown
# Prompt Library

## Refactor

### extract-method

**Extract a pure function from a selected block of code**

🏷️ Tags: `refactor`, `code-quality`
📦 Version: `1.3.0`

**Required Variables:**
- `{{code}}` - ✅ Required - The code block to extract
- `{{language}}` - ✅ Required - Programming language

<details>
<summary>📋 Prompt Text (Click to Expand)</summary>

Extract the following {{language}} code into a separate function...

</details>
```

---

## Comparison

| Feature           | HTML Browser | CLI Tool | Markdown |
| ----------------- | ------------ | -------- | -------- |
| Search & Filter   | ✅            | ❌        | ❌        |
| One-click Copy    | ✅            | ⚠️ CLI    | ❌        |
| Variable Filling  | ✅            | ✅        | ❌        |
| Live Preview      | ✅            | ❌        | ❌        |
| Example Data      | ✅            | ❌        | ❌        |
| Interactive       | ✅            | ✅        | ❌        |
| No Build Required | ❌            | ❌        | ❌        |
| Works Offline     | ✅            | ✅        | ✅        |
| Beautiful UI      | ✅            | ❌        | ⚠️        |
| Terminal-Friendly | ❌            | ✅        | ⚠️        |
| Scriptable        | ❌            | ✅        | ❌        |
| Mobile-Friendly   | ✅            | ❌        | ✅        |

**Recommendation:**

- **First-time users:** Use HTML browser
- **Power users:** Use CLI tool
- **Documentation:** Use Markdown library

---

## Workflow Examples

### Scenario 1: Quick Refactoring with ChatGPT

```bash
# Open HTML browser
npm run prompt-html
# → Opens in default browser

# 1. Type "extract" in search box
# 2. Click "Copy Prompt" on extract-method card (opens modal)
# 3. Fill in the form:
#    - code: [paste your messy code]
#    - language: "JavaScript"
# 4. Watch the preview update in real-time
# 5. Click "Copy Filled Prompt"
# 6. Paste into ChatGPT
# 7. Get refactored code back - no manual find/replace needed!
```

### Scenario 2: Automated Test Generation

```bash
# Use CLI for automation
npm run use-prompt write-tests << EOF
def calculate_total(items):
    return sum(i['price'] for i in items)
Python
EOF

# Output is ready to paste
```

### Scenario 3: Team Documentation

```bash
# Generate markdown for wiki
npm run prompt-library

# Add to your docs repo
cp PROMPT_LIBRARY.md docs/prompts.md
git add docs/prompts.md
git commit -m "Add prompt library"
```

---

## Next Steps

1. **Try the HTML browser** - Most user-friendly
2. **Experiment with CLI** - Great for repeated use
3. **Read the markdown** - Understand available prompts
4. **Create your own** - Add custom prompts to `prompts/`
5. **Share with team** - Everyone uses the same high-quality prompts

---

Built with ❤️ for developers who want better prompts
