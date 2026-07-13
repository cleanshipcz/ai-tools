package githubapp

// BuildTokenSource constructs a TokenSource from the app config and org-specific configs.
// defaultCfg is the fallback app credentials (may be nil if all orgs are explicit).
// orgConfigs holds per-org app credentials (each org has its own GitHub App + key).
func BuildTokenSource(defaultCfg *AppConfig, orgConfigs []OrgAppConfig, apiBaseURL string) TokenSource {
	if defaultCfg == nil && len(orgConfigs) == 0 {
		panic("BuildTokenSource called with no app config — config validation should prevent this")
	}

	// Single default app with no per-org overrides = simple single-org mode
	if defaultCfg != nil && len(orgConfigs) == 0 {
		return NewInstallationTokenSource(
			defaultCfg.AppID,
			defaultCfg.InstallationID,
			defaultCfg.PrivateKey,
			apiBaseURL,
		)
	}

	// Multi-org mode: build per-org sources, each with its own app credentials
	var defaultSource TokenSource
	if defaultCfg != nil {
		defaultSource = NewInstallationTokenSource(
			defaultCfg.AppID,
			defaultCfg.InstallationID,
			defaultCfg.PrivateKey,
			apiBaseURL,
		)
	}

	multi := NewMultiOrgTokenSource(defaultSource)
	for _, org := range orgConfigs {
		source := NewInstallationTokenSource(
			org.AppID,
			org.InstallationID,
			org.PrivateKey,
			apiBaseURL,
		)
		multi.AddOrg(org.Owner, source)
	}

	return multi
}
