package githubapp

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func generateTestKey(t *testing.T) *rsa.PrivateKey {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	return key
}

func TestInstallationTokenSource_MintJWT(t *testing.T) {
	key := generateTestKey(t)
	ts := NewInstallationTokenSource(12345, 67890, key, "https://api.github.com")

	signedJWT, err := ts.mintJWT()
	require.NoError(t, err)
	assert.NotEmpty(t, signedJWT)

	// Parse and verify the JWT
	token, err := jwt.Parse(signedJWT, func(token *jwt.Token) (any, error) {
		if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return &key.PublicKey, nil
	})
	require.NoError(t, err)
	assert.True(t, token.Valid)

	claims, ok := token.Claims.(jwt.MapClaims)
	require.True(t, ok)
	assert.Equal(t, "12345", claims["iss"])
}

func TestInstallationTokenSource_Token_Success(t *testing.T) {
	key := generateTestKey(t)
	var callCount atomic.Int32

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount.Add(1)
		assert.Equal(t, http.MethodPost, r.Method)
		assert.Contains(t, r.URL.Path, "/app/installations/67890/access_tokens")

		auth := r.Header.Get("Authorization")
		assert.True(t, len(auth) > len("Bearer "), "expected Authorization header with JWT")

		w.WriteHeader(http.StatusCreated)
		resp := installationTokenResponse{
			Token:     "ghs_test_token_123",
			ExpiresAt: time.Now().Add(1 * time.Hour),
		}
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	ts := NewInstallationTokenSource(12345, 67890, key, server.URL)

	// First call should hit the server
	token, err := ts.Token(context.Background())
	require.NoError(t, err)
	assert.Equal(t, "ghs_test_token_123", token)
	assert.Equal(t, int32(1), callCount.Load())

	// Second call should return cached token
	token, err = ts.Token(context.Background())
	require.NoError(t, err)
	assert.Equal(t, "ghs_test_token_123", token)
	assert.Equal(t, int32(1), callCount.Load(), "should use cached token")
}

func TestInstallationTokenSource_Token_RefreshOnExpiry(t *testing.T) {
	key := generateTestKey(t)
	var callCount atomic.Int32

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		count := callCount.Add(1)
		w.WriteHeader(http.StatusCreated)
		resp := installationTokenResponse{
			Token:     fmt.Sprintf("ghs_token_%d", count),
			ExpiresAt: time.Now().Add(5 * time.Minute), // expires in 5 min (< 10 min threshold)
		}
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	ts := NewInstallationTokenSource(12345, 67890, key, server.URL)

	// First call
	token1, err := ts.Token(context.Background())
	require.NoError(t, err)
	assert.Equal(t, "ghs_token_1", token1)

	// Second call should refresh because expiresAt - 10min is in the past
	token2, err := ts.Token(context.Background())
	require.NoError(t, err)
	assert.Equal(t, "ghs_token_2", token2)
	assert.Equal(t, int32(2), callCount.Load(), "should have refreshed")
}

func TestInstallationTokenSource_Token_HTTPError(t *testing.T) {
	key := generateTestKey(t)

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"message":"Bad credentials"}`))
	}))
	defer server.Close()

	ts := NewInstallationTokenSource(12345, 67890, key, server.URL)

	_, err := ts.Token(context.Background())
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "status 401")
}

func TestInstallationTokenSource_Token_Concurrent(t *testing.T) {
	key := generateTestKey(t)
	var callCount atomic.Int32

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		callCount.Add(1)
		// Simulate a small delay
		time.Sleep(10 * time.Millisecond)
		w.WriteHeader(http.StatusCreated)
		resp := installationTokenResponse{
			Token:     "ghs_concurrent_token",
			ExpiresAt: time.Now().Add(1 * time.Hour),
		}
		json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	ts := NewInstallationTokenSource(12345, 67890, key, server.URL)

	var wg sync.WaitGroup
	for i := 0; i < 10; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			token, err := ts.Token(context.Background())
			assert.NoError(t, err)
			assert.Equal(t, "ghs_concurrent_token", token)
		}()
	}
	wg.Wait()

	// All concurrent calls should have resulted in only one API call
	assert.Equal(t, int32(1), callCount.Load(), "concurrent calls should not cause multiple refreshes")
}
