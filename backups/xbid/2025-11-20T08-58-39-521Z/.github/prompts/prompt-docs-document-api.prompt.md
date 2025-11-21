# document-api

Generate comprehensive API documentation

## Variables

- `{{code}}` (required): The API code to document
- `{{language}}` (required): Programming language
- `{{doc_style}}`: Documentation style (javadoc, sphinx, jsdoc, etc.)

## Prompt

Generate API documentation for this {{language}} code:

```{{language}}
{{code}}
```

{{#doc_style}}
Use {{doc_style}} format.
{{/doc_style}}

Include:
- **Summary**: One-line description of what it does
- **Parameters**: Name, type, description for each parameter
- **Returns**: Type and description of return value
- **Raises/Throws**: Exceptions that can be thrown
- **Examples**: Practical usage examples with expected output
- **Notes**: Any important considerations or caveats
- **See Also**: Links to related functions/classes

Format the documentation according to the language standard:
- Python: Google/NumPy/Sphinx style docstrings
- Java/Kotlin: Javadoc/KDoc
- TypeScript/JavaScript: JSDoc/TSDoc
- Other: Follow language conventions

