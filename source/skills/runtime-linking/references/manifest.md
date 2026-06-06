# Runtime Link Manifest

Use this reference when editing `garden/manifests/runtime-links.json`.

## Ownership

The manifest records planned runtime activation surfaces. It is not an
installer log and does not grant approval to write files.

Each entry names:

- `runtime`: target runtime family.
- `sourcePath`: canonical source in this repository.
- `targetPath`: runtime path or import target.
- `primitiveTypes`: primitive families exposed by the entry.
- `strategy`: marketplace import, child symlinks, file symlink, or tree symlink.
- `status`: readiness of the entry.
- `requiresApproval`: whether activation needs explicit user approval.
- `collisionPolicy`: what to do when the target already exists.
- `notes`: target-specific caution or evidence.

## Status Rules

- `READY`: source exists and activation shape is known, but approval may still
  be required.
- `REVIEW_REQUIRED`: target state or collision risk must be inspected first.
- `PLANNED`: the idea belongs in the graph, but more runtime evidence is
  needed before activation.

## Validation

`runtime-links.json` is governed by
`garden/schemas/intelligence/runtime-links.schema.json`.

Run:

```sh
node scripts/validate-manifests.mjs
```
