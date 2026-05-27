# GitHub Actions Workflows

Use this reference when creating or editing `.github/workflows/*.yml`.

## Discovery

- Read all relevant workflow files before adding a new one.
- Inspect package scripts, build files, lockfiles, Dockerfiles, release scripts,
  and repo docs to find the real validation commands.
- Reuse existing action versions, setup conventions, and cache style unless
  they are stale or broken.
- Check whether workflows run on `pull_request`, `push`, `workflow_dispatch`,
  tags, schedules, or reusable `workflow_call`.

## Workflow Shape

- Give each workflow a narrow purpose: CI, release, docs, deploy, or scheduled
  maintenance.
- Use explicit `permissions`; start with read-only and add write scopes only
  for jobs that need them.
- Use `concurrency` for branch or deployment workflows where duplicate runs are
  wasteful or risky.
- Use matrix jobs for supported versions or platforms; avoid copy-pasted jobs.
- Use package-manager setup actions with built-in caching when available.
- Upload artifacts only when a later job, release, or human debugging path uses
  them.
- Keep secrets in GitHub secrets or environments, never in workflow files.
- Prefer OIDC for cloud deployment when the repo already supports it.

## Editing Rules

- Change the smallest workflow surface that owns the behavior.
- Keep shell snippets short; move complex logic into versioned scripts.
- Pin third-party actions to stable versions already used by the repo, or
  explain a version change.
- Do not disable a required check to get a green PR unless the user asks and the
  replacement gate is clear.

## Validation

- Run `actionlint` when installed.
- If no workflow linter exists, at least parse YAML with local tooling and check
  indentation, event filters, permissions, expressions, and referenced scripts.
- Run the commands invoked by the changed jobs locally when practical.
- For docs or generated-output jobs, run the generator before local tests.
