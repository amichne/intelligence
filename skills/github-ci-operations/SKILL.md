---
name: github-ci-operations
description: Triage and fix GitHub Actions failures, maintain GitHub workflow files, inspect PR checks with gh, design CI jobs, and handle release-oriented GitHub automation. Use for GitHub CI, Actions YAML, failing PR checks, workflow dispatch, caches, artifacts, secrets, permissions, and release pipelines.
---

# GitHub CI Operations

Use this skill for GitHub-hosted workflow and CI work. Keep it focused on the
repository's actual workflows and logs; do not guess from generic CI patterns
when the live run, YAML, scripts, or package metadata can be inspected.

## Operating Contract

- Verify the repository and GitHub CLI context before using `gh`.
- Use live PR checks, workflow runs, logs, annotations, and job summaries as
  evidence when debugging failures.
- Treat non-GitHub providers as external checks unless the repo has local
  tooling for them. Report their details URL rather than scraping unrelated
  systems.
- Fix root causes in code, tests, scripts, workflow YAML, or docs. Do not paper
  over failures by disabling checks unless the user explicitly asks and the risk
  is stated.
- Ask before changing secrets, environment protection, deployment targets,
  release tags, or destructive workflow state.
- Re-run the smallest relevant local check first, then use GitHub checks to
  confirm remote behavior when available.

## Workflow

1. Resolve the target.
   Identify the repo, branch, PR number or URL, workflow file, run URL, and
   whether the user wants diagnosis, a fix, workflow authoring, release work, or
   publication.

2. Inspect live state.
   Use `gh auth status`, `gh pr view`, `gh pr checks`, `gh run view`,
   `gh run list`, and workflow YAML under `.github/workflows/` as appropriate.
   Capture the failing job name, command, error snippet, run URL, head SHA, and
   local file that owns the failing behavior.

3. Classify the failure.
   Separate product/test failures from CI-environment failures, dependency
   install failures, cache issues, permissions/secrets problems, flaky tests,
   generated-output drift, and release/tag mistakes.

4. Fix narrowly.
   Edit the owning source, test, generator, script, or workflow file. Preserve
   existing matrix, permissions, concurrency, cache, and artifact conventions
   unless they are the cause.

5. Validate.
   Run the local equivalent of the failing command. For workflow YAML changes,
   run `actionlint` when present, parse YAML with available tooling when not,
   and then recheck `gh pr checks` or the relevant run.

6. Hand off.
   Summarize the failed signal, root cause, files changed, checks run, and any
   remote check still pending.

## Reference Routing

- Load [ci-failure-triage.md](references/ci-failure-triage.md) for failed PR
  checks, failing runs, log extraction, or rerun decisions.
- Load [actions-workflows.md](references/actions-workflows.md) when creating or
  editing `.github/workflows/*.yml`.
- Load [release-flow.md](references/release-flow.md) for GitHub releases, tags,
  generated notes, artifacts, or release automation.

## Completion Criteria

- The failing GitHub signal is identified with a run or check URL when
  available.
- The fix targets the owning source rather than masking the symptom.
- Local validation passed, or the missing environment/tool is stated.
- Remote checks were re-read or the exact pending verification is documented.
