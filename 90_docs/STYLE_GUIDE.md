# Style Guide for AI Tools

## Writing Effective Prompts

### Structure

Every prompt should have:

1. **Clear Objective**: What should the AI accomplish?
2. **Context**: What information is relevant?
3. **Constraints**: What limitations or requirements must be met?
4. **Output Format**: How should the result be structured?

### Language Guidelines

- **Be Specific**: Use concrete, actionable language
- **Be Concise**: Remove unnecessary words
- **Be Clear**: Avoid ambiguous terms
- **Use Imperatives**: "Extract the method" not "You should extract the method"

### Examples

❌ **Bad:**

```
Make this code better.
```

✅ **Good:**

```
Refactor this Python function to:
- Improve readability by using list comprehensions
- Add type hints to all parameters
- Include a docstring with parameter descriptions
```

## Variable Naming

### Conventions

- Use `snake_case` for variable names: `target_name`, `code_context`
- Make variables self-documenting: `error_message` not `msg`
- Use consistent naming across prompts

### Required vs Optional

- Clearly mark variables as required or optional
- Provide sensible defaults for optional variables
- Document what each variable represents

```yaml
variables:
  - name: code
    required: true
    description: 'The code block to analyze'
  - name: focus_areas
    required: false
    description: 'Specific areas to focus on (optional)'
    default: 'all'
```

## Evaluation Rubric

### Prompt Quality Checklist

- [ ] Has a unique, descriptive ID
- [ ] Includes semantic version
- [ ] Has clear description (10-500 chars)
- [ ] Documents all variables
- [ ] Specifies output format
- [ ] Includes constraints or rules
- [ ] Has at least one example (if applicable)
- [ ] References appropriate rulepacks
- [ ] Free of secrets or sensitive data

### Agent Quality Checklist

- [ ] Clear purpose statement
- [ ] Appropriate temperature setting (0.0-1.0)
- [ ] Relevant rulepacks included
- [ ] Required capabilities documented
- [ ] System prompt is focused
- [ ] User template uses variables correctly
- [ ] Constraints are explicit

### Rulepack Quality Checklist

- [ ] Rules are specific and actionable
- [ ] Each rule is 5-500 characters
- [ ] No contradictory rules
- [ ] Extends appropriate parent packs
- [ ] Rules are language-agnostic when possible

## Token Efficiency

### Keep Prompts Lean

- Remove redundant instructions
- Use includes for shared content
- Avoid repeating rulepack content in prompts
- Keep system prompts under 1000 tokens

### Optimization Techniques

1. **Use Includes**: Share common constraints
2. **Reference Rulepacks**: Don't inline rules
3. **Template Efficiently**: Use conditionals for optional sections
4. **Compress Examples**: Use minimal examples

## Testing Guidelines

### Write Tests For

- **Happy Path**: Normal expected inputs
- **Edge Cases**: Empty, null, boundary values
- **Error Cases**: Invalid inputs, missing data
- **Safety**: Jailbreak attempts, prompt injection

### Eval Suite Best Practices

- Keep datasets small and focused
- Use multiple check types (regex, command, LLM judge)
- Set realistic budgets
- Track changes with baselines
- Document expected behavior

## Version Control

### Semantic Versioning

- **MAJOR**: Breaking changes to interface or behavior
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, clarifications

### Changelog Entries

```markdown
## [1.2.0] - 2025-01-15

### Added

- New variable `max_iterations` for controlling loop count

### Changed

- Improved clarity of error messages

### Fixed

- Corrected handling of null values in code parser
```

## Security Best Practices

### Never Include

- API keys or tokens
- Passwords or credentials
- Personal identifiable information (PII)
- Internal system details
- Proprietary algorithms

### Always

- Use `${ENV_VAR}` placeholders for secrets
- Validate and sanitize inputs
- Implement rate limiting in checks
- Test against red team scenarios
- Follow principle of least privilege

## Common Patterns

### Conditional Content

Use Mustache-style templates:

```yaml
content: |
  {{#optional_param}}
  Additional instructions when param is provided: {{optional_param}}
  {{/optional_param}}
```

### Including Shared Content

```yaml
includes:
  - ../shared/constraints.md
  - ../shared/acceptance_criteria.md
```

### Rulepack Composition

```yaml
extends:
  - base
  - security
rulepacks:
  - coding-python
```

## Documentation Requirements

### Inline Documentation

- Comment complex logic in build scripts
- Document non-obvious decisions
- Explain "why" not just "what"

### External Documentation

- Keep AGENTS.md up to date (auto-generated)
- Document breaking changes in CHANGELOG
- Update README for new features

## Deprecation Process

1. Mark as deprecated in description
2. Add deprecation notice in comments
3. Keep for at least one major version
4. Provide migration guide
5. Create compatibility mapping in `adapters/compat.json`

```yaml
id: old-prompt
version: 2.0.0
description: '[DEPRECATED] Use new-prompt instead. This prompt will be removed in v3.0.0'
```
