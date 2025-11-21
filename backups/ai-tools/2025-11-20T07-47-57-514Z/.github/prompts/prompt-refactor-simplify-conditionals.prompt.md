# simplify-conditionals

Simplify complex conditional logic for better readability

## Variables

- `{{code}}` (required): The code block containing complex conditionals
- `{{language}}` (required): Programming language of the code

## Prompt

Simplify the following {{language}} conditional logic:

```{{language}}
{{code}}
```

Apply these refactoring techniques:
- Use early returns/guard clauses to reduce nesting
- Extract complex boolean expressions into named variables
- Combine related conditions
- Use language-specific idioms (e.g., switch expressions, pattern matching)
- Remove redundant checks

Provide:
1. **Simplified Code**: Cleaner conditional logic
2. **Explanation**: What was changed and why it's better
3. **Behavior Proof**: Confirm logic is equivalent

{{> constraints}}

