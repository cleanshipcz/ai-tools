package githubapp

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestBuildTokenSource_DefaultOnly(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(installationTokenResponse{
			Token:     "ghs_single",
			ExpiresAt: time.Now().Add(1 * time.Hour),
		})
	}))
	defer server.Close()

	defaultCfg := &AppConfig{
		AppID:          12345,
		InstallationID: 111,
		PrivateKey:     key,
	}

	ts := BuildTokenSource(defaultCfg, nil, server.URL)

	// Should be an InstallationTokenSource, not MultiOrgTokenSource
	_, isInstallation := ts.(*InstallationTokenSource)
	assert.True(t, isInstallation, "single default app should return InstallationTokenSource")

	token, err := ts.Token(context.Background())
	require.NoError(t, err)
	assert.Equal(t, "ghs_single", token)
}

func TestBuildTokenSource_MultiOrg_SeparateApps(t *testing.T) {
	keyA, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	keyB, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	keyDefault, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusCreated)
		// Return different tokens based on the installation ID in the URL
		switch r.URL.Path {
		case "/app/installations/111/access_tokens":
			json.NewEncoder(w).Encode(installationTokenResponse{
				Token:     "ghs_default",
				ExpiresAt: time.Now().Add(1 * time.Hour),
			})
		case "/app/installations/222/access_tokens":
			json.NewEncoder(w).Encode(installationTokenResponse{
				Token:     "ghs_orgA",
				ExpiresAt: time.Now().Add(1 * time.Hour),
			})
		case "/app/installations/333/access_tokens":
			json.NewEncoder(w).Encode(installationTokenResponse{
				Token:     "ghs_orgB",
				ExpiresAt: time.Now().Add(1 * time.Hour),
			})
		}
	}))
	defer server.Close()

	defaultCfg := &AppConfig{
		AppID:          100,
		InstallationID: 111,
		PrivateKey:     keyDefault,
	}

	orgConfigs := []OrgAppConfig{
		{Owner: "orgA", AppID: 200, InstallationID: 222, PrivateKey: keyA},
		{Owner: "orgB", AppID: 300, InstallationID: 333, PrivateKey: keyB},
	}

	ts := BuildTokenSource(defaultCfg, orgConfigs, server.URL)

	// Should be a MultiOrgTokenSource
	_, isMulti := ts.(*MultiOrgTokenSource)
	assert.True(t, isMulti, "multi-org should return MultiOrgTokenSource")

	// Test orgA routing
	ctx := ContextWithOwner(context.Background(), "orgA")
	token, err := ts.Token(ctx)
	require.NoError(t, err)
	assert.Equal(t, "ghs_orgA", token)

	// Test orgB routing
	ctx = ContextWithOwner(context.Background(), "orgB")
	token, err = ts.Token(ctx)
	require.NoError(t, err)
	assert.Equal(t, "ghs_orgB", token)

	// Test default fallback
	token, err = ts.Token(context.Background())
	require.NoError(t, err)
	assert.Equal(t, "ghs_default", token)
}

func TestBuildTokenSource_PerOrgOnly_NoDefault(t *testing.T) {
	keyA, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(installationTokenResponse{
			Token:     "ghs_orgA",
			ExpiresAt: time.Now().Add(1 * time.Hour),
		})
	}))
	defer server.Close()

	orgConfigs := []OrgAppConfig{
		{Owner: "orgA", AppID: 200, InstallationID: 222, PrivateKey: keyA},
	}

	ts := BuildTokenSource(nil, orgConfigs, server.URL)

	// Org-specific should work
	ctx := ContextWithOwner(context.Background(), "orgA")
	token, err := ts.Token(ctx)
	require.NoError(t, err)
	assert.Equal(t, "ghs_orgA", token)

	// Unknown org with no default should error
	ctx = ContextWithOwner(context.Background(), "unknown")
	_, err = ts.Token(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "no GitHub App installation configured for org")
}
