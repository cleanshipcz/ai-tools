package githubapp

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestTokenSourceTransport_InjectsToken(t *testing.T) {
	var capturedAuth string

	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedAuth = r.Header.Get("Authorization")
		w.WriteHeader(http.StatusOK)
	}))
	defer backend.Close()

	transport := &TokenSourceTransport{
		Transport:   http.DefaultTransport,
		TokenSource: &staticTokenSource{token: "ghs_test_token"},
	}

	client := &http.Client{Transport: transport}
	req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, backend.URL+"/repos/myorg/myrepo", nil)
	require.NoError(t, err)

	resp, err := client.Do(req)
	require.NoError(t, err)
	defer resp.Body.Close()

	assert.Equal(t, "Bearer ghs_test_token", capturedAuth)
}

func TestTokenSourceTransport_PropagatesOwnerContext(t *testing.T) {
	var capturedAuth string

	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedAuth = r.Header.Get("Authorization")
		w.WriteHeader(http.StatusOK)
	}))
	defer backend.Close()

	// MultiOrgTokenSource that routes based on owner
	multi := NewMultiOrgTokenSource(&staticTokenSource{token: "default_token"})
	multi.AddOrg("myorg", &staticTokenSource{token: "myorg_token"})

	transport := &TokenSourceTransport{
		Transport:   http.DefaultTransport,
		TokenSource: multi,
	}

	client := &http.Client{Transport: transport}
	ctx := ContextWithOwner(context.Background(), "myorg")
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, backend.URL+"/repos/myorg/myrepo", nil)
	require.NoError(t, err)

	resp, err := client.Do(req)
	require.NoError(t, err)
	defer resp.Body.Close()

	assert.Equal(t, "Bearer myorg_token", capturedAuth)
}
