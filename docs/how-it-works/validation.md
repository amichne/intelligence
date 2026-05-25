# Validation

Validation is the contract that keeps the public source graph usable. The
repository uses schema-backed checks, focused syntax checks, and documentation
builds.

## Main Gate

Use the CLI gate for ordinary changes.

```sh
bin/intelligence validate
```

The underlying manifest gate is:

```sh
node scripts/validate-manifests.mjs
```

## Structured Data Gate

Every persisted JSON file should have a schema, typed parser, generator, or
equivalent boundary assertion.

```sh
node scripts/validate-manifests.mjs
```

Use the portable mode when validating generated marketplace output outside this
machine's local filesystem layout.

```sh
node scripts/validate-manifests.mjs --portable
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-marketplace
```

## Focused Checks

Run the narrow check that matches the changed surface.

| Changed Surface | Check |
|---|---|
| Hook shell entrypoints | `bash -n hooks/*.sh` |
| JSON hook assets | `python3 -m json.tool hooks/name.hook.json` |
| Marketplace or plugin manifests | `node scripts/validate-manifests.mjs` |
| Documentation site | `zensical build --clean` |

## Package And Publish Checks

Build local distribution archives with:

```sh
npm run package:cli -- --version local
```

Preview marketplace publication with:

```sh
python3 scripts/publish-marketplace.py materialize --out /tmp/intelligence-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-marketplace
python3 scripts/publish-marketplace.py sync-github-plugin --check
python3 scripts/publish-marketplace.py publish-branch --branch marketplace --no-push
```
