# AI Tools Documentation

> Auto-generated documentation for agents, prompts, and skills

_Last updated: 2025-11-15_

## Table of Contents

- [Agents](#agents)
- [Prompts](#prompts)
- [Skills](#skills)

## Agents

Agents are complete AI assistants with specific purposes, configured with rulepacks, tools, and capabilities.

### `bug-fixer`

**Version:** 1.0.0

**Purpose:** Diagnose and fix bugs in code

An agent specialized in debugging and fixing issues in code.
Analyzes error messages, traces, and code to identify root causes
and propose fixes.


**Rulepacks:**
- `base`
- `coding-python`
- `coding-kotlin`

**Required Capabilities:**
- `mcp:filesystem`
- `mcp:shell`
- `mcp:git`

**Tools:**
- `read-file`
- `run-tests`
- `search-code`

**Default Settings:**
```yaml
temperature: 0.3
style: technical
```

---

### `code-reviewer`

**Version:** 2.1.0

**Purpose:** Structured code review with actionable findings

A senior code reviewer agent that analyzes code changes, identifies issues,
and provides constructive, actionable feedback.


**Rulepacks:**
- `base`
- `reviewer`
- `security`

**Required Capabilities:**
- `mcp:git`
- `mcp:filesystem`

**Tools:**
- `git-diff`
- `read-file`
- `search-code`

**Default Settings:**
```yaml
temperature: 0.2
model: claude-3-5-sonnet
style: terse
```

---

### `documentation-writer`

**Version:** 1.0.0

**Purpose:** Create and maintain comprehensive project documentation

A documentation specialist agent that writes clear, accurate technical documentation
including API docs, guides, READMEs, and architecture documentation following best
practices for technical writing.


**Rulepacks:**
- `base`

**Required Capabilities:**
- `mcp:filesystem`
- `mcp:git`

**Tools:**
- `read-file`
- `search-code`

**Default Settings:**
```yaml
temperature: 0.5
model: claude-3-5-sonnet
style: conversational
```

---

### `feature-builder`

**Version:** 1.0.0

**Purpose:** Implement new features from requirements

An agent specialized in building new features from scratch.
Takes requirements, designs the implementation, writes code,
and creates tests following best practices.


**Rulepacks:**
- `base`
- `coding-python`
- `coding-kotlin`
- `coding-typescript`
- `coding-java`

**Required Capabilities:**
- `mcp:filesystem`
- `mcp:shell`
- `mcp:git`

**Tools:**
- `read-file`
- `search-code`
- `run-tests`

**Default Settings:**
```yaml
temperature: 0.4
style: technical
```

---

### `kotlin-style-enforcer`

**Version:** 1.0.0

**Purpose:** Enforce Kotlin coding standards and idioms

An agent that reviews Kotlin code for style violations and
suggests idiomatic improvements.


**Rulepacks:**
- `base`
- `coding-kotlin`

**Required Capabilities:**
- `mcp:filesystem`

**Tools:**
- `run-detekt`
- `run-ktlint`

**Default Settings:**
```yaml
temperature: 0.2
style: terse
```

---

### `project-planner`

**Version:** 1.0.0

**Purpose:** Design system architecture and plan project implementation

A strategic planning agent that helps with architectural decisions, project planning,
feature roadmaps, and technical design. Breaks down complex projects into manageable
phases with clear milestones and dependencies.


**Rulepacks:**
- `base`

**Required Capabilities:**
- `mcp:filesystem`
- `mcp:git`

**Tools:**
- `read-file`
- `search-code`

**Default Settings:**
```yaml
temperature: 0.6
model: claude-3-5-sonnet
style: technical
```

---

### `prompt-engineer`

**Version:** 1.0.0

**Purpose:** Design and create high-quality prompts and agents for the ai-tools repository

A specialized agent for crafting well-structured prompts and agent manifests.
Expert in prompt engineering patterns, agent design, and the ai-tools manifest system.
Creates atomic, reusable prompts with proper variables, includes, and metadata.
Designs agents with clear purposes, appropriate rulepacks, and effective system prompts.


**Rulepacks:**
- `base`

**Required Capabilities:**
- `mcp:filesystem`
- `mcp:git`

**Tools:**
- `read-file`
- `search-code`
- `validate-schema`

**Default Settings:**
```yaml
temperature: 0.6
model: claude-3-5-sonnet
style: technical
```

---

### `refactoring-specialist`

**Version:** 1.0.0

**Purpose:** Improve code quality through strategic refactoring

An expert refactoring agent that identifies code smells, suggests improvements,
and performs safe, behavior-preserving transformations to enhance maintainability,
readability, and performance.


**Rulepacks:**
- `base`
- `coding-python`
- `coding-kotlin`
- `coding-typescript`
- `coding-java`

**Required Capabilities:**
- `mcp:filesystem`
- `mcp:git`
- `mcp:shell`

**Tools:**
- `read-file`
- `search-code`
- `git-diff`
- `run-tests`

**Default Settings:**
```yaml
temperature: 0.3
model: claude-3-5-sonnet
style: technical
```

---

### `tdd-navigator`

**Version:** 1.0.0

**Purpose:** Guide test-driven development workflow

An agent that helps with test-driven development by writing tests first,
then guiding implementation to make tests pass.


**Rulepacks:**
- `base`
- `coding-python`
- `coding-kotlin`

**Required Capabilities:**
- `mcp:filesystem`
- `mcp:shell`

**Tools:**
- `run-tests`
- `write-code`
- `refactor`

**Default Settings:**
```yaml
temperature: 0.4
style: conversational
```

---

### `ticket-manager`

**Version:** 1.0.0

**Purpose:** Create and manage development tickets with proper breakdown

A ticket management specialist that creates well-structured issues, breaks down
epics into stories, defines acceptance criteria, and helps with sprint planning
and backlog refinement.


**Rulepacks:**
- `base`

**Required Capabilities:**
- `mcp:filesystem`
- `mcp:git`

**Tools:**
- `read-file`
- `search-code`

**Default Settings:**
```yaml
temperature: 0.4
model: claude-3-5-sonnet
style: technical
```

---

## Prompts

Prompts are atomic, reusable templates with variables and clear specifications.

### Adr

#### `tech-decision-record`

**Version:** 1.0.0  
Create an Architecture Decision Record (ADR) for a technical decision

**Variables:**
- `decision` (required)
  The technical decision to document
- `context` (optional)
  Background and context for the decision
- `alternatives` (optional)
  Alternative approaches considered

### Agile

#### `create-user-story`

**Version:** 1.0.0  
Create a well-structured user story with acceptance criteria

**Variables:**
- `feature` (required)
  The feature or functionality to create a story for
- `user_type` (optional)
  Type of user (e.g., admin, customer, developer)
- `context` (optional)
  Additional context or requirements

### Api

#### `document-api`

**Version:** 1.0.0  
Generate comprehensive API documentation

**Variables:**
- `code` (required)
  The API code to document
- `language` (required)
  Programming language
- `doc_style` (optional)
  Documentation style (javadoc, sphinx, jsdoc, etc.)

### Architecture

#### `design-architecture`

**Version:** 1.0.0  
Design system architecture for a new feature or project

**Variables:**
- `requirements` (required)
  Feature or project requirements
- `constraints` (optional)
  Technical or business constraints
- `existing_system` (optional)
  Description of existing system to integrate with

#### `explain-architecture`

**Version:** 1.0.0  
Document system architecture with diagrams

**Variables:**
- `system` (required)
  The system or component to document
- `context` (optional)
  Codebase or system context

### Backlog

#### `prioritize-backlog`

**Version:** 1.0.0  
Prioritize and organize a product backlog

**Variables:**
- `backlog_items` (required)
  List of backlog items to prioritize
- `business_goals` (optional)
  Current business priorities
- `team_capacity` (optional)
  Team size and velocity

### Boilerplate

#### `scaffold-feature`

**Version:** 1.0.0  
Generate boilerplate code structure for a new feature

**Variables:**
- `feature_name` (required)
  Name of the feature to scaffold
- `language` (optional)
  Programming language (auto-detected if not specified)
- `components` (optional)
  Components needed (e.g., service, controller, model, tests)

### Bug

#### `create-bug-report`

**Version:** 1.0.0  
Create a detailed bug report with reproduction steps

**Variables:**
- `bug_description` (required)
  Description of the bug
- `steps_to_reproduce` (optional)
  How to reproduce the bug
- `expected_behavior` (optional)
  What should happen
- `actual_behavior` (optional)
  What actually happens

### Complexity

#### `simplify-conditionals`

**Version:** 1.0.0  
Simplify complex conditional logic for better readability

**Variables:**
- `code` (required)
  The code block containing complex conditionals
- `language` (required)
  Programming language of the code

### Conditionals

#### `simplify-conditionals`

**Version:** 1.0.0  
Simplify complex conditional logic for better readability

**Variables:**
- `code` (required)
  The code block containing complex conditionals
- `language` (required)
  Programming language of the code

### Decision

#### `tech-decision-record`

**Version:** 1.0.0  
Create an Architecture Decision Record (ADR) for a technical decision

**Variables:**
- `decision` (required)
  The technical decision to document
- `context` (optional)
  Background and context for the decision
- `alternatives` (optional)
  Alternative approaches considered

### Design

#### `design-architecture`

**Version:** 1.0.0  
Design system architecture for a new feature or project

**Variables:**
- `requirements` (required)
  Feature or project requirements
- `constraints` (optional)
  Technical or business constraints
- `existing_system` (optional)
  Description of existing system to integrate with

#### `explain-architecture`

**Version:** 1.0.0  
Document system architecture with diagrams

**Variables:**
- `system` (required)
  The system or component to document
- `context` (optional)
  Codebase or system context

### Docs

#### `create-tutorial`

**Version:** 1.0.0  
Write a step-by-step tutorial for a specific task

**Variables:**
- `task` (required)
  The task to create a tutorial for
- `audience` (optional)
  Target audience (beginner, intermediate, advanced)
- `context` (optional)
  Additional context or requirements

#### `document-api`

**Version:** 1.0.0  
Generate comprehensive API documentation

**Variables:**
- `code` (required)
  The API code to document
- `language` (required)
  Programming language
- `doc_style` (optional)
  Documentation style (javadoc, sphinx, jsdoc, etc.)

#### `explain-architecture`

**Version:** 1.0.0  
Document system architecture with diagrams

**Variables:**
- `system` (required)
  The system or component to document
- `context` (optional)
  Codebase or system context

#### `summarize-pr`

**Version:** 1.0.0  
Generate a concise summary of a pull request

**Variables:**
- `diff` (required)
  The git diff to summarize
- `context` (optional)
  Additional context about the PR

#### `write-readme`

**Version:** 1.0.0  
Create a comprehensive README.md for a project

**Variables:**
- `project_name` (required)
  Name of the project
- `project_description` (required)
  What the project does
- `tech_stack` (optional)
  Technologies used
- `installation_steps` (optional)
  How to install/setup
- `usage_examples` (optional)
  How to use the project

### Dry

#### `remove-duplication`

**Version:** 1.0.0  
Identify and eliminate code duplication (DRY principle)

**Variables:**
- `code` (required)
  The code containing duplication
- `language` (required)
  Programming language of the code
- `context` (optional)
  Additional context about the codebase

### Duplication

#### `remove-duplication`

**Version:** 1.0.0  
Identify and eliminate code duplication (DRY principle)

**Variables:**
- `code` (required)
  The code containing duplication
- `language` (required)
  Programming language of the code
- `context` (optional)
  Additional context about the codebase

### Epic

#### `break-down-epic`

**Version:** 1.0.0  
Break down an epic into smaller, manageable user stories

**Variables:**
- `epic` (required)
  The epic to break down
- `constraints` (optional)
  Team size, timeline, or other constraints

### Error-handling

#### `add-error-handling`

**Version:** 1.0.0  
Add comprehensive error handling and validation

**Variables:**
- `code` (required)
  The code needing error handling
- `language` (required)
  Programming language of the code

### Estimation

#### `estimate-effort`

**Version:** 1.0.0  
Estimate development effort for a feature or project

**Variables:**
- `feature` (required)
  Feature or task to estimate
- `context` (optional)
  Codebase or team context
- `team_experience` (optional)
  Team familiarity with required tech

### Feature

#### `scaffold-feature`

**Version:** 1.0.0  
Generate boilerplate code structure for a new feature

**Variables:**
- `feature_name` (required)
  Name of the feature to scaffold
- `language` (optional)
  Programming language (auto-detected if not specified)
- `components` (optional)
  Components needed (e.g., service, controller, model, tests)

### Getting-started

#### `write-readme`

**Version:** 1.0.0  
Create a comprehensive README.md for a project

**Variables:**
- `project_name` (required)
  Name of the project
- `project_description` (required)
  What the project does
- `tech_stack` (optional)
  Technologies used
- `installation_steps` (optional)
  How to install/setup
- `usage_examples` (optional)
  How to use the project

### Git

#### `summarize-pr`

**Version:** 1.0.0  
Generate a concise summary of a pull request

**Variables:**
- `diff` (required)
  The git diff to summarize
- `context` (optional)
  Additional context about the PR

### Guide

#### `create-tutorial`

**Version:** 1.0.0  
Write a step-by-step tutorial for a specific task

**Variables:**
- `task` (required)
  The task to create a tutorial for
- `audience` (optional)
  Target audience (beginner, intermediate, advanced)
- `context` (optional)
  Additional context or requirements

### Issue

#### `create-bug-report`

**Version:** 1.0.0  
Create a detailed bug report with reproduction steps

**Variables:**
- `bug_description` (required)
  Description of the bug
- `steps_to_reproduce` (optional)
  How to reproduce the bug
- `expected_behavior` (optional)
  What should happen
- `actual_behavior` (optional)
  What actually happens

### Java

#### `extract-method`

**Version:** 1.3.0  
Extract a pure function from a selected block of code

**Variables:**
- `code` (required)
  The code block to extract
- `target_name` (optional)
  Desired name for the extracted method
- `language` (required)
  Programming language of the code

### Kotlin

#### `add-null-safety`

**Version:** 1.0.0  
Add null safety checks to code that may have null pointer issues

**Variables:**
- `code` (required)
  The code to make null-safe
- `language` (required)
  Programming language

#### `extract-method`

**Version:** 1.3.0  
Extract a pure function from a selected block of code

**Variables:**
- `code` (required)
  The code block to extract
- `target_name` (optional)
  Desired name for the extracted method
- `language` (required)
  Programming language of the code

### Naming

#### `improve-naming`

**Version:** 1.0.0  
Rename variables, functions, and classes for better clarity

**Variables:**
- `code` (required)
  The code with unclear names
- `language` (required)
  Programming language of the code
- `context` (optional)
  Business/domain context

### Planning

#### `create-implementation-plan`

**Version:** 1.0.0  
Break down a project into phases with tasks and timeline

**Variables:**
- `project` (required)
  Project description
- `team_size` (optional)
  Number of developers
- `timeline` (optional)
  Desired completion timeline
- `priorities` (optional)
  Must-have vs nice-to-have features

#### `design-architecture`

**Version:** 1.0.0  
Design system architecture for a new feature or project

**Variables:**
- `requirements` (required)
  Feature or project requirements
- `constraints` (optional)
  Technical or business constraints
- `existing_system` (optional)
  Description of existing system to integrate with

#### `estimate-effort`

**Version:** 1.0.0  
Estimate development effort for a feature or project

**Variables:**
- `feature` (required)
  Feature or task to estimate
- `context` (optional)
  Codebase or team context
- `team_experience` (optional)
  Team familiarity with required tech

#### `tech-decision-record`

**Version:** 1.0.0  
Create an Architecture Decision Record (ADR) for a technical decision

**Variables:**
- `decision` (required)
  The technical decision to document
- `context` (optional)
  Background and context for the decision
- `alternatives` (optional)
  Alternative approaches considered

### Pr

#### `summarize-pr`

**Version:** 1.0.0  
Generate a concise summary of a pull request

**Variables:**
- `diff` (required)
  The git diff to summarize
- `context` (optional)
  Additional context about the PR

### Prioritization

#### `prioritize-backlog`

**Version:** 1.0.0  
Prioritize and organize a product backlog

**Variables:**
- `backlog_items` (required)
  List of backlog items to prioritize
- `business_goals` (optional)
  Current business priorities
- `team_capacity` (optional)
  Team size and velocity

### Python

#### `extract-method`

**Version:** 1.3.0  
Extract a pure function from a selected block of code

**Variables:**
- `code` (required)
  The code block to extract
- `target_name` (optional)
  Desired name for the extracted method
- `language` (required)
  Programming language of the code

### Qa

#### `write-tests`

**Version:** 1.0.0  
Generate comprehensive unit tests for given code

**Variables:**
- `code` (required)
  The code to test
- `language` (required)
  Programming language
- `test_framework` (optional)
  Testing framework to use (e.g., pytest, junit, jest)

### Readability

#### `improve-naming`

**Version:** 1.0.0  
Rename variables, functions, and classes for better clarity

**Variables:**
- `code` (required)
  The code with unclear names
- `language` (required)
  Programming language of the code
- `context` (optional)
  Business/domain context

### Readme

#### `write-readme`

**Version:** 1.0.0  
Create a comprehensive README.md for a project

**Variables:**
- `project_name` (required)
  Name of the project
- `project_description` (required)
  What the project does
- `tech_stack` (optional)
  Technologies used
- `installation_steps` (optional)
  How to install/setup
- `usage_examples` (optional)
  How to use the project

### Refactor

#### `add-error-handling`

**Version:** 1.0.0  
Add comprehensive error handling and validation

**Variables:**
- `code` (required)
  The code needing error handling
- `language` (required)
  Programming language of the code

#### `add-null-safety`

**Version:** 1.0.0  
Add null safety checks to code that may have null pointer issues

**Variables:**
- `code` (required)
  The code to make null-safe
- `language` (required)
  Programming language

#### `extract-method`

**Version:** 1.3.0  
Extract a pure function from a selected block of code

**Variables:**
- `code` (required)
  The code block to extract
- `target_name` (optional)
  Desired name for the extracted method
- `language` (required)
  Programming language of the code

#### `improve-naming`

**Version:** 1.0.0  
Rename variables, functions, and classes for better clarity

**Variables:**
- `code` (required)
  The code with unclear names
- `language` (required)
  Programming language of the code
- `context` (optional)
  Business/domain context

#### `remove-duplication`

**Version:** 1.0.0  
Identify and eliminate code duplication (DRY principle)

**Variables:**
- `code` (required)
  The code containing duplication
- `language` (required)
  Programming language of the code
- `context` (optional)
  Additional context about the codebase

#### `simplify-conditionals`

**Version:** 1.0.0  
Simplify complex conditional logic for better readability

**Variables:**
- `code` (required)
  The code block containing complex conditionals
- `language` (required)
  Programming language of the code

### Reference

#### `document-api`

**Version:** 1.0.0  
Generate comprehensive API documentation

**Variables:**
- `code` (required)
  The API code to document
- `language` (required)
  Programming language
- `doc_style` (optional)
  Documentation style (javadoc, sphinx, jsdoc, etc.)

### Roadmap

#### `create-implementation-plan`

**Version:** 1.0.0  
Break down a project into phases with tasks and timeline

**Variables:**
- `project` (required)
  Project description
- `team_size` (optional)
  Number of developers
- `timeline` (optional)
  Desired completion timeline
- `priorities` (optional)
  Must-have vs nice-to-have features

### Robustness

#### `add-error-handling`

**Version:** 1.0.0  
Add comprehensive error handling and validation

**Variables:**
- `code` (required)
  The code needing error handling
- `language` (required)
  Programming language of the code

### Safety

#### `add-null-safety`

**Version:** 1.0.0  
Add null safety checks to code that may have null pointer issues

**Variables:**
- `code` (required)
  The code to make null-safe
- `language` (required)
  Programming language

### Scaffold

#### `scaffold-feature`

**Version:** 1.0.0  
Generate boilerplate code structure for a new feature

**Variables:**
- `feature_name` (required)
  Name of the feature to scaffold
- `language` (optional)
  Programming language (auto-detected if not specified)
- `components` (optional)
  Components needed (e.g., service, controller, model, tests)

### Sizing

#### `estimate-effort`

**Version:** 1.0.0  
Estimate development effort for a feature or project

**Variables:**
- `feature` (required)
  Feature or task to estimate
- `context` (optional)
  Codebase or team context
- `team_experience` (optional)
  Team familiarity with required tech

### Story-breakdown

#### `break-down-epic`

**Version:** 1.0.0  
Break down an epic into smaller, manageable user stories

**Variables:**
- `epic` (required)
  The epic to break down
- `constraints` (optional)
  Team size, timeline, or other constraints

### Tdd

#### `write-tests`

**Version:** 1.0.0  
Generate comprehensive unit tests for given code

**Variables:**
- `code` (required)
  The code to test
- `language` (required)
  Programming language
- `test_framework` (optional)
  Testing framework to use (e.g., pytest, junit, jest)

### Testing

#### `write-tests`

**Version:** 1.0.0  
Generate comprehensive unit tests for given code

**Variables:**
- `code` (required)
  The code to test
- `language` (required)
  Programming language
- `test_framework` (optional)
  Testing framework to use (e.g., pytest, junit, jest)

### Tickets

#### `break-down-epic`

**Version:** 1.0.0  
Break down an epic into smaller, manageable user stories

**Variables:**
- `epic` (required)
  The epic to break down
- `constraints` (optional)
  Team size, timeline, or other constraints

#### `create-bug-report`

**Version:** 1.0.0  
Create a detailed bug report with reproduction steps

**Variables:**
- `bug_description` (required)
  Description of the bug
- `steps_to_reproduce` (optional)
  How to reproduce the bug
- `expected_behavior` (optional)
  What should happen
- `actual_behavior` (optional)
  What actually happens

#### `create-user-story`

**Version:** 1.0.0  
Create a well-structured user story with acceptance criteria

**Variables:**
- `feature` (required)
  The feature or functionality to create a story for
- `user_type` (optional)
  Type of user (e.g., admin, customer, developer)
- `context` (optional)
  Additional context or requirements

#### `prioritize-backlog`

**Version:** 1.0.0  
Prioritize and organize a product backlog

**Variables:**
- `backlog_items` (required)
  List of backlog items to prioritize
- `business_goals` (optional)
  Current business priorities
- `team_capacity` (optional)
  Team size and velocity

### Timeline

#### `create-implementation-plan`

**Version:** 1.0.0  
Break down a project into phases with tasks and timeline

**Variables:**
- `project` (required)
  Project description
- `team_size` (optional)
  Number of developers
- `timeline` (optional)
  Desired completion timeline
- `priorities` (optional)
  Must-have vs nice-to-have features

### Tutorial

#### `create-tutorial`

**Version:** 1.0.0  
Write a step-by-step tutorial for a specific task

**Variables:**
- `task` (required)
  The task to create a tutorial for
- `audience` (optional)
  Target audience (beginner, intermediate, advanced)
- `context` (optional)
  Additional context or requirements

### User-story

#### `create-user-story`

**Version:** 1.0.0  
Create a well-structured user story with acceptance criteria

**Variables:**
- `feature` (required)
  The feature or functionality to create a story for
- `user_type` (optional)
  Type of user (e.g., admin, customer, developer)
- `context` (optional)
  Additional context or requirements

---

## Skills

Skills are executable tools and commands that agents can use to perform specific tasks.

### Detekt

#### `run-detekt`

**Version:** 1.0.0  
Run Detekt static analysis for Kotlin

### Filesystem

#### `search-repo`

**Version:** 1.0.0  
Search repository for code patterns or text

### Formatting

#### `run-ktlint`

**Version:** 1.0.0  
Run ktlint for Kotlin code formatting

### Gradle

#### `run-gradle-tests`

**Version:** 1.0.0  
Run unit tests with Gradle

### Java

#### `run-gradle-tests`

**Version:** 1.0.0  
Run unit tests with Gradle

### Kotlin

#### `run-detekt`

**Version:** 1.0.0  
Run Detekt static analysis for Kotlin

#### `run-gradle-tests`

**Version:** 1.0.0  
Run unit tests with Gradle

#### `run-ktlint`

**Version:** 1.0.0  
Run ktlint for Kotlin code formatting

### Ktlint

#### `run-ktlint`

**Version:** 1.0.0  
Run ktlint for Kotlin code formatting

### Lint

#### `run-detekt`

**Version:** 1.0.0  
Run Detekt static analysis for Kotlin

### Pytest

#### `run-pytest`

**Version:** 1.0.0  
Run Python tests with pytest

### Python

#### `run-pytest`

**Version:** 1.0.0  
Run Python tests with pytest

### Search

#### `search-repo`

**Version:** 1.0.0  
Search repository for code patterns or text

### Static-analysis

#### `run-detekt`

**Version:** 1.0.0  
Run Detekt static analysis for Kotlin

### Testing

#### `run-gradle-tests`

**Version:** 1.0.0  
Run unit tests with Gradle

#### `run-pytest`

**Version:** 1.0.0  
Run Python tests with pytest
