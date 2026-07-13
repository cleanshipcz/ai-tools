package githubapp

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func generateTestPEM(t *testing.T) (string, *rsa.PrivateKey) {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	pemBytes := pem.EncodeToMemory(&pem.Block{
		Type:  "RSA PRIVATE KEY",
		Bytes: x509.MarshalPKCS1PrivateKey(key),
	})
	return string(pemBytes), key
}

func generateTestPKCS8PEM(t *testing.T) (string, *rsa.PrivateKey) {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	pkcs8Bytes, err := x509.MarshalPKCS8PrivateKey(key)
	require.NoError(t, err)
	pemBytes := pem.EncodeToMemory(&pem.Block{
		Type:  "PRIVATE KEY",
		Bytes: pkcs8Bytes,
	})
	return string(pemBytes), key
}

// clearAppEnv unsets all GitHub App env vars to prevent test interference.
func clearAppEnv(t *testing.T) {
	t.Helper()
	for _, key := range []string{
		"GITHUB_APP_ID", "GITHUB_APP_INSTALLATION_ID", "GITHUB_APP_PRIVATE_KEY",
		"GITHUB_APP_ORGS",
		"GITHUB_APP_ORG_ORGA_APP_ID", "GITHUB_APP_ORG_ORGA_INSTALLATION_ID", "GITHUB_APP_ORG_ORGA_PRIVATE_KEY",
		"GITHUB_APP_ORG_ORGB_APP_ID", "GITHUB_APP_ORG_ORGB_INSTALLATION_ID", "GITHUB_APP_ORG_ORGB_PRIVATE_KEY",
	} {
		os.Unsetenv(key)
	}
}

func TestParsePrivateKey_PKCS1(t *testing.T) {
	pemData, expected := generateTestPEM(t)
	key, err := ParsePrivateKey(pemData)
	require.NoError(t, err)
	assert.Equal(t, expected.D, key.D)
}

func TestParsePrivateKey_PKCS8(t *testing.T) {
	pemData, expected := generateTestPKCS8PEM(t)
	key, err := ParsePrivateKey(pemData)
	require.NoError(t, err)
	assert.Equal(t, expected.D, key.D)
}

func TestParsePrivateKey_InvalidPEM(t *testing.T) {
	_, err := ParsePrivateKey("not a pem")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "no PEM block found")
}

func TestParsePrivateKey_UnsupportedType(t *testing.T) {
	pemBytes := pem.EncodeToMemory(&pem.Block{
		Type:  "EC PRIVATE KEY",
		Bytes: []byte("fake"),
	})
	_, err := ParsePrivateKey(string(pemBytes))
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unsupported PEM block type")
}

func TestLoadFromEnv_NothingConfigured(t *testing.T) {
	clearAppEnv(t)
	_, _, err := LoadFromEnv()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "no GitHub App configured")
}

func TestLoadFromEnv_DefaultAppOnly(t *testing.T) {
	clearAppEnv(t)
	pemData, _ := generateTestPEM(t)
	t.Setenv("GITHUB_APP_ID", "12345")
	t.Setenv("GITHUB_APP_INSTALLATION_ID", "67890")
	t.Setenv("GITHUB_APP_PRIVATE_KEY", pemData)

	appCfg, orgConfigs, err := LoadFromEnv()
	require.NoError(t, err)
	assert.Equal(t, int64(12345), appCfg.AppID)
	assert.Equal(t, int64(67890), appCfg.InstallationID)
	assert.NotNil(t, appCfg.PrivateKey)
	assert.Empty(t, orgConfigs)
}

func TestLoadFromEnv_DefaultAppPartialConfig(t *testing.T) {
	clearAppEnv(t)
	t.Setenv("GITHUB_APP_ID", "12345")
	// Missing INSTALLATION_ID and PRIVATE_KEY

	_, _, err := LoadFromEnv()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "GITHUB_APP_INSTALLATION_ID is required")
}

func TestLoadFromEnv_PerOrgApps(t *testing.T) {
	clearAppEnv(t)
	pemA, _ := generateTestPEM(t)
	pemB, _ := generateTestPEM(t)

	t.Setenv("GITHUB_APP_ORGS", "orgA;orgB")
	t.Setenv("GITHUB_APP_ORG_ORGA_APP_ID", "111")
	t.Setenv("GITHUB_APP_ORG_ORGA_INSTALLATION_ID", "222")
	t.Setenv("GITHUB_APP_ORG_ORGA_PRIVATE_KEY", pemA)
	t.Setenv("GITHUB_APP_ORG_ORGB_APP_ID", "333")
	t.Setenv("GITHUB_APP_ORG_ORGB_INSTALLATION_ID", "444")
	t.Setenv("GITHUB_APP_ORG_ORGB_PRIVATE_KEY", pemB)

	appCfg, orgConfigs, err := LoadFromEnv()
	require.NoError(t, err)
	assert.Nil(t, appCfg, "no default app configured")
	require.Len(t, orgConfigs, 2)
	assert.Equal(t, "orgA", orgConfigs[0].Owner)
	assert.Equal(t, int64(111), orgConfigs[0].AppID)
	assert.Equal(t, int64(222), orgConfigs[0].InstallationID)
	assert.Equal(t, "orgB", orgConfigs[1].Owner)
	assert.Equal(t, int64(333), orgConfigs[1].AppID)
	assert.Equal(t, int64(444), orgConfigs[1].InstallationID)
}

func TestLoadFromEnv_DefaultPlusPerOrg(t *testing.T) {
	clearAppEnv(t)
	pemDefault, _ := generateTestPEM(t)
	pemA, _ := generateTestPEM(t)

	t.Setenv("GITHUB_APP_ID", "100")
	t.Setenv("GITHUB_APP_INSTALLATION_ID", "200")
	t.Setenv("GITHUB_APP_PRIVATE_KEY", pemDefault)
	t.Setenv("GITHUB_APP_ORGS", "orgA")
	t.Setenv("GITHUB_APP_ORG_ORGA_APP_ID", "111")
	t.Setenv("GITHUB_APP_ORG_ORGA_INSTALLATION_ID", "222")
	t.Setenv("GITHUB_APP_ORG_ORGA_PRIVATE_KEY", pemA)

	appCfg, orgConfigs, err := LoadFromEnv()
	require.NoError(t, err)
	assert.NotNil(t, appCfg)
	assert.Equal(t, int64(100), appCfg.AppID)
	require.Len(t, orgConfigs, 1)
	assert.Equal(t, "orgA", orgConfigs[0].Owner)
}

func TestLoadFromEnv_PerOrgMissingField(t *testing.T) {
	clearAppEnv(t)
	t.Setenv("GITHUB_APP_ORGS", "orgA")
	t.Setenv("GITHUB_APP_ORG_ORGA_APP_ID", "111")
	// Missing INSTALLATION_ID and PRIVATE_KEY

	_, _, err := LoadFromEnv()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "GITHUB_APP_ORG_ORGA_INSTALLATION_ID is required")
}

func TestLoadFromEnv_PrivateKeyFromFile(t *testing.T) {
	clearAppEnv(t)
	pemData, _ := generateTestPEM(t)

	tmpDir := t.TempDir()
	keyFile := filepath.Join(tmpDir, "test.pem")
	err := os.WriteFile(keyFile, []byte(pemData), 0600)
	require.NoError(t, err)

	t.Setenv("GITHUB_APP_ID", "12345")
	t.Setenv("GITHUB_APP_INSTALLATION_ID", "67890")
	t.Setenv("GITHUB_APP_PRIVATE_KEY", keyFile)

	appCfg, _, err := LoadFromEnv()
	require.NoError(t, err)
	assert.Equal(t, int64(12345), appCfg.AppID)
	assert.NotNil(t, appCfg.PrivateKey)
}
