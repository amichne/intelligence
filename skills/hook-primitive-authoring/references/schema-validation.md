# Schema Validation

Use this reference for Concordance-backed hook validation.

## Concordance Dependency

This repository uses the local `concordance` symlink as schema reference
material. The manifest validator loads schemas from:

```text
schemas/core/
concordance/standards/codex-hooks.schema.json
```

Hook metadata is validated with `hook.schema.json`, plugin composition with
`plugin.schema.json`, and marketplace entries with `marketplace.schema.json`
through:

```sh
node scripts/validate-manifests.mjs
```

Codex provider adapters under `hooks/codex/` validate against the Codex hooks
standard from Concordance. Repository-owned manifest and generated-report JSON
validates against `garden/schemas/intelligence/*.schema.json`. The same command also
checks local primitive references, promotion source paths, and rejects any JSON
file that is not covered by a schema validation path.

This is mandatory for every structured hook or plugin data change. If a new
structured hook artifact does not fit an existing schema, add or update the
owning schema before treating the artifact as accepted.

## Validation Checklist

- `hooks/<name>.hook.json` parses as JSON.
- The hook metadata validates against Concordance `hook.schema.json`.
- Provider adapters validate against their provider schema, currently
  `concordance/standards/codex-hooks.schema.json` for `hooks/codex/*.json`.
- Every local `path` exists.
- Every `dependsOn` reference points at a canonical primitive.
- Any plugin that composes the hook references it from `hooks/*`, not from a
  plugin-local payload copy.
- `garden/manifests/promotions.json` records source provenance for promoted hooks.

## Extra Checks

- Parse provider adapter JSON with `python3 -m json.tool`.
- Run `bash -n` for shell hooks.
- Run `python3 -m py_compile` for Python hooks and remove any `__pycache__`
  artifacts before finishing.
- Run `node --check` for JavaScript hook implementations.
- Run a representative command for hooks that support local execution.
