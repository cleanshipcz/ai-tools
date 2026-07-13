package githubapp

import (
	"context"
	"fmt"
	"strings"
)

// ownerContextKey is the context key for the repository owner/org name.
type ownerContextKey struct{}

// ContextWithOwner returns a context with the owner/org name stored in it.
// This is used by the owner injection middleware to pass the target org
// to MultiOrgTokenSource for per-org token routing.
func ContextWithOwner(ctx context.Context, owner string) context.Context {
	return context.WithValue(ctx, ownerContextKey{}, owner)
}

// OwnerFromContext retrieves the owner/org name from the context.
func OwnerFromContext(ctx context.Context) (string, bool) {
	owner, ok := ctx.Value(ownerContextKey{}).(string)
	return owner, ok
}

// MultiOrgTokenSource routes token requests to per-org InstallationTokenSources
// based on the owner stored in the request context.
type MultiOrgTokenSource struct {
	defaultSource TokenSource
	orgSources    map[string]TokenSource // keyed by lowercase owner name
}

// NewMultiOrgTokenSource creates a token source that routes to per-org sources.
// defaultSource is used when the owner is not in the map or not in the context.
// It may be nil if all orgs are explicitly configured; in that case, requests
// for unknown orgs will return an error.
func NewMultiOrgTokenSource(defaultSource TokenSource) *MultiOrgTokenSource {
	return &MultiOrgTokenSource{
		defaultSource: defaultSource,
		orgSources:    make(map[string]TokenSource),
	}
}

// AddOrg registers a token source for a specific org/owner.
func (m *MultiOrgTokenSource) AddOrg(owner string, source TokenSource) {
	m.orgSources[strings.ToLower(owner)] = source
}

// Token returns a valid installation access token for the org identified
// by the owner in the context. Falls back to the default source if the
// owner is not found or not in the context.
func (m *MultiOrgTokenSource) Token(ctx context.Context) (string, error) {
	owner, ok := OwnerFromContext(ctx)
	if ok && owner != "" {
		if source, found := m.orgSources[strings.ToLower(owner)]; found {
			return source.Token(ctx)
		}
	}

	if m.defaultSource != nil {
		return m.defaultSource.Token(ctx)
	}

	if ok && owner != "" {
		return "", fmt.Errorf("no GitHub App installation configured for org %q and no default installation set", owner)
	}
	return "", fmt.Errorf("no owner in context and no default GitHub App installation configured")
}
