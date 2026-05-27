---
name: "runtime-linking"
description: "Plan, validate, dry-run, and execute approval-gated symlink or marketplace activation of canonical AI tooling primitives into local runtimes without overwriting scattered originals. Use when connecting this source graph to Codex, Agents, Claude, Copilot, or other runtime directories, updating runtime link manifests, checking collisions, or preparing backup-then-link activation work."
---

# Runtime Linking

Use this skill to expose canonical primitives from this repository to local
agent runtimes. The default mode is planning and validation; filesystem writes
require explicit user approval and a current manifest.

## Operating Contract

- Treat `garden/manifests/runtime-links.json` as the activation plan.
- Do not write to runtime paths unless the user explicitly asks for activation.
- Never replace an existing file or symlink until its content is inspected,
  imported if needed, and backed up according to the manifest collision policy.
- Prefer marketplace import for referential plugins when a runtime supports it.
- Prefer symlinking children over replacing an entire runtime root.
- Keep canonical primitives in this repository; runtime paths are projections.
- Validate all structured data with `node scripts/validate-manifests.mjs`.

## Workflow

1. Read the plan.
   Inspect `garden/manifests/runtime-links.json` and confirm the relevant entry,
   source path, target path, strategy, status, and collision policy.

2. Verify source readiness.
   Confirm the source path exists, inventory is current, and the primitive or
   plugin has promotion provenance when it came from scattered sources.

3. Inspect target state.
   Check whether the target path exists, whether it is a symlink, and where it
   resolves. Do not assume top-level runtime folders are safe to replace.

4. Compare before linking.
   For existing target children, compare path, type, digest, and source
   provenance. Import meaningful deltas before any replacement is considered.

5. Prepare activation.
   Build a concrete operation list: create parent directories, back up existing
   paths, create symlinks, and verify `readlink` output. Keep this list out of
   `cleanup-ledger.json` unless cleanup of an original path is also being
   proposed.

6. Execute only with approval.
   If activation was requested, run the smallest safe operation set and verify
   every target. If activation was not requested, stop at the reviewed plan.

7. Refresh evidence.
   Run validation and inventory checks after changing manifests or after
   runtime paths are linked and should be visible to inventory.

## Reference Routing

- Load [manifest.md](references/manifest.md) when editing or interpreting
  `garden/manifests/runtime-links.json`.
- Load [collision-safety.md](references/collision-safety.md) before proposing
  backup or replacement of any existing runtime path.
- Load [runtime-targets.md](references/runtime-targets.md) when selecting the
  correct runtime target and activation strategy.

## Completion Criteria

- The activation plan is schema-valid and points at existing source paths.
- Runtime writes were either not performed, or were explicitly approved.
- Existing target content was inspected before any replacement.
- Backups and rollback paths exist for every replaced target.
- Post-activation validation proves symlinks resolve as intended.
