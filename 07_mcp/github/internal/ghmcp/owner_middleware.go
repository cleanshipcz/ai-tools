package ghmcp

import (
	"context"
	"encoding/json"

	"github.com/github/github-mcp-server/internal/githubapp"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

// ownerInjectionMiddleware extracts the "owner" parameter from MCP tools/call
// requests and injects it into the context via githubapp.ContextWithOwner.
// This enables multi-org token routing in AppDeps without changing tool handler
// signatures.
//
// Only registered when using GitHub App auth mode.
func ownerInjectionMiddleware(next mcp.MethodHandler) mcp.MethodHandler {
	return func(ctx context.Context, method string, req mcp.Request) (mcp.Result, error) {
		if method != "tools/call" {
			return next(ctx, method, req)
		}

		callReq, ok := req.(*mcp.CallToolRequest)
		if !ok {
			return next(ctx, method, req)
		}

		// Arguments is json.RawMessage — unmarshal to extract the owner field
		if len(callReq.Params.Arguments) > 0 {
			var args map[string]any
			if err := json.Unmarshal(callReq.Params.Arguments, &args); err == nil {
				if owner, ok := args["owner"].(string); ok && owner != "" {
					ctx = githubapp.ContextWithOwner(ctx, owner)
				}
			}
		}

		return next(ctx, method, req)
	}
}
