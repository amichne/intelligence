# Cleanup Handoff

Use this reference before proposing deletion or symlink replacement for scattered
source paths.

## Cleanup Boundary

Promotion does not imply cleanup. Cleanup starts only after:

- the canonical primitive exists and validates;
- the original source path is recorded in `manifests/promotions.json`;
- the generated inventory confirms the canonical replacement;
- duplicate digest or explicit review proves replacement equivalence;
- rollback or backup path is known;
- the user explicitly approves cleanup.

## Ledger Intent

`manifests/cleanup-ledger.json` is the approval gate. A future entry should
record:

- original `sourcePath`;
- `canonicalPath`;
- cleanup `decision`;
- verification evidence;
- rollback path.

Do not add ledger entries as a substitute for promotion evidence. Do not run
filesystem cleanup from this skill unless the user asks for cleanup work in the
current turn.

## Validation

`cleanup-ledger.json` is governed by
`schemas/intelligence/cleanup-ledger.schema.json`. Validate with:

```sh
node scripts/validate-manifests.mjs
```
