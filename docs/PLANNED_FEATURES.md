# Planned features

## Project base

- Defines some kind of standardized yaml that serves as a project base
  - gives context (project overview, purpose)
  - tech stack
  - reference to specific docs
    - readme
    - code style
    - contributing guide
    - etc.
  - commands
  - etc.

### Specific projects

- project-specific output files are generated based on the project
- two directories:
  - global projects (versioned)
  - local projects (gitignored)
- takes from both and generates project specific outputs for all tools (e.g. copilot-instructions.md contains project specific data)
  - similarly output for all tools

## Installation to tools

- Based on some local config file copy the outputs to projects
- e.g. the config would contain list of projects and for each projects list of tools to suppor
  - then upon some command (not necessarily build, something custom, like deploy:project-name) it would generate the outputs for the project and put them int he project folder to be used by the tools (cursor, windwsurf, ...)
  - this config can also be per project
- should also contain some example usage
  - example project (see feature above) with config

## Other features

- [ ] More tool adapters (Cody, Neovim, JetBrains AI)
- [ ] Prompt marketplace/sharing platform
- [ ] Advanced eval metrics (BLEU, ROUGE, custom)
- [ ] Cost analytics dashboard
- [ ] Version control integration (Git hooks for prompt changes)
- [ ] Prompt optimization suggestions (A/B testing automation)
