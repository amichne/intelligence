# Git Recovery

Use this reference before running commands that can move refs, rewrite history,
or hide working-tree state.

## Ground Rules

- Do not run `git reset --hard`, `git checkout -- <path>`,
  `git clean -fd`, forced push, interactive rebase, or branch deletion without
  explicit user approval.
- Before recovery, capture:
  - current branch and commit with `git status --short --branch`;
  - relevant diff with `git diff`;
  - staged diff with `git diff --cached`;
  - recent refs with `git log --oneline --decorate -n 20`.
- If the worktree has unrelated user changes, isolate the recovery to the
  affected paths or ask before broad state changes.

## Conflict Resolution

- Inspect conflicted files directly; do not assume one side is correct.
- Preserve generated-file boundaries: regenerate when practical instead of
  hand-merging generated output.
- Run the relevant tests or generators after resolving conflicts.
- Commit conflict resolution separately when that is the repo's pattern.

## Stash

- Prefer path-specific staging or temporary commits over stash when the worktree
  is mixed.
- If stash is appropriate, name it with context:
  `git stash push -m "wip: <reason>" -- <paths>`.
- Immediately verify with `git stash list` and record the stash name in the
  handoff.

## Reflog

- Use `git reflog` to find lost commits or a previous branch tip.
- Create a recovery branch before inspecting old state:
  `git branch recovery/<short-name> <sha>`.
- Do not reset the current branch to a reflog entry until the target SHA and
  consequences are clear.
