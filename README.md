# intelligence

Au naturel (some additives)


toolkit for my outsourcing of "intelligence"

## Current Shape

This repo is becoming the source graph for personal AI tooling primitives:
skills, agents, hooks, instructions, and referential plugins.

- `marketplace.json` exposes the local catalog.
- `agents/` contains independent agent profiles.
- `skills/` contains independent skill primitives.
- `plugins/intelligence-core/plugin.json` composes the first portable instruction
  and hook primitives by reference.
- `plugins/kotlin-review/plugin.json` composes the promoted Kotlin skill,
  review agents, and layout hook by reference.
- `plugins/planning-and-docs/plugin.json` composes goal definition and
  reference-document workflow skills by reference.
- `plugins/schema-governance/plugin.json` composes the schema contract skill,
  schema review agent, and schema/type concepts by reference.
- `plugins/tdd-workflow/plugin.json` composes the TDD workflow skill with the
  type/schema concepts by reference.
- `manifests/source-roots.json` lists the local places currently scanned.
- `manifests/promotions.json` records copied-in canonical primitives and their
  original sources.
- `manifests/discovered-primitives.json` records the generated inventory.
- `manifests/consolidation-report.json` records the derived review queue.
- `docs/source-of-truth.md` documents the working source-of-truth rules.
- `docs/inventory-summary.md` summarizes the current inventory pass.
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
