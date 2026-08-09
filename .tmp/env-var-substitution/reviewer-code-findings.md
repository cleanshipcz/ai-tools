# Review findings ledger — reviewer-code, iteration 1

Branch: feature/env-var-substitution · Verdict: REQUEST_CHANGES · Routed to developer: 2026-08-09

| # | Severity | Location | Finding (short) | Disposition |
|---|---|---|---|---|
| 1 | MAJOR | ToolsEngine.kt:123 | deploy.directory substitution inside per-project loop → partial deploys before abort on unresolved variable | FIX — hoist validation before loop, atomic abort, report all unresolved names |
| 2 | MINOR | VariableResolver.kt:60-65 | Boundary hole: expansion+literal can assemble a literal `${...}` (DOLLAR="$" + "${DOLLAR}{HOME}/x") | FIX — final guard on assembled result |
| 3 | MINOR | ToolsEngineTest.kt / AiToolsCliIntegrationTest.kt | No test for relative expanded deploy.directory | FIX — add relative-expansion test |
| 4 | MINOR | ConfigServiceTest.kt | env_vars merge tested only in override half, not additive half | FIX — add local-only-variable and no-default-block tests |
| 5 | MINOR | EngineConfig.kt:13,16 | data class holds VariableResolver without equals/hashCode | FIX — make VariableResolver a data class |
| 6 | MINOR | VariableResolver.kt:48 | Caller's map reference stored without defensive copy | FIX — .toMap() in constructor |
| 7 | NIT | ToolsEngine.kt:119-123 | Projects with zero adapters skip deploy.directory validation | FIX — subsumed by finding 1's up-front validation |
| 8 | NIT | ConfigService.kt:114 | Unresolved-variable origin says "config.yml or config.local.yml" though the file is knowable | FIX — name the actual file |
| 9 | NIT | ConfigService.kt:87 | Merged manifest carries emptyMap() where siblings preserve null | WAIVED — no consumer observes the difference (sole consumer calls orEmpty()); purely internal round-trip asymmetry, not worth churn in this run |
| 10 | NIT | ServerApp.kt:33 | Server prints raw stack trace for VariableSubstitutionException (CLI translates it) | WAIVED — pre-existing server error-handling gap (same for FileNotFoundException); server UX polish is outside this run's scope; candidate follow-up |

Reviewer positives: Regex.replace lambda-overload semantics verified against the real engine; all regex edge cases pinned by parameterized tests; kaml strict/lenient asymmetry checked (no new crash modes); backward compatibility and both intentional design decisions confirmed; no test weakened.

Iteration 2: developer fixed 1-6 & 8; re-review confirmed all fixes (verdict APPROVE) with residual findings below.

# Review findings ledger — reviewer-code, iteration 2 (commit 57dc376)

Verdict: APPROVE · Residual findings routed / waived: 2026-08-09

| # | Severity | Location | Finding (short) | Disposition |
|---|---|---|---|---|
| 2.1 | MINOR | VariableResolver.kt:47-56 | data class equals/hashCode derive from the caller's live map, not the `declared` copy that drives resolution — equal resolvers can resolve differently | FIX (round 3) — hand-written equals/hashCode over declared + environment |
| 2.2 | MINOR | ToolsEngineTest.kt:203 | Atomicity regression test passes against the pre-fix code on this filesystem (unsorted discovery order reads the broken project first) | FIX (round 3) — make the test discriminating for any read order |
| 2.3 | NIT | VariableResolver.kt:108-109 | UnexpandedReferenceException names the surviving reference, not the declaring variable | WAIVED — documented deliberate trade-off; message quotes both original value and substituted result, so the culprit is derivable; no single culprit exists in the boundary case |
| 2.4 | NIT | ConfigService.kt:86,94-99 | Lambdas could be property references | WAIVED — pure style preference; current form equally readable, reviewer says so himself |
| 2.5 | NIT | ToolsEngine.kt:165 | Failures logged at ERROR and repeated in the aggregate message | WAIVED — matches the existing house style of exportOrCollectFailure/ExportFailedException; changing only the new path would create inconsistency |

Reviewer-noted intentional behavior change (not a finding): runs configuring no tools now fail on unresolved deploy.directory because validation is up-front — accepted as the more correct behavior.

Iteration 3 (final, commit 422fb09): developer fixed 2.1 & 2.2; targeted re-check verdict APPROVE.
- 2.1 confirmed fixed: caller's map no longer retained, hand-written equals/hashCode over declared + environment, discriminating scenario test incl. hashCode stability.
- 2.2 confirmed fixed: two-roots construction makes read order deterministic (loader chain verified order-preserving end-to-end); test proven discriminating; ToolsEngine.kt byte-identical to 57dc376.
- Unrequested toString change (names only, no values) accepted as defense-in-depth.
- 3.1 NIT (toString label `variables=` could read as full state; `variableNames=` clearer) — WAIVED: cosmetic, reasoning documented in KDoc, reviewer himself judged it not worth a commit.

REVIEW GATE: CLOSED — verdict APPROVE, all findings resolved or waived. Iterations used: 3 of 3.
