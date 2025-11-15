# extract-method

Extract a pure function from a selected block of code

## Variables

- `{{code}}` (required): The code block to extract
- `{{target_name}}`: Desired name for the extracted method
- `{{language}}` (required): Programming language of the code

## Prompt

Extract the following {{language}} code into a separate, pure function:

```{{language}}
{{code}}
```

{{#target_name}}
Suggested function name: {{target_name}}
{{/target_name}}

Requirements:

- Create a focused, single-purpose function
- Use appropriate parameter types
- Return a clear, well-typed result
- Add proper error handling if needed
- Include a brief docstring/comment

{{> constraints}}
{{> acceptance_criteria}}
