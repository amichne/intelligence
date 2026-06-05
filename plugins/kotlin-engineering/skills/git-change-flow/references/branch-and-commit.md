# Branch And Commit Flow

Use this reference when a task asks for a branch, commit, push, or publication
handoff.

## Branching

- Inspect `git status --short --branch` before branching.
- If on `main`, `master`, `trunk`, or another protected branch, create a topic
  branch before commit-worthy edits unless the user requested otherwise.
- Use the repository's branch prefix when documented. In this workspace, default
  to `feature/` unless the user requested a different prefix.
- Keep the branch name short, lowercase, and tied to the actual change.

## Staging

- Use `git diff --name-only` and `git diff --stat` to confirm scope.
- Use path-specific staging when unrelated files are dirty.
- Include generated manifests, lockfiles, snapshots, or docs outputs only when
  they are expected consequences of the change.
- Run `git diff --cached --check` after staging.

## Commit Message

- Follow the repo convention when present.
- Prefer conventional commits in this workspace, such as:
  - `feat: add documentation workflow plugin`
  - `fix: refresh primitive manifest validation`
  - `docs: clarify source-of-truth policy`
- Keep the subject about the user-facing or repository-facing change, not the
  implementation mechanics.

## Publication Handoff

- Push only when the user asked or the workflow requires publication.
- Use `git push -u origin <branch>` for a new branch.
- When opening a pull request, summarize:
  - purpose and behavior change;
  - notable files or generated outputs;
  - validation commands and results;
  - residual risks or checks that could not run.
