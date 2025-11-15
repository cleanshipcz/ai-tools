# write-tests

Generate comprehensive unit tests for given code

## Variables

- `{{code}}` (required): The code to test
- `{{language}}` (required): Programming language
- `{{test_framework}}`: Testing framework to use (e.g., pytest, junit, jest)

## Prompt

Write comprehensive unit tests for the following {{language}} code:

```{{language}}
{{code}}
```

{{#test_framework}}
Use {{test_framework}} as the testing framework.
{{/test_framework}}

Requirements:

- Test normal/expected inputs (happy path)
- Test edge cases (empty, null, boundary values)
- Test error conditions and exceptions
- Use clear, descriptive test names
- Include setup and teardown if needed
- Mock external dependencies
- Add comments for non-obvious test logic

{{> acceptance_criteria}}
