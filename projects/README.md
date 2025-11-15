# Projects: Project-Specific AI Tool Configurations

> **Generate and deploy project-specific AI tool configurations**

This guide explains how to use the Projects feature to create customized AI tool configurations for your specific projects.

---

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Concepts](#concepts)
- [Creating Projects](#creating-projects)
- [Generating Configurations](#generating-configurations)
- [Deploying to Projects](#deploying-to-projects)
- [Project Manifest Reference](#project-manifest-reference)
- [Commands](#commands)
- [Features System](#features-system)
- [External Projects](#external-projects)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Examples](#examples)

---

## Overview

The Projects feature allows you to:

1. **Define project-specific context** - Tech stack, conventions, commands, documentation
2. **Generate tool-specific configs** - Customized for GitHub Copilot, Windsurf, Cursor, Claude Code
3. **Deploy automatically** - Push configurations to your project repositories
4. **Version control** - Track changes to your project configurations

### Why Use Projects?

- **Context-aware AI**: AI tools understand your project's specific requirements
- **Consistent conventions**: Enforce project-specific naming, patterns, testing standards
- **Easy updates**: Update once, deploy everywhere
- **Team alignment**: Share project configurations across the team

---

## Quick Start

### 1. Create a New Project

```bash
# Create a local project (private, not versioned)
npm run project:create my-project -- --description "My awesome project"

# Create a global project (versioned, shared with team)
npm run project:create my-api -- --global -d "Company REST API"
```

This will:
- Create the project directory
- Copy template files (`project.yml`, `deploy.yml`)
- Set up initial configuration

### 2. Configure Your Project

Edit the generated files:

```bash
# Edit project details
code projects/local/my-project/project.yml

# Edit deployment settings
code projects/local/my-project/deploy.yml

# For local overrides (gitignored)
code projects/local/my-project/deploy.local.yml
```

### 3. Deploy to Your Project

```bash
# Deploy automatically generates and copies files
npm run project:deploy my-project

# Dry run to preview
npm run project:deploy my-project -- --dry-run

# Skip confirmation prompt
npm run project:deploy my-project -- --no-confirm
```

### 4. List Projects

```bash
npm run project:list
```

You'll see:
- **Global projects** - Shared, versioned projects in `projects/global/`
- **Local projects** - Your private projects in `projects/local/`

---

## Concepts

### Global vs Local Projects

**Global Projects** (`projects/global/`)
- Versioned in git
- Shared across the team
- Examples and templates
- Use for: Reference implementations, team standards

**Local Projects** (`projects/local/`)
- Gitignored (private)
- User-specific
- Use for: Personal projects, client work, experiments

### Project Manifest

A YAML file (`project.yml`) containing:
- **Metadata**: ID, name, description, version
- **Context**: Overview, purpose
- **Tech Stack**: Languages, frameworks, tools
- **Documentation**: Links to docs
- **Commands**: Dev, build, test, deploy commands
- **Conventions**: Naming, patterns, testing rules
- **AI Tools Config**: Preferred agents, rulepacks, custom rules

### Generated Outputs

For each tool, the generator creates:

**GitHub Copilot**
- `.github/copilot-instructions.md` - Project-specific instructions

**Windsurf**
- `.windsurf/rules/project-rules.json` - Project rules

**Cursor**
- `.cursor/project-rules.json` - Project configuration

**Claude Code**
- `.claude/project-context.json` - Project context

### Deployment

### Deployment Process

The deployment automatically:

1. **Generates** outputs from project manifest (no separate step needed)
2. **Backs up** existing files (if `backup: true`)
3. **Copies** to target directory
4. **Commits** changes (if `auto_commit: true`)

---

## Creating Projects

### Using the Create Command (Recommended)

```bash
# Create a local project (private, gitignored)
npm run project:create my-project

# With description
npm run project:create my-project -- -d "Brief project description"

# Create a global project (versioned, shared)
npm run project:create my-api -- --global -d "Company REST API"
```

This automatically:

- Creates the project directory structure
- Generates `project.yml` from template with your project name
- Generates `deploy.yml` with deployment configuration
- Sets up proper metadata (creation date, etc.)

### Manual Creation

If you prefer to create projects manually:

```bash
# 1. Copy template
mkdir -p projects/local/my-project
cp projects/global/template/project.yml projects/local/my-project/
cp projects/global/template/deploy.yml projects/local/my-project/

# 2. Edit files
code projects/local/my-project/project.yml
code projects/local/my-project/deploy.yml

# 3. Validate
npm run validate
```

---

## Generating Configurations

**Note**: Generation now happens automatically during deployment. You rarely need to run this manually.

### Manual Generation (Optional)

```bash
# Generate for all tools
npm run project:generate my-project

# Generate for specific tools
npm run project:generate my-project github-copilot windsurf
```

Output is generated in `.output/<project-id>/` (gitignored):

- `.output/my-project/github-copilot/`
- `.output/my-project/windsurf/`
- `.output/my-project/cursor/`
- `.output/my-project/claude-code/`

### What Gets Generated?

#### GitHub Copilot

Generates `.github/copilot-instructions.md` containing:

- Project name and description
- Context (overview, purpose)
- Tech stack details
- Key commands (dev, build, test, etc.)
- Conventions (naming, patterns, testing)
- Project-specific rules
- Documentation references

#### Windsurf

Generates `.windsurf/rules/project-rules.json` with project-specific rules.

#### Cursor

Generates `.cursor/project-rules.json` with project rules and context.

#### Claude Code

Generates `.claude/project-context.json` with project context.

---

## Deploying to Projects

### Setup Deployment Configuration

Each project has its own `deploy.yml` file created during project creation.

1. Edit `projects/local/my-project/deploy.yml`:

   ```yaml
   # Target project directory (absolute or relative path)
   target: "../my-actual-project"
   
   # Tools to deploy
   tools:
     - github-copilot
     - windsurf
   
   # Deployment mode
   mode: local
   
   # Settings
   backup: true
   auto_commit: false
   ```

2. (Optional) Create local overrides in `deploy.local.yml` (gitignored):

   ```yaml
   # Override target with your local path
   target: /home/user/projects/my-project
   ```

3. Deploy:

   ```bash
   npm run project:deploy my-project
   ```

Deployment automatically generates outputs and copies them to your target project.

### Deployment Modes

**Local Mode** (`mode: local`)
- Copies files to target directory
- Can auto-commit if `auto_commit: true`
- Requires absolute path to project

**Manual Mode** (`mode: manual`)
- Generates files
- Shows instructions
- You copy manually

### Deployment Options

**Backup** (`backup: true`)
- Backs up existing files before deploying
- Stored in `.backups/<project>/<timestamp>/`
- Recommended: `true`

**Auto-commit** (`auto_commit: true`)
- Automatically commits changes
- Creates/uses branch specified in `git_branch`
- Requires `mode: local`
- Useful for automated workflows

### Deploy Commands

```bash
# Deploy specific project
npm run project:deploy my-project

# Deploy all configured projects
npm run project:deploy-all

# Dry run (preview what would happen)
npm run project:deploy my-project -- --dry-run

# Force deploy (skip confirmation)
npm run project:deploy my-project -- --force

# Dry run + check all projects
npm run project:deploy-all -- --dry-run
```

### Rollback (Experimental)

```bash
# Rollback to previous version
npm run project:rollback my-project

# Rollback to specific timestamp
npm run project:rollback my-project 2024-11-15T10-30-00
```

---

## Project Manifest Reference

### Required Fields

```yaml
id: my-project                  # Unique ID (kebab-case)
version: 1.0.0                  # Semantic version
name: "My Project"              # Human-readable name
description: "Brief description (10-500 chars)"
```

### Context (Optional but Recommended)

```yaml
context:
  overview: |
    Detailed project overview.
    Multiple lines supported.
  purpose: "Primary goal of the project"
```

### Tech Stack (Optional)

```yaml
tech_stack:
  languages:
    - typescript
    - python
  frontend:
    - react
    - next.js
  backend:
    - node.js
    - express
  database:
    - postgresql
  infrastructure:
    - docker
    - kubernetes
  tools:
    - jest
    - eslint
```

### Documentation (Optional)

```yaml
documentation:
  readme: "README.md"
  contributing: "CONTRIBUTING.md"
  architecture: "docs/ARCHITECTURE.md"
  code_style: "docs/CODE_STYLE.md"
  api_docs: "docs/API.md"
  custom:
    deployment: "docs/DEPLOYMENT.md"
```

### Commands (Optional)

```yaml
commands:
  dev:
    all: "npm run dev"
    frontend: "cd frontend && npm run dev"
  build:
    all: "npm run build"
  test:
    unit: "npm test"
    e2e: "npm run test:e2e"
```

### Conventions (Optional but Recommended)

```yaml
conventions:
  naming:
    - "Use PascalCase for React components"
    - "Use camelCase for functions"
  patterns:
    - "Prefer functional components"
    - "Use TypeScript strict mode"
  testing:
    - "Test coverage minimum 80%"
  structure:
    - "Follow feature-based structure"
  custom:
    - "Any other conventions"
```

### AI Tools Configuration (Optional but Recommended)

```yaml
ai_tools:
  preferred_agents:
    - code-reviewer
    - feature-builder
  preferred_rulepacks:
    - base
    - coding-typescript
    - security
  custom_rules:
    - "Always use our logger utility"
    - "API responses must follow standard format"
  excluded_agents:
    - kotlin-style-enforcer
  excluded_rulepacks:
    - coding-java
```

### Metadata (Optional)

```yaml
metadata:
  repository: "https://github.com/user/project"
  maintainers:
    - "email@example.com"
  created: "2024-01-15"
  updated: "2024-11-15"
  tags:
    - web
    - api
```

---

## Commands

### Project Creation

```bash
# Create a new local project (managed in this repo)
npm run project:create <name>

# Create with description
npm run project:create <name> -- -d "Description"

# Create a global project
npm run project:create <name> -- --global -d "Description"
```

### External Project Initialization

```bash
# Initialize AI tools in an external project
npm run project:init <path>

# With custom alias
npm run project:init <path> -- --alias <name>

# With description
npm run project:init <path> -- -d "Description"

# Register in global registry (shared with team)
npm run project:init <path> -- --register-global

# Initialize without auto-registering
npm run project:init <path> -- --no-register

# Complete example
npm run project:init /home/user/my-app -- --alias my-app -d "My application" --register-local
```

### Project Management

```bash
# List all projects
npm run project:list

# Validate project manifests
npm run validate
```

### Generation (Optional - happens automatically during deployment)

```bash
# Generate for all tools
npm run project:generate <project-id>

# Generate for specific tools
npm run project:generate <project-id> <tool1> <tool2>

# Example
npm run project:generate my-project github-copilot windsurf
```

### Deployment

```bash
# Deploy a project (auto-generates outputs)
npm run project:deploy <project-id>

# Dry run (preview changes)
npm run project:deploy <project-id> -- --dry-run

# Skip confirmation
npm run project:deploy <project-id> -- --no-confirm

# Dry run + skip confirmation
npm run project:deploy <project-id> -- --dry-run --no-confirm
```

### Rollback

```bash
# Rollback to previous version
npm run project:rollback <project-id>

# Rollback to specific backup
npm run project:rollback <project-id> <timestamp>
```

---

## Features System

The Features system allows you to define feature-specific context and code snippets within your projects.

### What Are Features?

Features are sub-components of your project that have their own:

- Context and documentation
- Architecture notes
- Code snippets and examples
- Conventions specific to that feature

### Creating Features

1. Create a features directory in your project:

   ```bash
   mkdir -p projects/local/my-project/features/user-authentication
   ```

2. Create a `feature.yml` manifest:

   ```yaml
   # projects/local/my-project/features/user-authentication/feature.yml
   id: user-authentication
   version: 1.0.0
   name: "User Authentication"
   description: "JWT-based user authentication and session management"
   
   context:
     overview: |
       Handles user login, registration, and session management using JWT tokens.
     architecture: |
       Uses middleware-based approach with token validation on protected routes.
     dependencies:
       - jsonwebtoken
       - passport
       - bcrypt
   
   files:
     entry_points:
       - src/auth/index.ts
     key_files:
       - src/auth/strategies/jwt.ts
       - src/middleware/auth.ts
     patterns:
       - src/auth/**/*.ts
   
   snippets:
     - id: protect-route
       title: "Protect a Route"
       description: "Apply authentication middleware to protect an endpoint"
       language: typescript
       content: |
         router.get('/protected',
           authenticate('jwt'),
           (req, res) => {
             res.json({ user: req.user });
           }
         );
   
   conventions:
     - "Always hash passwords before storing"
     - "Use HTTP-only cookies for token storage"
     - "Implement refresh token rotation"
   
   metadata:
     status: active
     owner: security-team
   ```

### Feature Generation

Features are automatically generated when you deploy your project:

```bash
# Features are generated during deploy
npm run project:deploy my-project

# Or generate manually
npm run project:generate-features my-project
```

### What Gets Generated for Features?

**GitHub Copilot**: `feature-<id>.md` files with full feature documentation

**Windsurf**: `feature-<id>.json` files with structured feature data

**Claude Code**: `feature-<id>.md` files with examples and context

**Cursor**: `features.json` with all features combined

### Feature Schema

See [`schemas/feature.schema.json`](../schemas/feature.schema.json) for the complete schema reference.

---

## External Projects

External Projects allow you to link projects from other repositories without duplicating configuration.

### What Are External Projects?

External projects are projects that live in other repositories but use this ai-tools configuration system. They're tracked via project registries.

### Why Use External Projects?

- **Centralized management**: Manage all project configs from one place
- **No duplication**: Projects maintain their own `.cleanship-ai-tools` folder
- **Team consistency**: Share global project configs, keep local ones private
- **Flexible deployment**: Deploy updates to multiple projects at once

### Setting Up an External Project

#### Method 1: Using project:init (Recommended)

The easiest way to set up an external project:

```bash
# Initialize AI tools in an existing project
npm run project:init /path/to/your-project

# With custom alias and description
npm run project:init /path/to/your-project --alias my-app -d "My application"

# Register in global registry (shared with team)
npm run project:init /path/to/your-project --register-global

# Initialize without auto-registering
npm run project:init /path/to/your-project --no-register
```

This will:
1. Create `.cleanship-ai-tools/` folder in your project
2. Copy and customize `project.yml` from template
3. Create `deploy.yml` configured to deploy to project root
4. Create example feature template
5. Auto-register in external projects registry (by default in local)

#### Method 2: Manual Setup

If you prefer manual setup:

1. **In the external project repository**, create a `.cleanship-ai-tools` folder:

   ```bash
   cd /path/to/your-project
   mkdir .cleanship-ai-tools
   ```

2. **Create project manifest** in the external project:

   ```bash
   # Copy template from ai-tools repo
   cp /path/to/ai-tools/projects/global/template/project.yml \
      .cleanship-ai-tools/project.yml
   
   # Edit it
   code .cleanship-ai-tools/project.yml
   ```

3. **Create deployment config** in the external project:

   ```yaml
   # .cleanship-ai-tools/deploy.yml
   target: ".."  # Deploy to project root (parent of .cleanship-ai-tools)
   tools:
     - github-copilot
     - windsurf
   mode: local
   backup: true
   ```

4. **Register in ai-tools repository**:

   ```bash
   cd /path/to/ai-tools
   
   # Add to local registry (gitignored)
   npm run project:external add /path/to/your-project/.cleanship-ai-tools --alias my-app
   
   # Or add to global registry (versioned, shared)
   npm run project:external add /path/to/your-project/.cleanship-ai-tools --alias my-app --global
   ```

### Managing External Projects

```bash
# List all external projects
npm run project:external list

# Add external project (local registry)
npm run project:external add /path/to/project/.cleanship-ai-tools --alias my-app

# Add external project (global registry, shared with team)
npm run project:external add /path/to/project/.cleanship-ai-tools --alias my-app --global

# Remove external project
npm run project:external remove my-app

# Remove from global registry
npm run project:external remove my-app --global
```

### Project Registries

**Global Registry** (`projects/projects.global.yml`)

- Versioned in git
- Shared with entire team
- Use for: Company projects, shared services

**Local Registry** (`projects/projects.local.yml`)

- Gitignored
- Private to you
- Use for: Personal projects, client work, experiments

Example registry structure:

```yaml
# projects/projects.global.yml
projects:
  - path: /opt/company/services/api-gateway/.cleanship-ai-tools
    alias: api-gateway
  - path: /opt/company/services/auth-service/.cleanship-ai-tools
    alias: auth-service
```

```yaml
# projects/projects.local.yml (gitignored)
projects:
  - path: /home/user/personal/my-app/.cleanship-ai-tools
    alias: my-personal-app
```

### Using External Projects

Once registered, external projects work just like local projects:

```bash
# List (includes external)
npm run project:list

# Generate
npm run project:generate my-app

# Deploy
npm run project:deploy my-app

# Deploy all (includes external)
npm run project:deploy-all
```

### External Project Structure

```
your-project/
├── src/
├── package.json
├── README.md
└── .cleanship-ai-tools/      # AI tools configuration
    ├── project.yml           # Project manifest
    ├── deploy.yml            # Deployment config
    └── features/             # Optional: Feature-specific context
        └── user-auth/
            └── feature.yml
```

After deployment:

```
your-project/
├── src/
├── .github/                  # Generated by deployment
│   └── copilot-instructions.md
├── .windsurf/                # Generated by deployment
│   └── rules/
└── .cleanship-ai-tools/      # Your configuration
    ├── project.yml
    └── deploy.yml
```

### Best Practices for External Projects

1. **Keep `.cleanship-ai-tools` in the project**: Let each project maintain its own configuration
2. **Use global registry for team projects**: Share paths to company projects
3. **Use local registry for personal work**: Keep client/personal projects private
4. **Document in project README**: Mention that the project uses ai-tools
5. **Version the manifests**: Track changes to project configs in the project's git history

---

## Best Practices

### 1. Start with the Template

Always start from `projects/local/.template/project.yml` - it has helpful comments.

### 2. Use Semantic Versioning

Update `version` when making significant changes:
- `1.0.0` → `1.1.0` - Added new conventions
- `1.1.0` → `2.0.0` - Breaking change (removed/changed rules)

### 3. Be Specific in Descriptions

Bad: "Use proper naming"
Good: "Use PascalCase for React components (e.g., ProductCard, UserProfile)"

### 4. Document Custom Rules

If your project has unique patterns, document them in `ai_tools.custom_rules`.

### 5. Keep It Updated

Update `metadata.updated` when making changes.
Update configurations when project conventions change.

### 6. Test Before Deploying

Always run with `--dry-run` first:
```bash
npm run project:deploy my-project -- --dry-run
```

### 7. Use Global Projects for Standards

Put team-wide standards in `projects/global/` so everyone uses them.

### 8. Enable Backups

Always deploy with `backup: true` in your deployment config.

---

## Troubleshooting

### Project Not Found

**Error**: `Project "my-project" not found`

**Solution**: Check that `projects/local/my-project/project.yml` or `projects/global/my-project/project.yml` exists.

### Validation Errors

**Error**: `Schema validation failed`

**Solution**:
1. Run `npm run validate` to see specific errors
2. Check required fields: `id`, `version`, `name`, `description`
3. Verify `id` is kebab-case
4. Verify `version` is semver (e.g., `1.0.0`)

### Generation Failed

**Error**: `Failed to generate project outputs`

**Solution**:
1. Check project manifest is valid YAML
2. Run `npm run validate`
3. Check file permissions

### Deployment Failed

**Error**: `Target directory does not exist`

**Solution**: Verify the `target` path in `config/deploy.yml` exists and is absolute.

**Error**: `Git commit failed`

**Solution**: Target directory might not be a git repository, or you have uncommitted changes.

### Files Not Copied

**Issue**: Deployment succeeds but files aren't where expected

**Solution**:
1. Run with `--dry-run` to see what would be copied
2. Check tool-specific target paths:
   - GitHub Copilot → `.github/`
   - Windsurf → `.windsurf/`
   - Cursor → `.cursor/`
   - Claude Code → `.claude/`

---

## Examples

### Example 1: Simple TypeScript Project

```yaml
id: simple-ts-app
version: 1.0.0
name: "Simple TypeScript App"
description: "A simple TypeScript application with basic conventions"

tech_stack:
  languages:
    - typescript

conventions:
  naming:
    - "Use camelCase for variables and functions"
    - "Use PascalCase for classes"
  patterns:
    - "Use TypeScript strict mode"

ai_tools:
  preferred_rulepacks:
    - base
    - coding-typescript
```

### Example 2: Full-Stack Project

```yaml
id: fullstack-app
version: 1.0.0
name: "Full-Stack Web App"
description: "React frontend with Node.js backend"

context:
  overview: "E-commerce web application with React and Node.js"
  purpose: "Provide online shopping experience"

tech_stack:
  languages:
    - typescript
  frontend:
    - react
    - redux
  backend:
    - node.js
    - express
  database:
    - postgresql

commands:
  dev:
    frontend: "cd frontend && npm run dev"
    backend: "cd backend && npm run dev"
  test:
    all: "npm test"

conventions:
  naming:
    - "Use PascalCase for React components"
  patterns:
    - "Use hooks for state management"
  testing:
    - "Minimum 80% test coverage"

ai_tools:
  preferred_agents:
    - code-reviewer
    - feature-builder
  preferred_rulepacks:
    - base
    - coding-typescript
    - security
  custom_rules:
    - "Always use our custom logger (src/utils/logger.ts)"
    - "API responses must follow { success, data, error } format"
```

### Example 3: Deployment Configuration

```yaml
# projects/local/my-api/deploy.yml
target: /home/user/projects/my-api
tools:
  - github-copilot
  - windsurf
mode: local
auto_commit: true
git_branch: "chore/update-ai-config"
backup: true
```

With local override in `deploy.local.yml` (gitignored):

```yaml
# projects/local/my-api/deploy.local.yml
target: /actual/local/path/to/my-api
```

---

## Advanced Usage

### Output Location

Generated outputs are stored in `.output/<project-id>/` (gitignored at repo root).

This location is automatically cleaned up and regenerated on each deployment.

### CI/CD Integration

Add to your CI pipeline:

```yaml
# .github/workflows/update-ai-config.yml
name: Update AI Config
on:
  push:
    paths:
      - 'projects/global/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: npm install
      - run: npm run validate
      - run: npm run project:deploy my-project -- --no-confirm
```

---

## See Also

- [PLAN.md](../PLAN.md) - Implementation plan
- [README.md](../README.md) - Main repository README
- [schemas/project.schema.json](../schemas/project.schema.json) - Schema reference
- [projects/global/example-ecommerce/](../projects/global/example-ecommerce/) - Complete example

---

**Questions?** Open an issue on GitHub or check the [main documentation](../README.md).
