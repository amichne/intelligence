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

This is the primary safety boundary: the source model must be valid before we
stage APM packages or write release artifacts.

### JSON and schema boundary

Every persisted JSON file should have schema, typed parser, generator, or an
equivalent assertion boundary.

```sh
node scripts/validate-manifests.mjs
```

Use the portable mode in CI and release workflows.

```sh
node scripts/validate-manifests.mjs --portable
```

## Layer 2: APM staging and runtime compatibility

A source-valid model can still fail when staged into APM packages. This layer
verifies APM manifest freshness, package staging, marketplace output mapping, and
APM audit checks.

### Focused checks

Match the surface you changed with the tightest check.

| Changed Surface | Check |
|---|---|
| Hook shell entrypoints | `bash -n source/hooks/*.sh` |
| JSON hook assets | `python3 -m json.tool source/hooks/name.hook.json` |
| Marketplace or plugin manifests | `node scripts/validate-manifests.mjs` |
| APM manifest freshness | `python3 scripts/prepare-apm-marketplace.py manifest --check` |
| APM marketplace preview | `npm run apm:pack:preview` |
| Documentation site | `zensical build --clean` |

## Publish proof path

For publish flows, keep the same source-to-APM sequence: validate source, stage
the APM workspace, then verify pack and audit output.

```sh
node scripts/validate-manifests.mjs --portable
python3 scripts/prepare-apm-marketplace.py manifest --check
python3 scripts/prepare-apm-marketplace.py stage --out build/apm-marketplace --check-root-manifest
cd build/apm-marketplace
apm pack --marketplace=all --dry-run --check-versions --json
apm audit --ci --no-policy
```

## Distribution artifact checks

When you are preparing archive outputs for CLI distribution, keep this command in
the publishing workflow:

```sh
npm run package:cli -- --version local
```

If an APM check fails, repair staging logic or schema boundaries, then regenerate
the ignored workspace instead of patching generated payloads by hand.

## What this protects

This two-layer model gives practical safety:

- It isolates invariant claims to source (`source/`), reducing drift from generated artifacts.
- It preserves the ability to support more targets through APM package outputs.
- It makes failures explainable:
  source-model invalidation vs APM staging or packaging regression.
