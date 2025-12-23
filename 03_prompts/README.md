# Prompts

Atomic, reusable prompt templates for AI models. These manifests define specific AI tasks using the `PromptManifest` schema.

## 🎯 Purpose

Prompts are the atomic building blocks of AI interaction in the `ai-tools` ecosystem. They provide:

- ✅ **Structure**: A clear way to define input variables and expected output.
- ✅ **Rulepacks**: Reusable sets of rules (referenced by ID).
- ✅ **Rules**: Custom rules specific for the given prompt.
- ✅ **Consistency**: Ensure the same logic is applied across different workflows.
- ✅ **Composition**: Prompts can be included in agents or used via rulepacks.

**Think of prompts as:** Specialized templates for specific engineering tasks like renaming variables, generating tests, or documenting APIs.

## 📁 File Structure

Prompts are defined in YAML files using the `PromptManifest` schema.

```yaml
id: prompt-id           # Unique identifier for the prompt
description: summary   # Brief description of the prompt's purpose
variables:             # List of input variables
  - name: variable_name
    required: true
    description: Summary...
rulepacks:             # Reusable sets of rules (Rulepack IDs)
  - base
rules:                 # Custom rules specific for this given prompt
  - 'Do X always'
content: |             # The core prompt template (Mustache)
  {{variable_name}}...
outputs:               # Output configuration
  format: markdown/code
metadata:              # Manifest metadata
  version: 1.0.0
  author: AI Tools Team
  tags: [tag1, tag2]
```

## 🎨 Design Patterns

### Atomicity
Each prompt should do ONE thing well. Instead of a "refactor code" prompt, we have `improve-naming`, `extract-method`, and `simplify-conditionals`.

### Variable-Driven Templates
Use Mustache syntax (`{{variable}}`) to make prompts dynamic and reusable. Required variables ensure the AI has the necessary context, while optional variables allow for flexibility.

### Rule-Based Constraints
Use reusable **Rulepacks** to ensure consistency across the project, and the **rules** field to provide specific behavioral constraints tailored to a single prompt. This keeps the `content` focused on the template while ensuring quality and standard compliance.

### Composition via Shared Snippets
Common prompt sections (like acceptance criteria or security constraints) can be shared across multiple prompts to ensure consistency and ease of maintenance.

## 🔍 Validation & Building

Prompts are validated against the `PromptManifest` schema and built into tool-specific configurations.

```bash
# Validate prompts
npm run validate

# Build specialized tool configs
npm run build
```

---

*Note: For a list of available prompts and their details, explore the subdirectories in this folder.*
