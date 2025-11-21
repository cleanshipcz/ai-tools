# improve-naming

Rename variables, functions, and classes for better clarity

## Variables

- `{{code}}` (required): The code with unclear names
- `{{language}}` (required): Programming language of the code
- `{{context}}`: Business/domain context

## Prompt

Improve the naming in this {{language}} code:

```{{language}}
{{code}}
```

{{#context}}
Domain context: {{context}}
{{/context}}

Apply these naming principles:
- Use intention-revealing names that explain purpose
- Follow language conventions (camelCase, snake_case, PascalCase)
- Make names searchable (avoid single letters except loop counters)
- Use consistent terminology from the domain
- Avoid mental mapping (don't make reader translate)
- Use verbs for functions/methods, nouns for variables/classes

Provide:
1. **Refactored Code**: With improved names
2. **Naming Changes**: Table of old → new names with rationale
3. **Consistency Check**: Ensure domain terms are used consistently

