# Planned features

## ✅ Implemented

### Miscs (v1.1.0)

- ✅ clean - Cleans generated files and rebuilds them (`npm run clean`)
- ✅ project - Deploy all available projects at once (`npm run project:deploy-all`)

### Large features (v1.1.0)

- ✅ Feature-specific context system in `projects/<global|local>/project-name/features/feature-name`
- ✅ Feature manifests with snippets and conventions
- ✅ Generates feature-specific context for all tools (GitHub Copilot, Windsurf, Claude Code, Cursor)
- ✅ Feature generation integrated into project generation workflow

### Project specific (v1.1.0)

- ✅ Support for external projects via `.cleanship-ai-tools` folders
- ✅ Project registry system with `projects.global.yml` (versioned) and `projects.local.yml` (gitignored)
- ✅ External project management commands (`npm run project:external`)
- ✅ External projects integrated into all project commands (list, generate, deploy)

### Copilot CLI support

- add Copilot CLI as a supported/exported tool

### Multi-agent support

- design and implement a feature that allows multiple agents to be used in a sequence
- imagine:
  - using a feature building agent to build a feature
  - using a review agent to review the feature
  - using a feature building/refactoring agent to reflect the review
  - repeat until reaching certain quality
  - use a documentation agent to reflect the changes
- the configuration would be a certain recipe to deliver a full feature as if it was done by a team
- I can see it working with claude code `-c ` feature and some custom script, for example
- I want it for major tools like claude code, copilot CLI, or if possible for GPT
- if not possible, provide an analysis

## Other features NOT NOW

- [ ] More tool adapters (Cody, Neovim, JetBrains AI)
- [ ] Prompt marketplace/sharing platform
- [ ] Advanced eval metrics (BLEU, ROUGE, custom)
- [ ] Cost analytics dashboard
- [ ] Version control integration (Git hooks for prompt changes)
- [ ] Prompt optimization suggestions (A/B testing automation)
