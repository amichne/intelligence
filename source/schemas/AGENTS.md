# Schema Instructions

## Scope

This file applies to `source/schemas/`.

## Rules

- Keep public-facing primitive, plugin, marketplace, and adapter schemas under
  `source/schemas/`.
- Keep provider-neutral core definitions in `source/schemas/core/`,
  marketplace root contracts in `source/schemas/marketplace/`, and
  non-marketplace runtime projections under `source/schemas/adapters/<adapter>/`.
- Add or update the schema before changing the persisted JSON shape it owns.
- Run `.local/intelligence/bin/intelligence validate` after changing schemas or
  JSON data.
