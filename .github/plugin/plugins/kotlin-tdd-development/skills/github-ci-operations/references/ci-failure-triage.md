# CI Failure Triage

Use this reference when a PR check or workflow run is failing.

## Useful Commands

Run these from the repository root.

```sh
gh auth status
gh pr view --json number,url,headRefName,headRefOid,baseRefName
gh pr checks <pr> --json name,state,bucket,link,startedAt,completedAt,workflow
gh run view <run-id> --json name,workflowName,status,conclusion,url,event,headBranch,headSha
gh run view <run-id> --log
```

If a `gh` JSON field is rejected, rerun with the fields reported by the local
CLI. The installed `gh` version can lag behind examples.

## Triage Order

1. Identify the failing check name and whether it is a GitHub Actions run.
2. Capture the run URL, job name, failing command, and concise error snippet.
3. Match the failing command to local scripts, tests, generators, or workflow
   steps.
4. Reproduce locally with the closest command.
5. Fix the owning source.
6. Re-run the local check.
7. Re-read PR checks or the run state.

## Failure Classes

- Product or test bug: fix code or tests and run the related suite.
- Generated drift: run the generator and commit generated outputs.
- Dependency install: inspect lockfiles, package manager setup, cache key, and
  tool versions.
- Permissions: inspect workflow `permissions`, fork PR behavior, token scopes,
  and protected environments.
- Secrets: confirm the workflow expects repository/environment secrets, but do
  not print secret values.
- Cache: check whether restore keys are hiding stale state; prefer precise keys
  tied to lockfiles.
- Flake: rerun once for evidence, then look for timing, ordering, network, or
  isolation failures before changing assertions.

## Reruns

- Rerun failed jobs only after deciding whether the failure might be flaky.
- Prefer `gh run rerun <run-id> --failed` for a suspected transient failure.
- Do not use reruns as the only fix for deterministic failures.
