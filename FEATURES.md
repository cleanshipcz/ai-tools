# Features & Capabilities (Legacy to Kotlin Migration)

This document outlines the **functional capabilities** (what the system does from a user's perspective) of the legacy `ai-tools` repository. It serves as the requirements document for the new Kotlin-based Engine (`ai-tools-engine`).

## 1. Unified Configuration Management
The core feature of the system is the ability to define AI behavior in a tool-agnostic way and deploy it anywhere.

### 1.1. Manifest-Driven Definition
Users define their AI setup using simple YAML files in a standard directory structure (`rulesets/`, `agents/`, `prompts/`).
*   **Feature**: Write configurations once, apply them to multiple AI tools (Windsurf, Cursor, Copilot, Claude).
*   **Feature**: Modularize configurations into reusable parts (e.g., a "Java coding style" ruleset can be shared across 10 different projects).

### 1.2. Intelligent Ruleset Resolution
The system doesn't just copy files; it actively "compiles" the best context for a given situation.
*   **Recursive Inheritance**: Users can create a hierarchy of rules (e.g., `company-standards` -> `team-standards` -> `project-rules`). The system automatically merges them.
*   **Context-Aware Filtering**:
    *   **By Tech Stack**: If a project uses Java, the system automatically includes Java-related rules and excludes irrelevant ones (like Python rules), keeping the AI focused.
    *   **By Scope**: Global agents get a clean, general context, while stack-specific agents (e.g., "Backend Developer") get the full technical details.

### 1.3. Hierarchical Model Selection
Users can specify which AI model to use at different levels of granularity. The system automatically picks the most specific one:
1.  **Feature Level**: "This specific feature refactor needs GPT-5."
2.  **Project Level**: "This entire legacy project requires Claude 3 Opus."
3.  **Agent Level**: "The 'Senior Architect' agent defaults to GPT-4."
4.  **Global Default**: Fallback for everything else.

---

## 2. Multi-Tool Support (The "Deploy Anywhere" Promise)
The system acts as a universal adapter, translating the unified configuration into the native formats of popular AI tools.

### 2.1. Windsurf Integration
*   Generates `.windsurf/rules/` and system prompts required for the IDE.
*   **Feature**: Compiles "Feature" manifests into Windsurf Workflows (`.windsurf/workflows/`) for automated task execution.
*   **Feature**: Marks generated rules as `trigger: manual` to give users control over when they apply.

### 2.2. Cursor Integration
*   Generates the `.cursor/project-rules.json` file which drives Cursor's AI behavior.
*   **Feature**: Automatically merges coding conventions, naming patterns, and file structure rules into the main project rules.
*   **Feature**: Creates "Recipes" (saved prompts) for every agent, specialized for different tech stacks (e.g., creating a `developer-backend` recipe).

### 2.3. GitHub Copilot, Claude, & Codex
*   **Copilot**: Generates Markdown instruction files (`.github/instructions/`) that Copilot chat can read.
*   **Claude Code**: Generates native prompt JSON configurations and compatible skill files (`SKILL.md`).
*   **Codex**: Manages user-global prompts by deploying them directly to the user's home directory (`~/.codex/prompts`).

---

## 3. Interactive Workflows (Recipes)
The system provides an engine to run complex, multi-step AI tasks rooted in best practices.

### 3.1. Interactive CLI Runner
Users can run a recipe (e.g., "Refactor Module") from the command line.
*   **Feature**: Step-by-step guidance where the AI performs a task, and the user verifies it.
*   **Feature**: Can "loop" over a task (e.g., "Fix errors until tests pass" with a max iteration limit).
*   **Feature**: Dynamic Context injection – automatically pulls in relevant reference documents or file contents for each step.

### 3.2. Portable Script Generation
Users can "compile" a recipe into a standalone Bash script.
*   **Feature**: The generated script runs anywhere (CI/CD, other dev machines) without needing the full AI Tools engine installed.
*   **Feature**: Scripts are context-aware, automatically finding the project's rules and context files at runtime.

---

## 4. Project Life-Cycle & Workspace
Features relating to how projects are created, tracked, and documented.

### 4.1. External Project Linking
Users don't need to move their code into the `ai-tools` repo.
*   **Feature**: "Link" any folder on your file system as a managed project.
*   **Feature**: Maintains a registry of these links (Global vs. Local scope), creating a powerful "Command Center" for all your AI-enabled projects.

### 4.2. Templated Scaffolding
*   **Feature**: `project:create` command generates a new project structure using a "Golden Template".
*   **Feature**: Automates standard boilerplate (Git, rulesets, basic config) so every new project starts with Best Practices built-in.

### 4.3. Auto-Documentation
*   **Feature**: Generates a static `docs/AGENTS.md` manual.
*   **Feature**: Automatically groups and indexes every Agent, Skill, and Prompt available in the system, creating a "Self-Documenting Capability" that never goes stale.

---

## 5. Deployment Pipeline
The system manages the lifecycle of AI configurations in a target project.

### 5.1. Safe Deployment
*   **Feature**: "Clean Room" generation ensure no stale or conflicting configurations remain from previous deployments.
*   **Feature**: Diff Engine – shows a detailed line-by-line comparison of what will change before you apply it.
*   **Feature**: Automated Backups – before overwriting anything, the system saves a timestamped backup of the existing configuration.
*   **Feature**: Rotation – automatically effectively manages disk space by keeping only the 10 most recent backups.

### 5.2. Validation & Security
*   **Feature**: Secrets Scanning – prevents accidental commits of API keys or tokens by scanning all configurations before deployment.
*   **Feature**: Input Sanitization – ensures that generated scripts are safe from command injection vulnerabilities (e.g., ensuring no backticks are present in strict fields).

---

## 6. Prompt Management System
A suite of tools designed to make Prompts accessible to non-technical team members.

### 6.1. Web-Based Library (Single-Page App)
*   **Feature**: Generates a self-contained `PROMPT_LIBRARY.html` file that works offline in any browser.
*   **Feature**: **Live Search & Filter** – instantly find prompts by tags, category, or description.
*   **Feature**: **Interactive Form Filler** – a UI that generates input fields for every `{{variable}}` in a prompt. Users fill the form, and the preview updates in real-time.
*   **Feature**: **One-Click Copy** – format-perfect copying of the final prompt for pasting into LLMs.

### 6.2. CLI Prompt Manager
*   **Feature**: `prompts use <id>` interactive mode in the terminal.
*   **Feature**: Steps the user through required variables one by one.
*   **Feature**: Validators – ensures required variables are not skipped before generating the output.

---

## 7. Skills System
An abstraction for defining "Tools" that AI agents can use.
*   **Feature**: Define a skill (like "Run Tests" or "Query Database") once in YAML.
*   **Feature**: Automatically generate the necessary adapters (e.g., `SKILL.md` for Claude) so the AI knows how to call it.
*   **Feature**: Supports wrapping MCP (Model Context Protocol) tools for seamless integration.
