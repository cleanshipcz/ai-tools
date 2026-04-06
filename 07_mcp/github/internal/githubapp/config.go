package githubapp

import (
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"os"
	"strconv"
	"strings"
)

// AppConfig holds the parsed GitHub App credentials for a single app/installation.
type AppConfig struct {
	AppID          int64
	InstallationID int64
	PrivateKey     *rsa.PrivateKey
}

// OrgAppConfig maps an org/owner name to its own GitHub App credentials.
// Each org can have a completely separate GitHub App with its own key.
type OrgAppConfig struct {
	Owner          string
	AppID          int64
	InstallationID int64
	PrivateKey     *rsa.PrivateKey
}

// LoadFromEnv reads GitHub App configuration from environment variables.
//
// Default app (single-org or fallback):
//   - GITHUB_APP_ID: the GitHub App's ID
//   - GITHUB_APP_INSTALLATION_ID: the installation ID
//   - GITHUB_APP_PRIVATE_KEY: PEM-encoded RSA private key (inline) or path to PEM file
//
// Per-org apps (multi-org with separate GitHub Apps per org):
//   - GITHUB_APP_ORG_<OWNER>_APP_ID: app ID for this org
//   - GITHUB_APP_ORG_<OWNER>_INSTALLATION_ID: installation ID for this org
//   - GITHUB_APP_ORG_<OWNER>_PRIVATE_KEY: PEM key (inline or file path) for this org
//   - GITHUB_APP_ORGS: semicolon-separated list of org names to load (e.g. "blahami2;cleanshipcz")
//
// At least one of the default app or GITHUB_APP_ORGS must be configured.
func LoadFromEnv() (*AppConfig, []OrgAppConfig, error) {
	defaultCfg, err := loadDefaultApp()
	if err != nil && !isNotConfigured(err) {
		return nil, nil, fmt.Errorf("default app config: %w", err)
	}

	orgConfigs, orgErr := loadOrgApps()
	if orgErr != nil {
		return nil, nil, orgErr
	}

	if defaultCfg == nil && len(orgConfigs) == 0 {
		return nil, nil, fmt.Errorf("no GitHub App configured: set GITHUB_APP_ID + GITHUB_APP_INSTALLATION_ID + GITHUB_APP_PRIVATE_KEY for the default app, or GITHUB_APP_ORGS for per-org apps")
	}

	return defaultCfg, orgConfigs, nil
}

// errNotConfigured is returned when required env vars are not set (vs. set but invalid).
type errNotConfigured struct{ msg string }

func (e *errNotConfigured) Error() string { return e.msg }

func isNotConfigured(err error) bool {
	_, ok := err.(*errNotConfigured)
	return ok
}

func loadDefaultApp() (*AppConfig, error) {
	appIDStr := os.Getenv("GITHUB_APP_ID")
	installIDStr := os.Getenv("GITHUB_APP_INSTALLATION_ID")
	keyData := os.Getenv("GITHUB_APP_PRIVATE_KEY")

	// If none are set, it's just not configured (not an error)
	if appIDStr == "" && installIDStr == "" && keyData == "" {
		return nil, &errNotConfigured{msg: "default app not configured"}
	}

	// If some are set but not all, that's an error
	if appIDStr == "" {
		return nil, fmt.Errorf("GITHUB_APP_ID is required")
	}
	if installIDStr == "" {
		return nil, fmt.Errorf("GITHUB_APP_INSTALLATION_ID is required")
	}
	if keyData == "" {
		return nil, fmt.Errorf("GITHUB_APP_PRIVATE_KEY is required")
	}

	appID, err := strconv.ParseInt(appIDStr, 10, 64)
	if err != nil {
		return nil, fmt.Errorf("GITHUB_APP_ID must be a valid integer: %w", err)
	}

	installID, err := strconv.ParseInt(installIDStr, 10, 64)
	if err != nil {
		return nil, fmt.Errorf("GITHUB_APP_INSTALLATION_ID must be a valid integer: %w", err)
	}

	privateKey, err := loadPrivateKeyFromData(keyData)
	if err != nil {
		return nil, fmt.Errorf("GITHUB_APP_PRIVATE_KEY: %w", err)
	}

	return &AppConfig{
		AppID:          appID,
		InstallationID: installID,
		PrivateKey:     privateKey,
	}, nil
}

func loadOrgApps() ([]OrgAppConfig, error) {
	orgsStr := os.Getenv("GITHUB_APP_ORGS")
	if orgsStr == "" {
		return nil, nil
	}

	var configs []OrgAppConfig
	orgs := strings.Split(orgsStr, ";")
	for _, org := range orgs {
		org = strings.TrimSpace(org)
		if org == "" {
			continue
		}

		cfg, err := loadOrgApp(org)
		if err != nil {
			return nil, fmt.Errorf("org %q: %w", org, err)
		}
		configs = append(configs, *cfg)
	}

	return configs, nil
}

func loadOrgApp(owner string) (*OrgAppConfig, error) {
	prefix := "GITHUB_APP_ORG_" + strings.ToUpper(owner) + "_"

	appIDStr := os.Getenv(prefix + "APP_ID")
	if appIDStr == "" {
		return nil, fmt.Errorf("%sAPP_ID is required", prefix)
	}
	appID, err := strconv.ParseInt(appIDStr, 10, 64)
	if err != nil {
		return nil, fmt.Errorf("%sAPP_ID must be a valid integer: %w", prefix, err)
	}

	installIDStr := os.Getenv(prefix + "INSTALLATION_ID")
	if installIDStr == "" {
		return nil, fmt.Errorf("%sINSTALLATION_ID is required", prefix)
	}
	installID, err := strconv.ParseInt(installIDStr, 10, 64)
	if err != nil {
		return nil, fmt.Errorf("%sINSTALLATION_ID must be a valid integer: %w", prefix, err)
	}

	keyData := os.Getenv(prefix + "PRIVATE_KEY")
	if keyData == "" {
		return nil, fmt.Errorf("%sPRIVATE_KEY is required", prefix)
	}
	privateKey, err := loadPrivateKeyFromData(keyData)
	if err != nil {
		return nil, fmt.Errorf("%sPRIVATE_KEY: %w", prefix, err)
	}

	return &OrgAppConfig{
		Owner:          owner,
		AppID:          appID,
		InstallationID: installID,
		PrivateKey:     privateKey,
	}, nil
}

func loadPrivateKeyFromData(keyData string) (*rsa.PrivateKey, error) {
	// If it doesn't look like PEM, treat it as a file path
	if !strings.HasPrefix(strings.TrimSpace(keyData), "-----BEGIN") {
		fileBytes, err := os.ReadFile(keyData)
		if err != nil {
			return nil, fmt.Errorf("failed to read private key file %q: %w", keyData, err)
		}
		keyData = string(fileBytes)
	}

	return ParsePrivateKey(keyData)
}

// ParsePrivateKey decodes a PEM-encoded RSA private key.
// It supports both PKCS1 ("RSA PRIVATE KEY") and PKCS8 ("PRIVATE KEY") formats.
func ParsePrivateKey(pemData string) (*rsa.PrivateKey, error) {
	block, _ := pem.Decode([]byte(pemData))
	if block == nil {
		return nil, fmt.Errorf("no PEM block found in private key data")
	}

	switch block.Type {
	case "RSA PRIVATE KEY":
		return x509.ParsePKCS1PrivateKey(block.Bytes)
	case "PRIVATE KEY":
		key, err := x509.ParsePKCS8PrivateKey(block.Bytes)
		if err != nil {
			return nil, fmt.Errorf("failed to parse PKCS8 private key: %w", err)
		}
		rsaKey, ok := key.(*rsa.PrivateKey)
		if !ok {
			return nil, fmt.Errorf("PKCS8 key is not RSA")
		}
		return rsaKey, nil
	default:
		return nil, fmt.Errorf("unsupported PEM block type: %s", block.Type)
	}
}
