# Tool Integration Reference
This repository generates tool-ready configurations from YAML manifests. Use the CLI to stage outputs in `.output/<project>/` and deploy them into your project (with backups) using `deploy.yml`.

## Generate and Deploy
```bash
# Stage outputs for a single project
npm run project:generate my-project

# Generate + copy to the deploy target with backups (recommended)
npm run project:deploy my-project
# Options: --dry-run --force --interactive
```

Staged files live under `.output/<project>/...`. Deploy copies them into the `target` path from `deploy.yml` and keeps timestamped backups in `.backups/<project>/`.

Global, project-agnostic adapters can be built with `npm run build` (written to `adapters/`).

## Output by Tool
| Tool | Staged Path (under `.output/<project>/`) | What You Get | Usage |
| ---- | --------------------------------------- | ------------ | ----- |
| Windsurf | `.windsurf/` | `project-context.md`, agent/prompt rules, recipes, feature workflows | Copy to project root (deploy does this). Workflows from `features/` are merged automatically. |
| Cursor | `.cursor/` | `recipes.json`, `project-rules.json`, `.cs.recipes/` | Copy to project root or `~/.cursor/`. Recipes are ready-to-run scripts. |
| Claude Code | `.claude/` | Prompt JSON, skills, agents, `project-context.json`, `.cs.recipes/` | Keep in project root or copy to `~/.config/claude/` if you want global access. |
| GitHub Copilot | `.github/` | `instructions.md`, prompt + agent markdown, `.cs.recipes/` | Place at repo root for IDE auto-application. |
| Copilot CLI | `AGENTS.md` and `.cs.recipes/` at root | Agent catalog plus runnable recipe scripts | Keep both at repo root; invoke recipes directly from `.cs.recipes/`. |
| Codex | `AGENTS.md` at root, `.codex/prompts/` | Agent catalog and prompt library | Deploy copies prompts to `~/.codex/prompts` and `AGENTS.md` to the project root. |

## Tips
- Use `deploy.yml` to choose which tools are generated, set the deployment target, and enable backups/auto-commit.
- Include/exclude prompts, agents, rulesets, and recipes per project with `project.yml`/`deploy.yml`.
- Feature manifests emit extra snippets and workflows; they are merged into the Windsurf output during deployment.
- Recipe scripts are placed in `.cs.recipes/` for each supported tool so you can run them directly from your project.
