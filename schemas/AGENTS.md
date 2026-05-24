# Schema Instructions

## Scope

This file applies to `schemas/`.

## Rules

- Keep repository-owned structured data schemas under `schemas/intelligence/`.
- Use `concordance/schemas/core/` for provider-neutral primitive, plugin,
  marketplace, and hook contracts instead of copying those schemas here.
- Add or update the schema before changing the persisted JSON shape it owns.
- Run `node scripts/validate-manifests.mjs` after changing schemas or JSON data.
