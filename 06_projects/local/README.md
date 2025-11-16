# Local Projects

This directory is for your personal, private projects. It's gitignored.

## Creating a New Project

```bash
# 1. Copy the template
cp -r ../global/template my-project

# 2. Edit the project.yml
cd my-project
code project.yml

# 3. Generate configurations
npm run project:generate my-project

# 4. Deploy (see deploy.yml in the template)
npm run project:deploy my-project
```

## Template

Use `projects/global/template/` as your starting point. It contains:
- `project.yml` - Project configuration with helpful comments
- `deploy.yml` - Example deployment configuration

## Examples

See `projects/global/example-ecommerce/` for a complete real-world example.
