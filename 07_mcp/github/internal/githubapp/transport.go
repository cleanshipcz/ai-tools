package githubapp

import (
	"fmt"
	"net/http"
	"strings"

	ghcontext "github.com/github/github-mcp-server/pkg/context"
	headers "github.com/github/github-mcp-server/pkg/http/headers"
)

// TokenSourceTransport is an http.RoundTripper that injects a Bearer token
// from a TokenSource on every request. It mirrors BearerAuthTransport but
// sources the token dynamically (supporting token refresh and multi-org routing).
type TokenSourceTransport struct {
	Transport   http.RoundTripper
	TokenSource TokenSource
}

func (t *TokenSourceTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	token, err := t.TokenSource.Token(req.Context())
	if err != nil {
		return nil, fmt.Errorf("failed to get token: %w", err)
	}

	req = req.Clone(req.Context())
	req.Header.Set(headers.AuthorizationHeader, "Bearer "+token)

	// Propagate GraphQL feature flags from context, matching BearerAuthTransport behavior
	if features := ghcontext.GetGraphQLFeatures(req.Context()); len(features) > 0 {
		req.Header.Set(headers.GraphQLFeaturesHeader, strings.Join(features, ", "))
	}

	return t.Transport.RoundTrip(req)
}
