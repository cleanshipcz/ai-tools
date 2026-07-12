# Team Lead Agent — Design

**Date:** 2026-07-12
**Status:** Approved
**Artifact:** `05_agents/team-lead.yml` (standard `AgentManifest`, no engine changes)

## Purpose

A team-lead orchestrator agent that delivers a full scope of change end-to-end: it plans, spawns specialist agents (developers, reviewers, documenters, analysts), enforces quality gates, and delivers the result on a feature branch. Primary mode is **unattended delivery**; interactive mode exists as the hardening/debugging harness. Orchestration is model-driven in a single Claude Code main loop — the team lead decides staffing and looping with judgment rather than following a fixed pipeline.

## Key decisions and rationale

| Decision | Rationale |
| --- | --- |
| Plain `AgentManifest` in `05_agents/`, no engine change | `claude --agent team-lead` runs the agent as the main loop with the Agent tool retained (verified against Claude Code docs; ClaudeAdapter emits no `tools:` frontmatter, so all tools are inherited). Headless via `claude --agent team-lead -p "..."`. Since Claude Code v2.1.172 even nested subagent spawning works (depth limit 5). |
| Team lead plans itself (no delegated planner) | It holds the whole context; a delegated planner re-introduces the over-prescription problem that got `planner-execution` deprecated. |
| Mandatory spine + judgment staffing | A fully fixed pipeline is recipes again; fully open staffing is unauditable unattended. The spine is what must be trustable blind; judgment covers the rest with per-decision justification. |
| Branch out, commit, never merge | Safety boundary for unattended runs. Push/PR details deliberately deferred. |
| Team lead never writes code itself | Keeps the orchestrator auditable and the spine unskippable. |

## Entry point

One free-form argument: `claude --agent team-lead -p "<feature description | path to spec .md>"`.
First step of the prompt: if the argument resolves to a readable file, read it as the spec; otherwise the argument is the spec.

Gate behavior: interactive sessions pause for plan approval before implementing; headless runs proceed on objective evidence only (build/tests/lint pass, review findings resolved or waived with rationale).

## Delivery process

Mandatory spine (never skippable): **plan → implement → review loop → verify**. Full sequence:

1. **Intake** — read the spec, briefly survey the affected codebase, derive acceptance criteria if the spec lacks them.
2. **Branch** — create `feature/<slug>` (or `bugfix/<slug>`) off the current branch; all work happens there.
3. **Plan** — done by the team lead itself: scope, acceptance criteria, risks, and a staffing decision list where every optional specialist engaged **or skipped** gets a one-line justification. Plans state WHAT and WHY — never prescribe classes, methods, or file structure.
4. **Implement** — spawn `developer-feature` or `developer-bugfix` with spec, acceptance criteria, and plan brief. Optional analysts (`analyst-codebase`, `analyst-security`, …) run before this when the plan justified them.
5. **Review loop** — `reviewer-code` always; optional reviewers (`reviewer-security`, `reviewer-api`, `reviewer-architecture`) per staffing decisions. Findings go to a developer agent to fix, then re-review. Max 3 iterations. Every finding ends resolved or waived with written rationale.
6. **Verify** — run the target project's own verification commands (from its CLAUDE.md/rules; e.g. `./gradlew clean build` incl. ktlint and detekt in this repo). Failures route back into the review loop and count against the iteration budget.
7. **Document** — judgment-based: `documenter-*` agents only when public surface or user-facing behavior changed.
8. **Deliver** — final commit(s) on the branch plus a delivery report. Never merge, never push to a protected branch. Stop.

## Context passing

Each subagent receives a self-contained written brief in its spawn prompt (spec excerpt, acceptance criteria, relevant plan section). No shared mutable state is assumed between agents.

Run artifacts — plan, review findings, waivers, delivery/handoff report — live in `.delivery/<run-slug>/` committed on the feature branch. Morning review = open branch, read `.delivery/*/report.md`, read the diff. Whether `.delivery/` survives the eventual merge is deferred with the other git details.

## Failure policy

If any gate cannot be satisfied within its iteration budget (3 iterations), the team lead stops. It does not lower the bar, waive its own blockers, or silently degrade scope. It commits the current state plus a **handoff report**: what was attempted, what passed, what is failing and why, recommended next action. A partial branch with an honest report is the success criterion for a failed run.

## Constraints (manifest `constraints` list)

- Never implement, fix, or edit production code directly — all code changes go through developer agents.
- Never skip the review or verification gates.
- Never merge, never push to a protected branch, never force-push.
- Never disable or delete tests to make a gate pass.
- Justify every optional-agent decision (engaged or skipped) in one line in the plan.
- Briefs and plans describe outcomes and constraints, not implementations.
- On unsatisfiable gates: stop and write a handoff report; never push through.

## Manifest composition notes

- `rulesets`: `base` plus delegation/authoring-relevant rulesets only — **not** coding/testing rulesets (those belong to the spawned specialists).
- Because `--agent` replaces the default system prompt entirely, the manifest prompt must be self-sufficient: full methodology, gate policy, failure handling.

## Validation

Harden interactively first: pick a small real feature, run `claude --agent team-lead` in a target project, and watch the staffing decisions and gate behavior. Then run the same spec headless (`-p`) and compare. Repeat until the judgment is trusted.
