package githubapp

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// staticTokenSource is a simple TokenSource for testing.
type staticTokenSource struct {
	token string
}

func (s *staticTokenSource) Token(_ context.Context) (string, error) {
	return s.token, nil
}

func TestContextWithOwner(t *testing.T) {
	ctx := context.Background()
	ctx = ContextWithOwner(ctx, "myorg")

	owner, ok := OwnerFromContext(ctx)
	assert.True(t, ok)
	assert.Equal(t, "myorg", owner)
}

func TestOwnerFromContext_Missing(t *testing.T) {
	ctx := context.Background()
	_, ok := OwnerFromContext(ctx)
	assert.False(t, ok)
}

func TestMultiOrgTokenSource_RoutesToOrgSource(t *testing.T) {
	defaultSource := &staticTokenSource{token: "default_token"}
	multi := NewMultiOrgTokenSource(defaultSource)
	multi.AddOrg("orgA", &staticTokenSource{token: "orgA_token"})
	multi.AddOrg("orgB", &staticTokenSource{token: "orgB_token"})

	ctx := ContextWithOwner(context.Background(), "orgA")
	token, err := multi.Token(ctx)
	require.NoError(t, err)
	assert.Equal(t, "orgA_token", token)
}

func TestMultiOrgTokenSource_CaseInsensitive(t *testing.T) {
	defaultSource := &staticTokenSource{token: "default_token"}
	multi := NewMultiOrgTokenSource(defaultSource)
	multi.AddOrg("OrgA", &staticTokenSource{token: "orgA_token"})

	ctx := ContextWithOwner(context.Background(), "orga")
	token, err := multi.Token(ctx)
	require.NoError(t, err)
	assert.Equal(t, "orgA_token", token)
}

func TestMultiOrgTokenSource_FallsBackToDefault(t *testing.T) {
	defaultSource := &staticTokenSource{token: "default_token"}
	multi := NewMultiOrgTokenSource(defaultSource)
	multi.AddOrg("orgA", &staticTokenSource{token: "orgA_token"})

	ctx := ContextWithOwner(context.Background(), "unknown_org")
	token, err := multi.Token(ctx)
	require.NoError(t, err)
	assert.Equal(t, "default_token", token)
}

func TestMultiOrgTokenSource_NoOwnerInContext_UsesDefault(t *testing.T) {
	defaultSource := &staticTokenSource{token: "default_token"}
	multi := NewMultiOrgTokenSource(defaultSource)
	multi.AddOrg("orgA", &staticTokenSource{token: "orgA_token"})

	token, err := multi.Token(context.Background())
	require.NoError(t, err)
	assert.Equal(t, "default_token", token)
}

func TestMultiOrgTokenSource_NoOwnerNoDefault_Error(t *testing.T) {
	multi := NewMultiOrgTokenSource(nil)
	multi.AddOrg("orgA", &staticTokenSource{token: "orgA_token"})

	_, err := multi.Token(context.Background())
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "no owner in context")
}

func TestMultiOrgTokenSource_UnknownOrgNoDefault_Error(t *testing.T) {
	multi := NewMultiOrgTokenSource(nil)
	multi.AddOrg("orgA", &staticTokenSource{token: "orgA_token"})

	ctx := ContextWithOwner(context.Background(), "unknown")
	_, err := multi.Token(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "no GitHub App installation configured for org")
}
