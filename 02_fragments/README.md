# Fragments

Reusable reference material and knowledge that can be composed into prompts, agents, and skills. Unlike rulesets (which are behavioral constraints), fragments provide free-form context such as format guides, templates, and usage patterns.

## What's Here

- **confluence-format-guide.yml** - Guide for formatting Confluence pages

## Purpose

Fragments are **building blocks** for providing reference knowledge to agents. Instead of duplicating context across agents, create reusable fragments that can be composed together.

**Key distinction:**

- **Rulesets** = "you must do X" (behavioral constraints, always a list of rules)
- **Fragments** = "here's how X works" (reference knowledge, free-form content)

**Example:**

```yaml
# agents/my-agent.yml
rulesets:
  - base
  - security
fragments:
  - confluence-format-guide
```

This agent now has all rules from the rulesets AND the Confluence formatting guide as reference context.

## How to Use in Different Tools

Fragments are rendered as a separate "Fragments" section in the generated output, distinct from rules. They appear after the prompt/constraints sections and provide reference material that agents can consult during their work.

### Any Tool

Fragments are automatically bundled into agents, prompts, and skills when you build:

```bash
npm run build
# or
./gradlew clean build  # for the Kotlin engine
```

## Creating Your Own Fragments

### Basic Fragment

```yaml
# 02_fragments/my-style-guide.yml
id: my-style-guide
description: Team style guide for documentation
content: |
  Use sentence case for headings.
  Prefer active voice over passive voice.
  Keep paragraphs to 3-5 sentences.
  Use numbered lists for sequential steps.
  Use bullet points for unordered items.
metadata:
  version: 1.0.0
  author: 'AI Tools Team'
  created: '2026-04-03'
  tags:
    - documentation
    - style
```

### Fragment Schema

| Field         | Required | Description                                    |
|---------------|----------|------------------------------------------------|
| `id`          | Yes      | Unique identifier for the fragment             |
| `description` | Yes      | One-line description of the fragment           |
| `content`     | Yes      | Free-form content (reference material, guides) |
| `metadata`    | Yes      | Version, author, dates, and tags               |

### Best Practices

**Good fragments:**

```yaml
content: |
  Use headings (h1-h6) to structure content hierarchically.
  Use tables for structured data comparison.
  Use code blocks with language hints for code snippets.
```

**Bad fragments:**

```yaml
content: |
  Write good docs.
```

**Fragments should be:**

- Specific and informative
- Self-contained (can be understood without additional context)
- Focused on one topic or concept
- Written as reference material, not as commands

## Related

- [Rulesets](../01_rulesets/README.md) - Behavioral constraints for agents
- [Agents](../05_agents/README.md) - Use fragments to provide context to agents
- [Prompts](../03_prompts/README.md) - Combine fragments with prompts for richer output
- [Skills](../04_skills/README.md) - Attach reference material to skills
