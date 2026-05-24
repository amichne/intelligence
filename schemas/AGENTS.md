# Schema Instructions

## Scope

This file applies to `schemas/`.

## Rules

- Keep public-facing primitive, plugin, marketplace, and adapter schemas under
  `schemas/`.
- Keep private garden cleanup and inventory schemas under
  `garden/schemas/intelligence/`.
- Keep `schemas/core/` aligned with `concordance/schemas/core/` when the local
  Concordance schema reference is present.
- Add or update the schema before changing the persisted JSON shape it owns.
- Run `node scripts/validate-manifests.mjs` after changing schemas or JSON data.
