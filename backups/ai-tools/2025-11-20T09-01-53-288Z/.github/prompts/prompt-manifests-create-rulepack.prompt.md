# create-rulepack

Clarify coding standards and create a simple rulepack.yml manifest

## Variables

- `{{user_description}}` (required): Brief description of the coding standards or rules needed

## Prompt

The user wants to create a rulepack based on this description:

{{user_description}}

**Your job is to clarify and translate:**

1. **Ask clarifying questions:**
   - What domain does this rulepack cover? (language, framework, security, style, etc.)
   - What specific coding standards should be enforced?
   - Are these rules from a standard? (PEP 8, ESLint, etc.)
   - Any must-follow vs nice-to-have rules?

2. **Suggest structure:**
   - Propose a kebab-case ID (e.g., coding-python, security-baseline)
   - Identify 2-4 rule categories
   - Recommend 10-20 total rules

3. **Create simple rulepack.yml with:**
   - id, version (1.0.0), description (1 sentence)
   - tags: [language/framework, category]
   - rules: Array of 10-20 specific, actionable rules
   - metadata: author, dates

**Keep focused, allow detail:**
- Rules should be specific and actionable
- Recommend 1-2 sentences per rule; allow more for complex rules
- Group related rules together
- Simple rulepacks: ~50-80 lines; Comprehensive ones: as needed

**Example:**

User: "Python coding standards"

You: "Questions:
- Follow PEP 8?
- Type hints required?
- Docstring style? (Google, NumPy, etc.)
- Any specific patterns? (async, dataclasses, etc.)"

User: "Yes PEP 8, type hints required, Google docstrings, prefer dataclasses"

You: "Perfect! I'll create 'coding-python' with:
- PEP 8 formatting rules
- Type hint requirements
- Google-style docstrings
- Dataclass usage patterns
Sound good?"

