# Review Queue

Use this reference when deciding what to promote from
`garden/docs/consolidation-queue.md` or `garden/manifests/consolidation-report.json`.

## Queue Meaning

- `canonical`: already lives in this repository and should usually be kept.
- `authored-local`: source-controlled or locally-authored material outside this
  repo. Review before promotion.
- `runtime`: installed local runtime copy. Use for provenance or drift checks,
  not as the first promotion target when an authored source exists.
- `backup`: historical copy. Review only after authored and runtime copies are
  understood.
- `installed-marketplace`: external marketplace/cache material. Use as
  reference material only; do not raw-copy it into canonical primitives.

## Selection Rules

- Prefer coherent capability families over isolated files.
- Promote from authored local sources before runtime caches.
- Treat duplicate digests as cleanup candidates only after a canonical
  replacement exists.
- Treat duplicate names with different digests as semantic review work, not as
  automatic conflict resolution.
- Ignore installed marketplace duplicates unless they reveal first-party name or
  digest collision risk.

## Evidence Commands

```sh
python3 garden/scripts/inventory-primitives.py
python3 garden/scripts/analyze-consolidation.py
python3 garden/scripts/inventory-primitives.py --check
python3 garden/scripts/analyze-consolidation.py --check
```

After running these, inspect `garden/docs/consolidation-queue.md` for human-readable
triage and `garden/manifests/consolidation-report.json` for exact paths and digests.
