# Schema Instructions

## Scope

This file applies to `schemas/`.

## Rules

- Keep public-facing primitive, plugin, marketplace, and adapter schemas under
  `schemas/`.
- Keep provider-neutral core definitions in `schemas/core/`,
  marketplace root contracts in `schemas/marketplace/`, and
  non-marketplace runtime projections under `schemas/adapters/<adapter>/`.
- Add or update the schema before changing the persisted JSON shape it owns.
- Run projector tests after changing schemas or JSON data, then project a
  representative source to each supported harness.
