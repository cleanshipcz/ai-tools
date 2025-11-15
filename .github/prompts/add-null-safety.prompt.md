# add-null-safety

Add null safety checks to code that may have null pointer issues

## Variables

- `{{code}}` (required): The code to make null-safe
- `{{language}}` (required): Programming language

## Prompt

Analyze the following {{language}} code for potential null pointer issues and add appropriate null safety:

```{{language}}
{{code}}
```

Tasks:

1. Identify all places where null/undefined could cause issues
2. Add appropriate null checks or safe navigation
3. Use language idioms (e.g., Kotlin's `?.`, Python's `Optional`, etc.)
4. Consider returning early with null checks where appropriate
5. Add defensive assertions if needed

{{> constraints}}
{{> acceptance_criteria}}
