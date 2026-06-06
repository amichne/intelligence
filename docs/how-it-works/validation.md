# Validation

Validation is APM-first. Run checks from the repository root.

Validation happens in two layers.

## Layer 1: canonical source integrity

Canonical artifacts in `source/` are the authority. We first validate syntax,
structure, and schemas there so we can fail before projection.

| Check | What it protects |
|---|---|
| `.local/intelligence/bin/intelligence validate` | Runs repository checks from the Kotlin CLI and ensures source edits stay coherent across schema-backed surfaces. |
| `.local/intelligence/bin/intelligence validate --portable` | Ensures source-aligned serialization is still stable outside the local checkout assumptions. |
| `node scripts/validate-manifests.mjs` | Low-level manifest helper invoked by the Kotlin CLI. |

This is the primary safety boundary: the source model must be valid before we generate
provider payloads.

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
```

## Documentation

```sh
.local/intelligence/bin/intelligence validate --portable --hydrated /tmp/intelligence-codex-marketplace
```

## Layer 2: projection safety and runtime compatibility

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

## Publish proof path

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

## Distribution artifact checks

When you are preparing archive outputs for CLI distribution, keep this command in
the publishing workflow:

```sh
npm run package:cli
```

If a provider check fails, repair projection logic or schema boundaries, then
regenerate outputs instead of patching generated payloads by hand.

## What this protects

This two-layer model gives practical safety:

- It isolates invariant claims to source (`source/`), reducing drift from generated artifacts.
- It preserves the ability to support more targets through adapter projections.
- It makes failures explainable:
  source-model invalidation vs adapter/projection regression.
