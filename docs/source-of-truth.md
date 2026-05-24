# Intelligence Source Of Truth

This repository is the canonical workspace for AI tooling primitives that are
worth keeping: skills, agents, hooks, instructions, and plugins that compose
those primitives.

The current rule is conservative: inventory first, promote after review, and do
not delete scattered sources until the cleanup ledger records the replacement
and rollback path.

## Source Graph

- `marketplace.json` exposes the local marketplace catalog.
- `agents/` contains independent reusable agent profiles.
- `skills/` contains independent reusable skills.
- `plugins/*/plugin.json` defines composed plugin sets by reference.
- `concepts/` currently holds portable instruction primitives.
- `hooks/` holds provider-neutral hook metadata, implementations, and provider
  adapter configs.
- `manifests/source-roots.json` lists local roots that may contain source
  primitives.
- `manifests/promotions.json` records primitives copied into this repository and
  the source paths they came from.
- `manifests/discovered-primitives.json` is generated inventory evidence.
- `manifests/consolidation-report.json` is the generated review queue derived
  from inventory evidence.
- `docs/inventory-summary.md` summarizes the current generated inventory.
- `docs/consolidation-queue.md` summarizes the promotion, duplicate, and broken
  symlink queues.
- `manifests/cleanup-ledger.json` is the approval gate before deletion or
  replacement of scattered originals.

The local `concordance` symlink supplies the provider-neutral schemas used to
validate marketplace, plugin, and hook primitive files. It is reference material,
not owned source for this repository.

## Workflow

1. Run `python3 scripts/inventory-primitives.py` to refresh the local primitive
   inventory.
2. Run `python3 scripts/analyze-consolidation.py` to rebuild the consolidation
   queue.
3. Review authored/local candidates before importing runtime or cache material.
4. Promote one canonical primitive at a time into this repository.
5. Add the promoted primitive to `marketplace.json` and any composed
   `plugins/*/plugin.json` files.
6. Record the source of promoted primitives in `manifests/promotions.json`.
7. Record cleanup intent in `manifests/cleanup-ledger.json` only after the
   canonical replacement is verified.

Plugins remain composition surfaces. The primitive must be useful without the
plugin, and the plugin must only assemble primitives that already exist
independently.

## First-Party Source Handling

OpenAI, Anthropic, and other first-party distributions are useful reference
material, but they should not be copied into this repository verbatim as local
canonical primitives.

- Promote first-party material only after deciding it belongs in this personal
  source graph.
- Rename promoted derivatives with local, non-colliding primitive names.
- Rewrite instructions into this repository's voice and provider-neutral
  workflow shape.
- Keep original first-party paths only as provenance in
  `manifests/promotions.json`.
- Do not ship local primitives whose names would collide with installed
  first-party tools unless the primitive is intentionally replacing that local
  name and the manifest says so.
- `node scripts/validate-manifests.mjs` enforces first-party name and raw digest
  collision checks against the generated inventory.

## Verification

Use these checks after changing manifests, hooks, or source graph files:

```sh
python3 scripts/inventory-primitives.py
python3 scripts/inventory-primitives.py --check
python3 scripts/analyze-consolidation.py
python3 scripts/analyze-consolidation.py --check
node scripts/validate-manifests.mjs
bash -n hooks/*.sh
python3 -m json.tool marketplace.json >/dev/null
```
