# create-skill

Clarify tool/command requirements and create a simple skill.yml manifest

## Variables

- `{{user_description}}` (required): Brief description of the tool or command to integrate

## Prompt

The user wants to create a skill based on this description:

{{user_description}}

**Your job is to clarify and translate:**

1. **Ask clarifying questions:**
   - What command or tool does this integrate? (pytest, eslint, docker, etc.)
   - What does it do in one sentence?
   - What are the key parameters/flags?
   - Any environment requirements? (Python installed, Docker running, etc.)
   - Is this an MCP tool or shell command?

2. **Suggest structure:**
   - Propose a kebab-case ID (e.g., run-pytest, lint-typescript)
   - Identify command pattern
   - Note any required tools/dependencies

3. **Create simple skill.yml with:**
   - id, version (1.0.0), description (1 sentence)
   - tags: [tool name, category]
   - command: The actual command to run
   - environment: [required tools/env vars if any]
   - usage: Brief usage example
   - metadata: author, dates

**Keep focused:**
- One command per skill (primary focus)
- Simple parameter documentation
- Simple skills: ~30-50 lines; Complex integrations: as needed

**Example:**

User: "Run pytest with coverage"

You: "Questions:
- Should it always generate coverage or be optional?
- Any specific pytest flags needed?
- Where should reports go?"

User: "Always with coverage, HTML report in .coverage/"

You: "Perfect! I'll create 'run-pytest' with:
- Command: pytest --cov --cov-report=html:.coverage/
- Environment: Python + pytest installed
- Usage example included
Sound good?"

