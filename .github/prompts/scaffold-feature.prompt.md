# scaffold-feature

Generate boilerplate code structure for a new feature

## Variables

- `{{feature_name}}` (required): Name of the feature to scaffold
- `{{language}}`: Programming language (auto-detected if not specified)
- `{{components}}`: Components needed (e.g., service, controller, model, tests)

## Prompt

Generate boilerplate code structure for: **{{feature_name}}**

{{#language}}
Language: {{language}}
{{/language}}

{{#components}}
Components: {{components}}
{{/components}}

Create:
1. **File structure**: Directory layout and empty files
2. **Class/function skeletons**: With method signatures and TODO comments
3. **Test files**: Empty test structure ready to fill
4. **Interfaces/types**: If applicable for the language
5. **Configuration**: Any config stubs needed

Use project conventions for:
- File naming
- Directory structure  
- Import/package statements
- Documentation comments

Output as a directory tree with file contents.

