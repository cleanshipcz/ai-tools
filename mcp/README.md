# MCP (Model Context Protocol)

MCP servers provide AI models with access to external tools and data sources - filesystems, git, databases, APIs, and more.

## 📁 What's Here

### Servers (`servers/`)
- **filesystem.yaml** - Read/write files
- **git.yaml** - Git operations (status, diff, log, commit)
- **http.yaml** - Make HTTP requests to APIs
- **shell.yaml** - Execute shell commands (⚠️ use carefully!)

### Presets (`presets/`)
- **base.tools.yaml** - Safe defaults (filesystem + git)
- **secure.tools.yaml** - Extra restricted (no shell, limited HTTP)

## 🎯 Purpose

MCP servers give AI agents **external capabilities**:
- 📁 File system access (read, write, list)
- 🔧 Git operations (commit, branch, diff)
- 🌐 HTTP requests (APIs, webhooks)
- 💻 Shell commands (run scripts, tools)

**Without MCP:** AI can only generate text  
**With MCP:** AI can interact with your system

## 🔧 How MCP Works in Different Tools

### Windsurf

MCP capabilities are configured in agents and automatically available.

1. **In agent definition:**
   ```yaml
   # agents/my-agent.yml
   capabilities:
     - mcp:git
     - mcp:filesystem
   ```

2. **Build:**
   ```bash
   npm run build
   ```

3. **Import agent:**
   - Settings → Cascade → Rules
   - Import: `adapters/windsurf/rules/my-agent.json`

4. **Usage:**
   - "Show me the git status"
   - "Read the README file"
   - Agent uses MCP to execute

### Cursor

1. **Configure in agent:**
   ```yaml
   capabilities:
     - mcp:git
     - mcp:filesystem
     - mcp:http
   ```

2. **Build and import:**
   ```bash
   npm run build
   # Import adapters/cursor/recipes.json
   ```

3. **MCP servers automatically available** when using the agent

### Claude Code / Desktop

1. **MCP servers defined in:** `adapters/claude-code/mcp-config.json` (after build)

2. **Add to Claude Desktop config:**
   ```json
   // ~/Library/Application Support/Claude/claude_desktop_config.json
   {
     "mcpServers": {
       "filesystem": {
         "command": "npx",
         "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/workspace"]
       },
       "git": {
         "command": "npx",
         "args": ["-y", "@modelcontextprotocol/server-git", "--repository", "/path/to/repo"]
       }
     }
   }
   ```

3. **Restart Claude Desktop** to activate MCP servers

### VSCode / GitHub Copilot

MCP support depends on the extension. Check:
- GitHub Copilot Chat documentation
- Extension-specific MCP configuration

## 📝 Creating Custom MCP Configurations

### Basic MCP Server

```yaml
# mcp/servers/database.yaml
server: database
command: npx
args:
  - "-y"
  - "@mycompany/mcp-server-database"
  - "--host"
  - "localhost"
  - "--database"
  - "mydb"
description: Database query and management
capabilities:
  - "query_database"
  - "list_tables"
  - "describe_schema"
tags:
  - database
  - sql
```

### Creating a Preset

```yaml
# mcp/presets/my-stack.tools.yaml
preset: my-stack
description: My development stack
includes:
  - filesystem
  - git
  - database
  - http
```

### Using in Agents

```yaml
# agents/my-agent.yml
capabilities:
  - mcp:filesystem
  - mcp:git
  - mcp:database
  # Or reference preset:
  - mcp-preset:my-stack
```

## 🔍 Available MCP Servers

### filesystem (v1.0.0)

**Provides:**
- Read files
- Write files
- List directories
- Create/delete files

**Security:** Read/write access to workspace
**Use in:** Most agents

### git (v1.0.0)

**Provides:**
- Git status
- Git diff
- Git log
- Git commit
- Branch operations

**Security:** Full git access to repository
**Use in:** Code review, commit agents

### http (v1.0.0)

**Provides:**
- HTTP GET/POST/PUT/DELETE
- API calls
- Webhook triggers

**Security:** Can make external HTTP requests
**Use in:** API integration agents

### shell (v1.0.0)

**Provides:**
- Execute arbitrary shell commands

**Security:** ⚠️ **DANGEROUS** - Full shell access
**Use in:** Only highly trusted, sandboxed environments

## 🔒 Security Considerations

### Security Levels

**Safe (Recommended):**
```yaml
capabilities:
  - mcp:filesystem  # Limited to workspace
  - mcp:git         # Limited to repository
```

**Moderate Risk:**
```yaml
capabilities:
  - mcp:http  # Can call external APIs
```

**High Risk:**
```yaml
capabilities:
  - mcp:shell  # Full system access ⚠️
```

### Best Practices

1. **Principle of Least Privilege**
   ```yaml
   # Good: Only what's needed
   capabilities:
     - mcp:git
   
   # Bad: Everything
   capabilities:
     - mcp:filesystem
     - mcp:git
     - mcp:http
     - mcp:shell
   ```

2. **Use Presets**
   ```yaml
   # Instead of listing all:
   capabilities:
     - mcp-preset:base  # filesystem + git only
   ```

3. **Disable Shell by Default**
   ```yaml
   # Only enable for specific use cases
   # capabilities:
   #   - mcp:shell  # Commented out
   ```

4. **Sandbox When Possible**
   - Use Docker containers
   - Virtual machines
   - Limited user accounts

### Preset Comparison

| Preset | filesystem | git | http | shell |
| ------ | ---------- | --- | ---- | ----- |
| base   | ✅          | ✅   | ❌    | ❌     |
| secure | ✅ (RO)     | ✅   | ❌    | ❌     |
| full   | ✅          | ✅   | ✅    | ⚠️     |

## 🔨 Building & Validation

```bash
# Validate
npm run validate

# Build
npm run build

# Check generated MCP configs
ls adapters/*/mcp-config.json
cat adapters/claude-code/mcp-config.json
```

## 💡 Usage Examples

### Reading Files

**Agent with filesystem MCP:**
```
User: "Read the package.json file"
Agent: Uses MCP filesystem to read file
Agent: "Here's your package.json content..."
```

### Git Operations

**Agent with git MCP:**
```
User: "What files changed in the last commit?"
Agent: Uses MCP git to run: git diff HEAD~1
Agent: "These files changed: [list]"
```

### HTTP Requests

**Agent with http MCP:**
```
User: "Get the latest GitHub issues"
Agent: Uses MCP http to: GET https://api.github.com/repos/.../issues
Agent: "Here are the latest issues: [list]"
```

## 🎨 Design Patterns

### Layered Capabilities

```yaml
# Basic agent - read-only
capabilities:
  - mcp:filesystem  # Read only in secure preset

# Intermediate - read/write
capabilities:
  - mcp:filesystem  # Full access
  - mcp:git

# Advanced - external calls
capabilities:
  - mcp:filesystem
  - mcp:git
  - mcp:http

# Expert - full system
capabilities:
  - mcp:filesystem
  - mcp:git
  - mcp:http
  - mcp:shell  # ⚠️
```

### Preset Composition

```yaml
# mcp/presets/ci-cd.tools.yaml
preset: ci-cd
includes:
  - filesystem
  - git
  - http  # For deployment webhooks
# Excludes shell for security
```

## 🔗 Related

- [Agents](../agents/README.md) - Agents that use MCP capabilities
- [Skills](../skills/README.md) - Skills can be exposed via MCP
- [Security](../docs/SECURITY.md) - Security best practices

## 🐛 Troubleshooting

### MCP Server Not Working

1. **Check if server is installed:**
   ```bash
   npx -y @modelcontextprotocol/server-filesystem --help
   ```

2. **Check configuration:**
   ```bash
   cat adapters/claude-code/mcp-config.json
   ```

3. **Check logs:**
   - Windsurf: Check Cascade output
   - Claude Desktop: Check Console/DevTools
   - Cursor: Check extension logs

### Permission Issues

```bash
# Filesystem access denied
# Solution: Check workspace path in MCP config

# Git operations fail
# Solution: Verify git repository path
```

### Installing MCP Servers

```bash
# Standard MCP servers
npm install -g @modelcontextprotocol/server-filesystem
npm install -g @modelcontextprotocol/server-git

# Custom servers
npm install -g @mycompany/mcp-server-custom
```

## 📚 Further Reading

- [MCP Documentation](https://modelcontextprotocol.io) - Official MCP docs
- [Main README](../README.md) - Full project documentation
- [Security Guide](../docs/SECURITY.md) - Security best practices
