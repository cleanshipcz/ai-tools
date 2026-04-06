package githubapp

import (
	"context"
	"crypto/rsa"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// TokenSource provides GitHub installation access tokens.
type TokenSource interface {
	// Token returns a valid installation access token, refreshing if necessary.
	Token(ctx context.Context) (string, error)
}

// InstallationTokenSource mints JWTs and exchanges them for GitHub App
// installation access tokens. It caches the token and refreshes lazily
// when the cached token is within 10 minutes of expiry.
type InstallationTokenSource struct {
	appID          int64
	installationID int64
	privateKey     *rsa.PrivateKey
	apiBaseURL     string // e.g. "https://api.github.com"

	// httpClient is used for token exchange calls only (not for user API calls).
	httpClient *http.Client

	mu        sync.Mutex
	cached    string
	expiresAt time.Time
}

// NewInstallationTokenSource creates a token source for the given GitHub App installation.
// apiBaseURL should be the base GitHub API URL (e.g. "https://api.github.com").
func NewInstallationTokenSource(appID, installationID int64, privateKey *rsa.PrivateKey, apiBaseURL string) *InstallationTokenSource {
	return &InstallationTokenSource{
		appID:          appID,
		installationID: installationID,
		privateKey:     privateKey,
		apiBaseURL:     apiBaseURL,
		httpClient:     &http.Client{Timeout: 30 * time.Second},
	}
}

// Token returns a valid installation access token. It returns a cached token
// if it has more than 10 minutes of remaining lifetime; otherwise it mints
// a new JWT and exchanges it for a fresh installation token.
func (s *InstallationTokenSource) Token(ctx context.Context) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.cached != "" && time.Now().Before(s.expiresAt.Add(-10*time.Minute)) {
		return s.cached, nil
	}

	signedJWT, err := s.mintJWT()
	if err != nil {
		return "", fmt.Errorf("failed to mint JWT: %w", err)
	}

	token, expiresAt, err := s.exchangeForInstallationToken(ctx, signedJWT)
	if err != nil {
		return "", fmt.Errorf("failed to exchange JWT for installation token: %w", err)
	}

	s.cached = token
	s.expiresAt = expiresAt
	return s.cached, nil
}

func (s *InstallationTokenSource) mintJWT() (string, error) {
	now := time.Now()
	claims := jwt.RegisteredClaims{
		Issuer:    strconv.FormatInt(s.appID, 10),
		IssuedAt:  jwt.NewNumericDate(now.Add(-60 * time.Second)), // clock skew tolerance
		ExpiresAt: jwt.NewNumericDate(now.Add(9 * time.Minute)),   // GitHub max is 10min
	}
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	return token.SignedString(s.privateKey)
}

type installationTokenResponse struct {
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expires_at"`
}

func (s *InstallationTokenSource) exchangeForInstallationToken(ctx context.Context, jwtToken string) (string, time.Time, error) {
	baseURL := strings.TrimRight(s.apiBaseURL, "/")
	url := fmt.Sprintf("%s/app/installations/%d/access_tokens", baseURL, s.installationID)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, nil)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+jwtToken)
	req.Header.Set("Accept", "application/vnd.github+json")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode != http.StatusCreated {
		return "", time.Time{}, fmt.Errorf("GitHub API returned status %d: %s", resp.StatusCode, string(body))
	}

	var tokenResp installationTokenResponse
	if err := json.Unmarshal(body, &tokenResp); err != nil {
		return "", time.Time{}, fmt.Errorf("failed to parse response: %w", err)
	}

	if tokenResp.Token == "" {
		return "", time.Time{}, fmt.Errorf("empty token in response")
	}

	if tokenResp.ExpiresAt.IsZero() {
		return "", time.Time{}, fmt.Errorf("missing or zero expires_at in token response")
	}

	return tokenResp.Token, tokenResp.ExpiresAt, nil
}
