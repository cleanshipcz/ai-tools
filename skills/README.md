# Skills

Executable commands that AI agents can run - like running tests, linters, or searching the codebase.

## 📁 What's Here

- **run-pytest.yml** - Execute Python tests with pytest
- **run-gradle-tests.yml** - Run Gradle test suite
- **run-detekt.yml** - Kotlin static analysis
- **run-ktlint.yml** - Kotlin linter
- **search-repo.yml** - Search codebase with grep

## 🎯 Purpose

Skills define **executable actions** that AI agents can perform:

- Running tests
- Code analysis
- Searching code
- Building projects
- Running linters

**Skills are exposed through:**

- MCP (Model Context Protocol) servers
- Tool adapters for AI coding assistants
- Agent capabilities

## 🏗️ Hybrid Architecture: YAML Source → Anthropic SKILL.md

This repository uses a **hybrid approach** to support both tool-agnostic configuration and native Anthropic Agent Skills format:

### Source Format (YAML)

Skills are authored in **YAML** for:

- ✅ Tool-agnostic: Generate configs for Windsurf, Cursor, Claude, etc.
- ✅ Validation: JSON Schema ensures correctness
- ✅ Composition: Skills can be referenced in agents
- ✅ Structured data: Easy to transform and version

```yaml
# skills/run-pytest.yml
id: run-pytest
version: 1.0.0
description: Run pytest unit tests
command:
  program: 'pytest'
  args: ['-v', '--tb=short']
timeout_sec: 300
tags: [test, python]
```

### Generated Format (SKILL.md)

During build, YAML manifests are **automatically converted** to official **Anthropic Agent Skills** format (`SKILL.md` with YAML frontmatter):

```bash
npm run build
# Generates: adapters/claude-code/skills/run-pytest/SKILL.md
```

**Generated structure:**

```
adapters/claude-code/skills/
├── run-pytest/
│   └── SKILL.md          # ✅ Native Anthropic format
├── run-detekt/
│   └── SKILL.md
└── ...
```

**Why this approach?**

- **Best of both worlds:** YAML source for all tools + native SKILL.md for Claude
- **Single source of truth:** One YAML manifest → multiple output formats
- **Progressive disclosure:** Claude loads SKILL.md content on-demand
- **Rich instructions:** Generated markdown includes usage, examples, troubleshooting

### Build Process

```bash
# Generate all adapters including SKILL.md files
npm run build

# Or generate only SKILL.md files
npm run skills
```

The build process:

1. Reads YAML manifests from `skills/*.yml`
2. Validates against JSON Schema
3. Generates tool-specific configs (Windsurf, Cursor)
4. Generates Anthropic SKILL.md files with:
   - YAML frontmatter (name, description)
   - Markdown instructions
   - Usage examples
   - Troubleshooting guides

### Example: Generated SKILL.md

From the YAML source above, the build generates:

```markdown
---
name: run-pytest
description: Run pytest unit tests
---

# Run Pytest

## When to use this skill

Use this skill when:

- Working with test, python
- User asks to "run pytest"

## Prerequisites

- pytest must be installed

## How to use

Run the following command:

\`\`\`bash
pytest -v --tb=short
\`\`\`

**Timeout:** 300 seconds

## Understanding the output

### Exit code

- `0`: Success
- Non-zero: Error occurred (check stderr for details)

### Standard output (stdout)

Contains the main output of the command.

### Standard error (stderr)

Contains error messages and warnings.

## Examples

### Basic usage

\`\`\`
User: "Can you run pytest?"
Assistant: [Runs: pytest -v --tb=short]
Assistant: [Interprets output and reports results]
\`\`\`

## Troubleshooting

### Command not found

Ensure `pytest` is installed and available in the PATH.

### Timeout

If the command takes longer than 300 seconds, it will be terminated. Consider:

- Breaking down the task into smaller steps
- Running on a subset of files
- Checking for performance issues
```

This rich markdown format is what Claude uses natively!

## 🔧 How Skills Work in Different Tools

Skills are **automatically included** when you import agents. The agent's `capabilities` field determines which skills are available.

### Windsurf

1. **Build configs:**

   ```bash
   npm run build
   ```

2. **Import an agent:**
   - Settings → Cascade → Rules
   - Import: `adapters/windsurf/rules/code-reviewer.json`

3. **Skills are bundled:**
   - Agent JSON includes skill definitions
   - Cascade can execute these commands when needed

4. **Usage:**
   - Ask: "Run the tests"
   - Agent uses the `run-pytest` or `run-gradle-tests` skill

### Cursor

1. **Build:**

   ```bash
   npm run build
   ```

2. **Import:**
   - Import `adapters/cursor/recipes.json`

3. **Skills available:**
   - Listed in `adapters/claude-code/skills.json`
   - Agents reference these skills

### Claude Code

1. **Build:**

   ```bash
   npm run build
   ```

2. **Skills file:**

   ```bash
   cat adapters/claude-code/skills.json
   ```

3. **Contains:**
   - All skill definitions in one file
   - Command, args, timeout for each
   - Import into Claude Code's tool configuration

### MCP Integration

Skills can be exposed through MCP servers. See [MCP README](../mcp/README.md) for details.

## 📝 Creating Your Own Skills

### Basic Skill

```yaml
# skills/my-lint.yml
id: my-lint
version: 1.0.0
description: Run my custom linter
command:
  program: 'npm'
  args:
    - 'run'
    - 'lint'
  cwd: '.'
timeout_sec: 60
outputs:
  stdout: true
  stderr: true
  exit_code: true
tags:
  - lint
  - quality
```

### Advanced Features

**With environment variables:**

```yaml
command:
  program: 'python'
  args:
    - '-m'
    - 'pytest'
    - '--maxfail=5'
  cwd: '.'
  env:
    PYTHONPATH: 'src:tests'
    CI: 'true'
```

**Conditional execution:**

```yaml
prerequisites:
  - file_exists: 'package.json'
  - command_available: 'npm'
```

**Multiple outputs:**

```yaml
outputs:
  stdout: true
  stderr: true
  exit_code: true
  files:
    - 'coverage/coverage.json'
    - 'test-results.xml'
```

## 🔍 Validation & Building

```bash
# Validate
npm run validate

# Build
npm run build

# Check generated files
cat adapters/claude-code/skills.json
```

## 📋 Available Skills

### run-pytest (v1.0.0)

Execute Python tests with pytest.

**Command:** `python -m pytest`  
**Timeout:** 300s  
**Outputs:** stdout, stderr, exit_code

**Use for:**

- Running Python test suites
- Checking test coverage
- Validating Python code

### run-gradle-tests (v1.0.0)

Run Gradle test suite.

**Command:** `./gradlew test`  
**Timeout:** 600s  
**Outputs:** stdout, stderr, exit_code

**Use for:**

- Running JVM/Kotlin tests
- Gradle project testing
- Integration test execution

### run-detekt (v1.0.0)

Kotlin static analysis with Detekt.

**Command:** `./gradlew detekt`  
**Timeout:** 300s  
**Outputs:** stdout, stderr, exit_code

**Use for:**

- Kotlin code analysis
- Finding code smells
- Style checking

### run-ktlint (v1.0.0)

Kotlin linter.

**Command:** `./gradlew ktlintCheck`  
**Timeout:** 120s  
**Outputs:** stdout, stderr, exit_code

**Use for:**

- Kotlin code formatting
- Style enforcement
- Auto-fixing with ktlintFormat

### search-repo (v1.0.0)

Search codebase with grep.

**Command:** `grep -r --include='*.{js,ts,py,kt}' [pattern] .`  
**Timeout:** 60s  
**Outputs:** stdout, stderr, exit_code

**Use for:**

- Finding code patterns
- Searching for usages
- Code exploration

## 🎨 Best Practices

### Skill Design

1. **Single purpose** - One skill does one thing
2. **Reasonable timeout** - Don't make AI wait too long
3. **Clear output** - Return actionable information
4. **Fail gracefully** - Handle errors well

### Timeout Guidelines

- **Quick checks:** 30-60s (linting, formatting)
- **Unit tests:** 120-300s (depends on suite size)
- **Integration tests:** 300-600s (can be slower)
- **Builds:** 600-1200s (complex projects)

### Security Considerations

**Safe:**

```yaml
command:
  program: 'npm'
  args: ['test']
```

**Unsafe:**

```yaml
command:
  program: 'bash'
  args: ['-c', 'rm -rf /'] # ❌ NEVER DO THIS
```

**Guidelines:**

- Use specific commands, not shells
- Avoid `bash -c` or `sh -c`
- Don't pass user input directly
- Validate file paths
- Use read-only operations when possible

## 🔗 Related

- [Agents](../agents/README.md) - Agents that use skills
- [MCP](../mcp/README.md) - MCP servers expose skills to agents
- [Rulepacks](../rulepacks/README.md) - Guidelines for using skills

## 💡 Usage Examples

### In Agent Configuration

```yaml
# agents/my-agent.yml
capabilities:
  - mcp:filesystem
  - skill:run-pytest
  - skill:search-repo
```

### Testing Your Skill

```bash
# Run the command manually
./gradlew test

# Time it
time ./gradlew test

# Check if timeout is appropriate
```

### Debugging

```bash
# View generated skill
cat adapters/claude-code/skills.json | jq '.[] | select(.id=="run-pytest")'

# Test in agent
# Import agent, ask it to "run the tests"
# Check if skill is executed correctly
```

## 📚 Further Reading

- [Main README](../README.md) - Full project documentation
