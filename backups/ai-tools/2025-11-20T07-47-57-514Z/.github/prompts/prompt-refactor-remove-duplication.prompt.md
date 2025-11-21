# remove-duplication

Identify and eliminate code duplication (DRY principle)

## Variables

- `{{code}}` (required): The code containing duplication
- `{{language}}` (required): Programming language of the code
- `{{context}}`: Additional context about the codebase

## Prompt

Eliminate duplication in the following {{language}} code:

```{{language}}
{{code}}
```

{{#context}}
Context: {{context}}
{{/context}}

Analysis:
1. Identify duplicated or similar code patterns
2. Determine the appropriate abstraction (function, class, utility)
3. Extract common logic while preserving differences
4. Use parameterization for variations

Provide:
1. **Analysis**: Duplicated patterns identified
2. **Refactored Code**: DRY implementation with extracted functions
3. **Rationale**: Why this abstraction is appropriate
4. **Usage Examples**: How callers use the extracted code

{{> constraints}}

