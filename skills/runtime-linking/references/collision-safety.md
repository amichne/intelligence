# Collision Safety

Use this reference before changing a runtime target path.

## Required Checks

Before linking:

- expand `~` and resolve the target parent;
- check `test -e`, `test -L`, and `readlink` for the target path;
- compare existing target content against the canonical source when both exist;
- import useful deltas into the canonical primitive before replacement;
- choose the smallest link unit that avoids replacing unrelated runtime
  contents.

## Collision Policies

- `SKIP_EXISTING`: leave existing target content untouched.
- `FAIL_IF_EXISTS`: stop if any target path already exists.
- `BACKUP_THEN_LINK`: copy or move the existing target into a timestamped
  backup location before symlinking.

Do not use `BACKUP_THEN_LINK` without explicit approval in the current turn.

## Verification

After approved activation, verify:

```sh
readlink <target>
test -e <target>
```

For child-link plans, verify every created child and then rerun inventory if the
target root is part of `manifests/source-roots.json`.
