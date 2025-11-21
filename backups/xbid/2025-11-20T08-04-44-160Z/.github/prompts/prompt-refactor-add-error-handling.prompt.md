# add-error-handling

Add comprehensive error handling and validation

## Variables

- `{{code}}` (required): The code needing error handling
- `{{language}}` (required): Programming language of the code

## Prompt

Add proper error handling to this {{language}} code:

```{{language}}
{{code}}
```

Consider these error scenarios:
- Invalid input parameters
- Null/None values
- Resource unavailability (files, network, database)
- Boundary conditions (empty collections, overflow)
- Concurrent access issues
- External service failures

Apply these patterns:
- Input validation at function boundaries
- Use Result types or exceptions appropriately
- Provide context in error messages
- Clean up resources (RAII, try-finally, context managers)
- Fail fast for programming errors
- Handle expected errors gracefully

Provide:
1. **Refactored Code**: With comprehensive error handling
2. **Error Cases**: List of scenarios handled
3. **Error Messages**: Clear, actionable messages

{{> constraints}}

