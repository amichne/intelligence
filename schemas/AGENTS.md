# Schema Instructions

## Scope

This file applies to `schemas/`.

## Rules

- Keep public-facing primitive, plugin, marketplace, and adapter schemas under
  `schemas/`.
- Keep provider-neutral contracts in `schemas/core/` and runtime-specific
  projections under `schemas/adapters/<adapter>/`.
- Keep private garden cleanup and inventory schemas under
  `garden/schemas/intelligence/`.
- Add or update the schema before changing the persisted JSON shape it owns.
- Run `node scripts/validate-manifests.mjs` after changing schemas or JSON data.
