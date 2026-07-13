package ghmcp

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"

	"github.com/github/github-mcp-server/internal/githubapp"
	"github.com/github/github-mcp-server/pkg/github"
	"github.com/github/github-mcp-server/pkg/http/transport"
	"github.com/github/github-mcp-server/pkg/inventory"
	"github.com/github/github-mcp-server/pkg/lockdown"
	"github.com/github/github-mcp-server/pkg/observability"
	"github.com/github/github-mcp-server/pkg/observability/metrics"
	"github.com/github/github-mcp-server/pkg/raw"
	"github.com/github/github-mcp-server/pkg/translations"
	"github.com/github/github-mcp-server/pkg/utils"
	gogithub "github.com/google/go-github/v82/github"
	"github.com/shurcooL/githubv4"
)

// AppDeps implements github.ToolDependencies for GitHub App installation auth.
// Unlike BaseDeps (which holds pre-built clients with a static token), AppDeps
// constructs clients on demand using a TokenSource that handles JWT minting,
// token exchange, and caching with automatic refresh.
type AppDeps struct {
	tokenSource   githubapp.TokenSource
	apiHost       utils.APIHostResolver
	version       string
	lockdownMode  bool
	insidersMode  bool
	repoAccessOpts []lockdown.RepoAccessOption

	t                 translations.TranslationHelperFunc
	contentWindowSize int
	featureChecker    inventory.FeatureFlagChecker
	obsv              observability.Exporters
}

// Compile-time assertion that AppDeps implements ToolDependencies.
var _ github.ToolDependencies = (*AppDeps)(nil)

// NewAppDeps creates an AppDeps with the provided token source and configuration.
func NewAppDeps(
	tokenSource githubapp.TokenSource,
	apiHost utils.APIHostResolver,
	version string,
	lockdownMode bool,
	insidersMode bool,
	repoAccessOpts []lockdown.RepoAccessOption,
	t translations.TranslationHelperFunc,
	contentWindowSize int,
	featureChecker inventory.FeatureFlagChecker,
	obsv observability.Exporters,
) *AppDeps {
	return &AppDeps{
		tokenSource:       tokenSource,
		apiHost:           apiHost,
		version:           version,
		lockdownMode:      lockdownMode,
		insidersMode:      insidersMode,
		repoAccessOpts:    repoAccessOpts,
		t:                 t,
		contentWindowSize: contentWindowSize,
		featureChecker:    featureChecker,
		obsv:              obsv,
	}
}

// GetClient implements github.ToolDependencies.
// Uses TokenSourceTransport so the token is fetched dynamically per-request,
// supporting automatic refresh when the installation token expires.
func (d *AppDeps) GetClient(ctx context.Context) (*gogithub.Client, error) {
	baseRestURL, err := d.apiHost.BaseRESTURL(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get base REST URL: %w", err)
	}
	uploadURL, err := d.apiHost.UploadURL(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get upload URL: %w", err)
	}

	httpClient := &http.Client{
		Transport: &githubapp.TokenSourceTransport{
			Transport:   http.DefaultTransport,
			TokenSource: d.tokenSource,
		},
	}

	restClient := gogithub.NewClient(httpClient)
	restClient.UserAgent = fmt.Sprintf("github-mcp-server/%s", d.version)
	restClient.BaseURL = baseRestURL
	restClient.UploadURL = uploadURL
	return restClient, nil
}

// GetGQLClient implements github.ToolDependencies.
func (d *AppDeps) GetGQLClient(ctx context.Context) (*githubv4.Client, error) {
	gqlHTTPClient := &http.Client{
		Transport: &githubapp.TokenSourceTransport{
			Transport: &transport.GraphQLFeaturesTransport{
				Transport: http.DefaultTransport,
			},
			TokenSource: d.tokenSource,
		},
	}

	graphqlURL, err := d.apiHost.GraphqlURL(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get GraphQL URL: %w", err)
	}

	return githubv4.NewEnterpriseClient(graphqlURL.String(), gqlHTTPClient), nil
}

// GetRawClient implements github.ToolDependencies.
func (d *AppDeps) GetRawClient(ctx context.Context) (*raw.Client, error) {
	client, err := d.GetClient(ctx)
	if err != nil {
		return nil, err
	}

	rawURL, err := d.apiHost.RawURL(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get Raw URL: %w", err)
	}

	return raw.NewClient(client, rawURL), nil
}

// GetRepoAccessCache implements github.ToolDependencies.
func (d *AppDeps) GetRepoAccessCache(ctx context.Context) (*lockdown.RepoAccessCache, error) {
	if !d.lockdownMode {
		return nil, nil
	}

	gqlClient, err := d.GetGQLClient(ctx)
	if err != nil {
		return nil, err
	}

	return lockdown.GetInstance(gqlClient, d.repoAccessOpts...), nil
}

// GetT implements github.ToolDependencies.
func (d *AppDeps) GetT() translations.TranslationHelperFunc { return d.t }

// GetFlags implements github.ToolDependencies.
func (d *AppDeps) GetFlags(_ context.Context) github.FeatureFlags {
	return github.FeatureFlags{
		LockdownMode: d.lockdownMode,
		InsidersMode: d.insidersMode,
	}
}

// GetContentWindowSize implements github.ToolDependencies.
func (d *AppDeps) GetContentWindowSize() int { return d.contentWindowSize }

// Logger implements github.ToolDependencies.
func (d *AppDeps) Logger(_ context.Context) *slog.Logger {
	return d.obsv.Logger()
}

// Metrics implements github.ToolDependencies.
func (d *AppDeps) Metrics(ctx context.Context) metrics.Metrics {
	return d.obsv.Metrics(ctx)
}

// IsFeatureEnabled implements github.ToolDependencies.
func (d *AppDeps) IsFeatureEnabled(ctx context.Context, flagName string) bool {
	if d.featureChecker == nil || flagName == "" {
		return false
	}

	enabled, err := d.featureChecker(ctx, flagName)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Feature flag check error for %q: %v\n", flagName, err)
		return false
	}

	return enabled
}
