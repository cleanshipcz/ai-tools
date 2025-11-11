# Rulepacks

Reusable collections of coding guidelines and best practices. Mix and match to create custom coding standards.

## 📁 What's Here

- **base.yml** - Universal coding standards (naming, formatting, documentation)
- **security.yml** - Security best practices (input validation, secrets handling)
- **coding-python.yml** - Python-specific conventions (PEP 8, type hints)
- **coding-kotlin.yml** - Kotlin-specific conventions (naming, null safety)
- **reviewer.yml** - Code review guidelines
- **windsurf-defaults.yml** - Windsurf IDE default rules

## 🎯 Purpose

Rulepacks are **building blocks** for agents. Instead of duplicating rules across agents, create reusable rulepacks that can be composed together.

**Example:**
```yaml
# agents/my-agent.yml
rulepacks:
  - base       # Everyone needs good fundamentals
  - security   # Add security checks
  - coding-python  # Python-specific rules
```

This agent now has ALL rules from all three rulepacks!

## 🔧 How to Use in Different Tools

### Windsurf

Rulepacks are automatically bundled into agents when you import them.

1. Build the configs:
   ```bash
   npm run build
   ```

2. Import an agent (which includes its rulepacks):
   ```bash
   # In Windsurf: Settings → Cascade → Rules
   # Import: adapters/windsurf/rules/code-reviewer.json
   ```

3. The `code-reviewer` agent includes these rulepacks:
   - `base` - Fundamental coding standards
   - `reviewer` - Review-specific guidelines
   - `security` - Security checks

**View what's included:**
```bash
cat adapters/windsurf/rules/code-reviewer.json
# Look for the "rules" array - it contains all merged rules
```

### Cursor

Same as Windsurf - rulepacks are bundled into agents:

1. Build: `npm run build`
2. Import: `adapters/cursor/recipes.json`
3. Rulepacks are merged into each recipe

### Claude Code

Rulepacks are merged into agent prompts:

```bash
cat adapters/claude-code/agents/code-reviewer.json
# The "system" field contains all rules
```

### Manual Use

If you want to use rulepacks directly (outside of agents):

1. Read the YAML file:
   ```bash
   cat rulepacks/base.yml
   ```

2. Copy the `rules` array

3. Add to your tool's custom instructions

## 📝 Creating Your Own Rulepacks

### Basic Rulepack

```yaml
# rulepacks/my-team-rules.yml
id: my-team-rules
version: 1.0.0
description: Our team's coding standards
rules:
  - "Use meaningful variable names"
  - "Functions should do one thing"
  - "Maximum 50 lines per function"
  - "Write tests for all public APIs"
  - "Document complex logic"
```

### Extending Other Rulepacks

```yaml
# rulepacks/my-python-rules.yml
id: my-python-rules
version: 1.0.0
description: Our Python standards
extends:
  - base           # Include all base rules
  - coding-python  # Include Python rules
rules:
  # Add your team-specific rules
  - "Use Black for formatting"
  - "Prefer dataclasses over dictionaries"
  - "Type hints on all public functions"
```

**Inheritance chain:**
```
my-python-rules
  ├─ base
  │   └─ (fundamental rules)
  ├─ coding-python
  │   └─ base (inherited)
  │   └─ (Python-specific rules)
  └─ (team-specific rules)
```

All rules are merged together when building!

### Best Practices

**Good Rules:**
```yaml
rules:
  - "Use descriptive variable names (not x, y, z)"
  - "Prefer early returns over nested if statements"
  - "Maximum cyclomatic complexity: 10"
```

**Bad Rules:**
```yaml
rules:
  - "Be good"  # Too vague
  - "Don't write bad code"  # Not actionable
  - "Follow best practices"  # What practices?
```

**Rules should be:**
- ✅ Specific and actionable
- ✅ Measurable (can be checked)
- ✅ Justified (explain why)
- ✅ Consistent with other rules

## 🔍 Validation

```bash
npm run validate
```

Checks:
- Valid YAML syntax
- Required fields (id, version, description)
- Unique IDs across all manifests
- Valid semantic versioning
- Extends refers to existing rulepacks
- No circular dependencies

## 🔨 Building

```bash
npm run build
```

Process:
1. Load all rulepacks
2. Resolve inheritance (extends)
3. Merge rules into agents
4. Generate tool-specific configs

**Output:**
```
adapters/
├── windsurf/rules/
│   ├── code-reviewer.json    (includes base, reviewer, security rules)
│   ├── bug-fixer.json         (includes base rules)
│   └── ...
├── cursor/recipes.json        (all agents with merged rules)
└── claude-code/agents/        (agents with rules in system prompt)
```

## 📋 Available Rulepacks

### base (v1.2.0)

Universal coding standards applicable to any language:
- Meaningful naming conventions
- Function size limits
- Documentation requirements
- Code organization principles
- Error handling patterns

**Use in:** Every agent

### security (v1.1.0)

Security-focused guidelines:
- Input validation and sanitization
- Secrets management (no hardcoded keys)
- SQL injection prevention
- XSS protection
- Authentication/authorization checks

**Use in:** Agents that handle user input, APIs, databases

### coding-python (v1.3.0)

Python-specific conventions:
- PEP 8 compliance
- Type hints usage
- Docstring format (Google style)
- Import organization
- List comprehensions vs loops
- Context managers for resources

**Extends:** base

### coding-kotlin (v1.2.0)

Kotlin-specific conventions:
- Naming conventions (camelCase, PascalCase)
- Null safety patterns
- Data classes usage
- Extension functions
- Coroutine best practices

**Extends:** base

### reviewer (v1.1.0)

Code review guidelines:
- What to look for in reviews
- How to provide feedback
- Breaking changes detection
- Performance implications
- Test coverage expectations

**Use in:** Code review agents

### windsurf-defaults (v1.0.0)

Default rules from Windsurf IDE:
- Windsurf-specific conventions
- IDE integration patterns
- Cascade chat guidelines

**Use in:** Windsurf-specific agents

## 🎨 Design Patterns

### Composition over Duplication

**Bad:**
```yaml
# agents/python-reviewer.yml
rules:
  - "Use meaningful names"
  - "Functions under 50 lines"
  - "Check for SQL injection"
  - "Follow PEP 8"
  - "Use type hints"
  # ... 50+ rules duplicated across agents
```

**Good:**
```yaml
# agents/python-reviewer.yml
rulepacks:
  - base
  - security
  - coding-python
  - reviewer
# Rules are reused, not duplicated!
```

### Layered Architecture

```
base
  ↓ (extended by)
coding-python ──→ my-python-rules
  ↓                     ↓
security         my-team-rules
  ↓ (all used in)       ↓
agent: python-security-reviewer
```

### Single Responsibility

Each rulepack should focus on ONE concern:
- ✅ `security` - Only security rules
- ✅ `coding-python` - Only Python conventions
- ❌ `python-security-docs` - Too broad, split it

## 🔗 Related

- [Agents](../agents/README.md) - Use rulepacks to configure agents
- [Prompts](../prompts/README.md) - Combine with rules for better output
- [Skills](../skills/README.md) - Actions agents can take

## 💡 Tips

### Finding the Right Granularity

**Too granular:**
```yaml
rulepacks/python-naming.yml
rulepacks/python-imports.yml
rulepacks/python-types.yml
rulepacks/python-docs.yml
```
Too many files, hard to manage.

**Too broad:**
```yaml
rulepacks/everything-python.yml  # 200+ rules
```
Hard to reuse, monolithic.

**Just right:**
```yaml
rulepacks/coding-python.yml  # 20-30 focused rules
rulepacks/testing-python.yml # Test-specific rules
```

### Versioning Strategy

- **MAJOR:** Breaking changes (removing rules, changing meaning)
- **MINOR:** Adding new rules (backward compatible)
- **PATCH:** Clarifications, typo fixes

### Testing Your Rules

1. Create an agent using your rulepack
2. Build and import into your tool
3. Test with real code scenarios
4. Iterate based on actual usage

```bash
npm run build
# Import into Windsurf/Cursor
# Write some code and see if AI follows the rules
```

## 📚 Further Reading

- [STYLE_GUIDE.md](../docs/STYLE_GUIDE.md) - Writing effective rules
- [Main README](../README.md) - Full project documentation
