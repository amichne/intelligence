# intelligence

Au naturel (some additives)


toolkit for my outsourcing of "intelligence"

## Current Shape

This repo is becoming the source graph for personal AI tooling primitives:
skills, agents, hooks, instructions, and referential plugins.

- `marketplace.json` exposes the local catalog.
- `plugins/intelligence-core/plugin.json` composes the first portable instruction
  and hook primitives by reference.
- `manifests/source-roots.json` lists the local places currently scanned.
- `manifests/discovered-primitives.json` records the generated inventory.
- `manifests/consolidation-report.json` records the derived review queue.
- `docs/source-of-truth.md` documents the working source-of-truth rules.
- `docs/inventory-summary.md` summarizes the first inventory pass.
- `docs/consolidation-queue.md` summarizes promotion and cleanup candidates.

Refresh and validate with:

```sh
python3 scripts/inventory-primitives.py
python3 scripts/inventory-primitives.py --check
python3 scripts/analyze-consolidation.py
python3 scripts/analyze-consolidation.py --check
node scripts/validate-manifests.mjs
bash -n hooks/*.sh
```
