# Planned features

## Miscs

- clean - build cleans generated files and rebuilds them
- project - deploy all available projects at once (possibly if found?) ->  this serves when a rule is e.g. updated so that it's applied to all projects

## Large features

- similar to projects, local features with context can be defined
- it a subfolder of the project folder
  - meaning in projects/<global|local>/project-name/features/feature-name
- doesn't add anything to the global context but generates snippets to be used as feature-specific context for import (like e.g. prompts in copilot)

## Project specific

- expect a custom ".cleanship-ai-tools" folder in the project root
- it will contain project and feature specific information (so that the project maintains it and not this repository) -> it will be treated just like the local projects folder
- it can be supplied as an argument to the generation command
  - it treats the folder as a project folder (looks for project.yml, deploy.yml, features/, etc.)
  - it outputs to the project folder
- here in this repo, in projects/ there will be also two lists:
  - projects.global.yml (versioned)
  - projects.local.yml (gitignored)
  - these lists will contain paths to the project folders that are to be loaded along with the content of projects/global and projects/local
  - that should be reflected for all project:X commands (e.g. list, deploy, generate, ...)

## Other features NOT NOW

- [ ] More tool adapters (Cody, Neovim, JetBrains AI)
- [ ] Prompt marketplace/sharing platform
- [ ] Advanced eval metrics (BLEU, ROUGE, custom)
- [ ] Cost analytics dashboard
- [ ] Version control integration (Git hooks for prompt changes)
- [ ] Prompt optimization suggestions (A/B testing automation)
