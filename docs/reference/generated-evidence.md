# Generated Evidence

Generated evidence explains the current state of the source graph. It is useful
for review, but it should not be edited by hand.

## Primary Reports

The Markdown reports under `garden/docs/` summarize generated manifests.

| Report | Answers |
|---|---|
| `garden/docs/source-of-truth.md` | What sources, rules, and workflows define the repository? |
| `garden/docs/inventory-summary.md` | What primitive-shaped files were discovered? |
| `garden/docs/consolidation-queue.md` | What promotion or cleanup candidates exist? |
| `garden/docs/plugin-coverage.md` | Which canonical primitives are composed or exposed? |
| `garden/docs/primitive-decision-coverage.md` | Which discovered entries have a decision path? |
| `garden/docs/review-completeness.md` | Which canonical primitives still need audit decisions? |
| `garden/docs/source-root-retirement.md` | Which scanned roots are canonical, retained, or review-required? |
| `garden/docs/source-turnoff-readiness.md` | Which source turnoff paths are blocked, ready, or complete? |
| `garden/docs/toolbox-readiness.md` | How close is the toolbox objective to complete? |
| `garden/docs/runtime-activation-approvals.md` | Which runtime activation packets are ready or blocked? |

## Regeneration

Regenerate evidence when source roots, primitive locations, plugin coverage,
review ledgers, cleanup ledgers, or runtime activation plans change.

```sh
python3 garden/scripts/check-source-graph.py --refresh
```

Then prove the generated files are current.

```sh
python3 garden/scripts/check-source-graph.py
```

## Reading Evidence Safely

Generated evidence can justify a decision only when it is current and paired
with the relevant authored manifest.

For example, runtime activation should consult the runtime plan, preflight
manifest, approval packets, and cleanup ledger together. A single generated
table is context, not permission.
