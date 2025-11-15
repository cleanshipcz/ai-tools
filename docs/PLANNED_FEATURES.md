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

## Other features NOT NOW

- [ ] More tool adapters (Cody, Neovim, JetBrains AI)
- [ ] Prompt marketplace/sharing platform
- [ ] Advanced eval metrics (BLEU, ROUGE, custom)
- [ ] Cost analytics dashboard
- [ ] Version control integration (Git hooks for prompt changes)
- [ ] Prompt optimization suggestions (A/B testing automation)
