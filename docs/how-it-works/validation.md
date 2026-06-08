# Validation

Validation starts from the marketplace source graph. Run checks from the
repository root.

Validation happens in two layers.

## Layer 1: Canonical Source Integrity

Canonical artifacts in `source/` are the authority. We first validate syntax,
structure, and source references there so we can fail before projection.

| Check | What it protects |
|---|---|
| `.local/intelligence/bin/intelligence validate` | Runs repository checks from the Kotlin CLI and ensures source edits stay coherent across schema-backed surfaces. |
| `.local/intelligence/bin/intelligence validate --portable` | Runs the same checks without relying on host-local assumptions. |

This is the primary safety boundary: the source model must be valid before we
generate provider payloads.

### JSON and schema boundary

Every persisted JSON file should have schema, typed parser, generator, or an
equivalent assertion boundary.

```sh
.local/intelligence/bin/intelligence validate
```

## Hooks

Parse changed hook JSON and check executable scripts.

```sh
.local/intelligence/bin/intelligence validate --portable
bash -n source/hooks/*.sh
```

## Layer 2: Projection Safety And Runtime Compatibility

A source-valid model can still fail in a target projection. This layer verifies
provider mappings and adapter surfaces.

### Focused checks

Match the surface you changed with the tightest check.

| Changed Surface | Check |
|---|---|
| Hook shell entrypoints | `bash -n source/hooks/*.sh` |
| JSON hook assets | `python3 -m json.tool source/hooks/name.hook.json` |
| Marketplace or plugin manifests | `.local/intelligence/bin/intelligence validate` |
| Documentation site | `zensical build --clean` |
| Provider-specific projection | `.local/intelligence/bin/intelligence validate --portable --hydrated <provider-output-dir>` |

## Publish Proof Path

For publish flows, keep the same source-to-projection sequence: validate source,
materialize outputs, then verify hydration.

```sh
.local/intelligence/bin/intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace
.local/intelligence/bin/intelligence validate --portable --hydrated /tmp/intelligence-codex-marketplace
.local/intelligence/bin/intelligence marketplace publish-branch --provider codex --branch codex --no-push
.local/intelligence/bin/intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace
.local/intelligence/bin/intelligence validate --portable --hydrated /tmp/intelligence-github-marketplace
.local/intelligence/bin/intelligence marketplace publish-branch --provider github --branch github --no-push
```

If a provider check fails, repair projection logic or schema boundaries, then
regenerate outputs instead of patching generated payloads by hand.

## What This Protects

This two-layer model gives practical safety:

- It isolates invariant claims to source (`source/`), reducing drift from generated artifacts.
- It preserves the ability to support more targets through adapter projections.
- It makes failures explainable: source-model invalidation vs adapter/projection regression.
