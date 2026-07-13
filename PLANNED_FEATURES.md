# Planned features

## CLI execution

- add support for CLI execution -> using gradle wrapper and a simple CLI app

## Deployment ignored

- when deploying to an external project, for each deployed folder check:
  - is the folder present? yes -> throw error
  - is the folder not present or empty? continue:
    - is the folder mentioned in .gitignore or .git/info/exclude? yes -> continue, no -> add it to .git/info/exclude and continue

## Skills

- add support for skills

## Project archetype

- add support for project archetype
  - archetype would be a parent of a project to reuse common configuration
  - archetype would be defined in a similar way as project.yml
  - project.yml in the new project would then just reference the archetype and override what is needed
- issues:
  - would require project manifests to have optional fields because they can be filled by archetypes
  - for proper validation then a custom validation would be required while the fields are still optional -> leads to separating IO model and internal model -> can do in later stage once it settles, for now use the copy prompt

## Prompts

- prompts webpage supported
- should it be somehow interconnected? Maybe CRUD server? Or just a webpage?

## MCPs

- add support for MCPs

## Project-specific configuration

- add support for project-specific tools (skills, prompts, rulesets, agents)
- based on structure of the project
  - project.yml
  - skills/
  - prompts/
  - rulesets/
  - agents/

## Separate DAO and service layers

- this will allow hierarchical definitions and better validation
