---
name: "pull-request-lifecycle"
description: "Manage a change from local branch through pull request and green checks. Use when the user asks to create a feature branch, commit and push work, open or raise a pull request, convert a draft PR to ready for review, monitor checks, babysit CI until green, or repair a PR after failed checks."
---

# Pull Request Lifecycle

Use this skill to carry code work across the version-control boundary without
losing evidence. It composes local Git hygiene, GitHub PR operations, and CI
triage; it must remain useful even when a host-specific GitHub connector is not
available.

Related primitives in this repository:

- `skills/git-change-flow`
- `skills/github-ci-operations`

## Operating Contract

- Start by reading the nearest repo instructions and `git status --short
  --branch`.
- Treat unrelated dirty files as user work. Do not stage, stash, rebase, amend,
  or overwrite them unless the user asks for that exact operation.
- Branch before commit-worthy work when currently on `main`, `master`, `trunk`,
  or another shared branch.
- Run the narrowest local validation that corresponds to the changed surface
  before pushing.
- Open PRs from the actual head branch and target the nearest intended base, not
  `main` by reflex when a stacked branch is visible.
- Do not mark a PR ready for review until the branch has a focused diff,
  validation evidence, and no known deterministic failures.
- Babysit checks by reading live check state, logs, and annotations. Fix the
  owning source instead of rerunning deterministic failures.
- Never claim a PR is green until `gh pr checks` or the repository's check
  surface reports passing, skipped, or neutral terminal states for required
  checks.

## Workflow

1. Orient.
   Identify the current branch, upstream, base branch, changed files, existing
   PR number if any, and requested end state: local branch, pushed branch, draft
   PR, ready PR, or green PR.

2. Isolate the branch.
   If the task is publishable and the checkout is on a shared branch, create a
   focused topic branch. If unrelated dirty files exist, keep staging and diffs
   path-specific.

3. Validate locally.
   Run the narrowest command that proves the changed behavior. For Kotlin or
   Gradle changes, prefer `./gradlew :module:test`, `./gradlew :module:check`,
   or the repository's checked-in wrapper before broad `check`.

4. Commit and push.
   Stage only files in scope. Run `git diff --cached --check`, commit with the
   repository's convention, then push the branch with upstream tracking when
   publication is requested.

5. Open or update the PR.
   Use `gh pr view --json number,url,headRefName,headRefOid,baseRefName,isDraft`
   to detect an existing PR. If none exists, open one with purpose, changed
   surface, validation evidence, and residual risk. Prefer draft until local
   evidence is complete.

6. Watch checks.
   Use `gh pr checks <pr> --json name,state,bucket,link,startedAt,completedAt`
   and, for failing GitHub Actions runs, `gh run view` with logs. Separate
   product/test failures, generated drift, dependency setup, permissions,
   secrets, cache, and flaky behavior.

7. Repair or hand off.
   For deterministic failures, patch the owning source, rerun the local
   equivalent, push a new commit, and re-read checks. For pending checks, report
   the pending names and URLs. For external or inaccessible checks, state the
   exact signal that could not be verified.

## Check Green Loop

When asked to babysit until green:

1. Poll check state only after a push or rerun changes the remote state.
2. Treat `failure`, `cancelled`, `timed_out`, and required `action_required` as
   stop conditions that need diagnosis.
3. Treat `pending`, `queued`, and `in_progress` as wait states only while the
   run is still moving.
4. Stop and report if the same blocking condition recurs after three repair
   attempts or if a required secret, permission, or environment approval is not
   available locally.

## Evidence To Report

Report:

- branch name, base branch, PR number or URL, and head SHA;
- files staged or intentionally left unstaged;
- local validation commands and outcomes;
- remote check names, states, and failing run URLs when relevant;
- what remains pending or externally blocked.

## Completion Criteria

- The requested branch, commit, push, PR, or ready-for-review state exists.
- User-owned unrelated work remains untouched.
- Local validation was run or the missing tool/environment is named.
- Remote checks were read after the latest push, and green claims are backed by
  check state rather than expectation.
