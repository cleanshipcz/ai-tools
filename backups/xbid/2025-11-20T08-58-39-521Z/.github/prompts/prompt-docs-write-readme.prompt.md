# write-readme

Create a comprehensive README.md for a project

## Variables

- `{{project_name}}` (required): Name of the project
- `{{project_description}}` (required): What the project does
- `{{tech_stack}}`: Technologies used
- `{{installation_steps}}`: How to install/setup
- `{{usage_examples}}`: How to use the project

## Prompt

Create a README.md for the project "{{project_name}}":

Description: {{project_description}}

{{#tech_stack}}
Tech stack: {{tech_stack}}
{{/tech_stack}}

{{#installation_steps}}
Installation: {{installation_steps}}
{{/installation_steps}}

{{#usage_examples}}
Usage: {{usage_examples}}
{{/usage_examples}}

Include these sections:
1. **Project Title and Description**: Clear overview with badges
2. **Features**: Key capabilities and highlights
3. **Prerequisites**: Required tools and versions
4. **Installation**: Step-by-step setup instructions
5. **Usage**: Quick start and common use cases with examples
6. **Configuration**: Environment variables and config options
7. **API Reference**: Link to detailed API docs (if applicable)
8. **Contributing**: How to contribute (link to CONTRIBUTING.md)
9. **Testing**: How to run tests
10. **License**: License information
11. **Authors/Maintainers**: Credits
12. **Acknowledgments**: Dependencies and inspiration

Use proper markdown formatting with code blocks, lists, and links.

