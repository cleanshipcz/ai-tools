# summarize-pr

Generate a concise summary of a pull request

## Variables

- `{{diff}}` (required): The git diff to summarize
- `{{context}}`: Additional context about the PR

## Prompt

Analyze this pull request and provide a clear summary:

```diff
{{diff}}
```

{{#context}}
Context: {{context}}
{{/context}}

Provide:
1. **Overview**: One-line summary of what this PR does
2. **Changes**: Bullet points of key changes
3. **Breaking Changes**: Any backward-incompatible changes (if any)
4. **Testing**: What testing was done or is needed
5. **Impact**: Areas of the codebase affected

Format as markdown suitable for a PR description.

