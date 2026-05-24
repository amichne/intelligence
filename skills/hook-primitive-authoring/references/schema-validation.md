# Schema Validation

Use this reference for repo-local hook validation.

## Schema Dependency

This repository owns the schema reference material used by hook validation. The
manifest validator loads schemas from:

```text
schemas/core/
schemas/adapters/
```

Hook metadata is validated with `hook.schema.json`, plugin composition with
`plugin.schema.json`, and marketplace entries with `marketplace.schema.json`
through:

```sh
node scripts/validate-manifests.mjs
```

Adapter projections under `hooks/<adapter>/` validate against the matching
schema in `schemas/adapters/<adapter>/`. Repository-owned manifest and
generated-report JSON validates against `garden/schemas/intelligence/*.schema.json`.
The same command also checks local primitive references, promotion source paths,
and rejects any JSON file that is not covered by a schema validation path.

This is mandatory for every structured hook or plugin data change. If a new
structured hook artifact does not fit an existing schema, add or update the
owning schema before treating the artifact as accepted.

## Validation Checklist

- `hooks/<name>.hook.json` parses as JSON.
- The hook metadata validates against `schemas/core/hook.schema.json`.
- Adapter projections validate against their adapter schema, such as
  `schemas/adapters/codex/hooks.schema.json` for `hooks/codex/*.json`.
- Every local `path` exists.
- Every `dependsOn` reference points at a canonical primitive.
- Any plugin that composes the hook references it from `hooks/*`, not from a
  plugin-local payload copy.
- `garden/manifests/promotions.json` records source provenance for promoted hooks.

## Extra Checks

- Parse runtime adapter JSON with `python3 -m json.tool`.
- Run `bash -n` for shell hooks.
- Run `python3 -m py_compile` for Python hooks and remove any `__pycache__`
  artifacts before finishing.
- Run `node --check` for JavaScript hook implementations.
- Run a representative command for hooks that support local execution.
