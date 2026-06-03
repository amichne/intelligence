# Validation

The validation model is derived from the same first principle used in the source
model:
**every change is valid only if it preserves the normalized contract before any
runtime output is produced.**

Validation happens in two layers.

## Layer 1: canonical source integrity

Canonical artifacts in `source/` are the authority. We first validate syntax,
structure, and schemas there so we can fail before projection.

| Check | What it protects |
|---|---|
| `bin/intelligence validate` | Runs repository checks from one entrypoint and ensures source edits stay coherent across schema-backed surfaces. |
| `node scripts/validate-manifests.mjs` | Enforces JSON manifest contracts from `source/`, including `source/schemas`, marketplaces, plugins, profiles, and schema ownership rules. |
| `node scripts/validate-manifests.mjs --portable` | Ensures source-aligned serialization is still stable outside the local checkout assumptions. |

This is the primary safety boundary: the source model must be valid before we generate
provider payloads.

### JSON and schema boundary

Every persisted JSON file should have schema, typed parser, generator, or an
equivalent assertion boundary.

```sh
node scripts/validate-manifests.mjs
```

Use the portable mode when validating generated marketplace output outside this
machine's local filesystem layout.

```sh
node scripts/validate-manifests.mjs --portable
```

Hydrated validation checks runtime-adapted output surfaces and catches projection
mismatches that normal schema checks may miss.

```sh
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-codex-marketplace
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
| Marketplace or plugin manifests | `node scripts/validate-manifests.mjs` |
| Documentation site | `zensical build --clean` |
| Provider-specific projection | `node scripts/validate-manifests.mjs --portable --hydrated <provider-output-dir>` |

## Publish proof path

For publish flows, keep the same source-to-projection sequence: validate source,
materialize outputs, then verify hydration.

```sh
python3 scripts/publish-marketplace.py materialize --provider codex --out /tmp/intelligence-codex-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-codex-marketplace
python3 scripts/publish-marketplace.py publish-branch --provider codex --branch codex --no-push
python3 scripts/publish-marketplace.py materialize --provider github --out /tmp/intelligence-github-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-github-marketplace
python3 scripts/publish-marketplace.py publish-branch --provider github --branch github --no-push
python3 scripts/publish-marketplace.py sync-main-marketplaces --check
```

## Distribution artifact checks

When you are preparing archive outputs for CLI distribution, keep this command in
the publishing workflow:

```sh
npm run package:cli -- --version local
```

If a provider check fails, repair projection logic or schema boundaries, then
regenerate outputs instead of patching generated payloads by hand.

## What this protects

This two-layer model gives practical safety:

- It isolates invariant claims to source (`source/`), reducing drift from generated artifacts.
- It preserves the ability to support more targets through adapter projections.
- It makes failures explainable:
  source-model invalidation vs adapter/projection regression.
