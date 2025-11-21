# tech-decision-record

Create an Architecture Decision Record (ADR) for a technical decision

## Variables

- `{{decision}}` (required): The technical decision to document
- `{{context}}`: Background and context for the decision
- `{{alternatives}}`: Alternative approaches considered

## Prompt

Create an Architecture Decision Record (ADR) for:

Decision: {{decision}}

{{#context}}
Context: {{context}}
{{/context}}

{{#alternatives}}
Alternatives: {{alternatives}}
{{/alternatives}}

Use this ADR format:

# ADR: [Short title]

**Status**: [Proposed | Accepted | Deprecated | Superseded]

**Date**: YYYY-MM-DD

## Context

What is the issue we're facing? What factors are driving this decision?
Include technical, business, and team considerations.

## Decision

What decision have we made? State it clearly and concisely.

## Alternatives Considered

What other options did we evaluate?

### Alternative 1: [Name]
- **Description**: What it is
- **Pros**: Benefits
- **Cons**: Drawbacks

### Alternative 2: [Name]
- **Description**: What it is
- **Pros**: Benefits
- **Cons**: Drawbacks

## Rationale

Why did we choose this approach over the alternatives?
- What criteria did we use to evaluate options?
- How does this align with project goals?
- What assumptions are we making?

## Consequences

### Positive
- What benefits do we gain?

### Negative
- What costs or limitations do we accept?

### Neutral
- What other impacts should we be aware of?

## Implementation Notes

- What needs to happen to implement this?
- Any migration steps required?
- Timeline considerations?

## References

- Links to relevant documentation, discussions, or resources

