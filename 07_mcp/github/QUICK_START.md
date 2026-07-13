# GitHub MCP Server — Quick Start (GitHub App Auth)

This is a fork of [github/github-mcp-server](https://github.com/github/github-mcp-server) with native GitHub App installation token authentication. The server handles JWT minting and token refresh internally — no manual `gh auth login` or token management needed.

## Build

```bash
cd 07_mcp/github
go build -o github-mcp-server ./cmd/github-mcp-server
```

Cross-compile for Linux (e.g. for a remote server):

```bash
GOOS=linux GOARCH=amd64 go build -o github-mcp-server-linux-amd64 ./cmd/github-mcp-server
```

## Prerequisites

You need a GitHub App with:
- An **App ID**
- An **Installation ID** (install the app on your org/account)
- A **Private Key** (`.pem` file downloaded from the app settings)

If you have multiple orgs, you can use a separate GitHub App per org.

---

## OpenClaw

### Single org

```json
{
  "mcp": {
    "servers": {
      "github": {
        "command": "/path/to/github-mcp-server",
        "args": ["stdio", "--auth-mode=github-app"],
        "env": {
          "GITHUB_APP_ID": "123456",
          "GITHUB_APP_INSTALLATION_ID": "789012",
          "GITHUB_APP_PRIVATE_KEY": "/path/to/private-key.pem"
        }
      }
    }
  }
}
```

### Multiple orgs (separate GitHub App per org)

```json
{
  "mcp": {
    "servers": {
      "github": {
        "command": "/path/to/github-mcp-server",
        "args": ["stdio", "--auth-mode=github-app"],
        "env": {
          "GITHUB_APP_ORGS": "blahami2;cleanshipcz",

          "GITHUB_APP_ORG_BLAHAMI2_APP_ID": "111",
          "GITHUB_APP_ORG_BLAHAMI2_INSTALLATION_ID": "222",
          "GITHUB_APP_ORG_BLAHAMI2_PRIVATE_KEY": "/path/to/blahami2.pem",

          "GITHUB_APP_ORG_CLEANSHIPCZ_APP_ID": "333",
          "GITHUB_APP_ORG_CLEANSHIPCZ_INSTALLATION_ID": "444",
          "GITHUB_APP_ORG_CLEANSHIPCZ_PRIVATE_KEY": "/path/to/cleanshipcz.pem"
        }
      }
    }
  }
}
```

You can combine both: set a default app (`GITHUB_APP_ID` etc.) as a fallback, plus per-org overrides via `GITHUB_APP_ORGS`.

---

## Claude Code

Add to `~/.claude/settings.json` or your project's `.mcp.json`:

### Single org

```json
{
  "mcpServers": {
    "github": {
      "command": "/path/to/github-mcp-server",
      "args": ["stdio", "--auth-mode=github-app"],
      "env": {
        "GITHUB_APP_ID": "123456",
        "GITHUB_APP_INSTALLATION_ID": "789012",
        "GITHUB_APP_PRIVATE_KEY": "/path/to/private-key.pem"
      }
    }
  }
}
```

### Multiple orgs

```json
{
  "mcpServers": {
    "github": {
      "command": "/path/to/github-mcp-server",
      "args": ["stdio", "--auth-mode=github-app"],
      "env": {
        "GITHUB_APP_ORGS": "blahami2;cleanshipcz",
        "GITHUB_APP_ORG_BLAHAMI2_APP_ID": "111",
        "GITHUB_APP_ORG_BLAHAMI2_INSTALLATION_ID": "222",
        "GITHUB_APP_ORG_BLAHAMI2_PRIVATE_KEY": "/path/to/blahami2.pem",
        "GITHUB_APP_ORG_CLEANSHIPCZ_APP_ID": "333",
        "GITHUB_APP_ORG_CLEANSHIPCZ_INSTALLATION_ID": "444",
        "GITHUB_APP_ORG_CLEANSHIPCZ_PRIVATE_KEY": "/path/to/cleanshipcz.pem"
      }
    }
  }
}
```

---

## PAT mode (unchanged from upstream)

The original PAT-based auth still works exactly as before:

```json
{
  "command": "/path/to/github-mcp-server",
  "args": ["stdio"],
  "env": {
    "GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_..."
  }
}
```

---

## Environment variable reference

### Default app credentials

| Variable | Required | Description |
|----------|----------|-------------|
| `GITHUB_AUTH_MODE` | No | Alternative to `--auth-mode` flag. `pat` (default) or `github-app` |
| `GITHUB_APP_ID` | Yes* | GitHub App ID |
| `GITHUB_APP_INSTALLATION_ID` | Yes* | Installation ID for this app |
| `GITHUB_APP_PRIVATE_KEY` | Yes* | PEM content (inline) or path to `.pem` file |

*Required for single-org or as a fallback. Not needed if only using per-org configs.

### Per-org credentials

| Variable | Required | Description |
|----------|----------|-------------|
| `GITHUB_APP_ORGS` | Yes | Semicolon-separated list of org names (e.g. `blahami2;cleanshipcz`) |
| `GITHUB_APP_ORG_<OWNER>_APP_ID` | Yes | App ID for this org (`<OWNER>` is uppercased) |
| `GITHUB_APP_ORG_<OWNER>_INSTALLATION_ID` | Yes | Installation ID for this org |
| `GITHUB_APP_ORG_<OWNER>_PRIVATE_KEY` | Yes | PEM content or path to `.pem` file for this org |

### How it works

1. On startup, the server parses app credentials and RSA private keys
2. On the first API call, it mints an RS256 JWT, exchanges it for a `ghs_` installation token via GitHub's API, and caches it
3. Tokens auto-refresh 10 minutes before their 1-hour expiry — fully transparent
4. For multi-org: when a tool call includes an `owner` param, the server routes to that org's token automatically
