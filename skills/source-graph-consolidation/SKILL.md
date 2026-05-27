---
name: "source-graph-consolidation"
description: "Inventory, review, promote, synthesize, validate, and prepare cleanup for scattered AI tooling primitives while preserving originals. Use when consolidating skills, agents, hooks, concepts, instructions, or plugin manifests into a single source graph, updating promotion manifests, interpreting duplicate queues, or preparing later symlink/deletion handoff ledgers."
---

# Source Graph Consolidation

Use this skill to move scattered AI tooling primitives toward this repository as
the source of truth. Consolidation is evidence-first: inventory before moving,
promote before cleanup, validate before trusting, and keep every original in
place until the cleanup ledger proves the replacement and rollback path.

## Operating Contract

- Do not delete, rewrite, or replace scattered originals during promotion.
- Treat generated inventory and consolidation reports as evidence, not as the
  final decision.
- Promote primitives into canonical roots: `skills/`, `agents/`, `hooks/`, and
  `concepts/`.
- Keep plugins referential. They may compose primitives, but they must not own
  the only copy of a primitive.
- Record every promotion in `garden/manifests/promotions.json` with source paths and
  first-party handling when applicable.
- Treat every persisted JSON edit as schema-driven. `node scripts/validate-manifests.mjs`
  must cover the changed file.
- Do not raw-copy OpenAI, Anthropic, or other first-party material. Rename,
  rewrite, and preserve source paths as provenance.

## Workflow

1. Refresh the evidence.
   Run inventory and consolidation generation before deciding what to promote.
   Compare the generated queue to the existing canonical roots.

2. Pick a coherent family.
   Promote related primitives together only when they form a useful capability
   family. Avoid mixing unrelated cleanup just because it appears nearby in the
   queue.

3. Inspect sources.
   Read the candidate source paths and any existing canonical equivalent.
   Decide whether to keep, rewrite, synthesize, split, or ignore the candidate.

4. Promote into canonical roots.
   Place reusable skills, agents, hooks, and concepts where they remain useful
   without a plugin. Extract provider-specific or language-specific details into
   references or scoped subdirectories.

5. Compose by reference.
   Add or update `adaptable.marketplace.json` and `plugins/*/plugin.json` after the
   independent primitive exists. Use direct primitive paths, not payload copies.

6. Record provenance.
   Update `garden/manifests/promotions.json` for promoted material. If the source is a
   first-party distribution, include `firstPartyHandling` and use a local
   non-colliding canonical name.

7. Validate and regenerate.
   Run the validation set, inspect counts, and update summary docs when the
   inventory changes.

8. Defer cleanup.
   Only prepare `garden/manifests/cleanup-ledger.json` entries after the canonical
   replacement is proven and a rollback path exists. Do not perform the cleanup
   unless the user explicitly asks for it.

## Reference Routing

- Load [review-queue.md](references/review-queue.md) when interpreting
  `garden/docs/consolidation-queue.md` or generated report priorities.
- Load [promotion-records.md](references/promotion-records.md) when editing
  `garden/manifests/promotions.json` or handling first-party source material.
- Load [cleanup-handoff.md](references/cleanup-handoff.md) before proposing
  symlink replacement, source deletion, or cleanup ledger entries.

## Completion Criteria

- The promoted primitive is independent of plugins.
- Plugins and marketplace entries reference canonical primitives directly.
- Provenance is recorded before any cleanup is considered.
- Generated inventory and consolidation reports are current.
- Manifest validation proves schema coverage, local references, and first-party
  collision checks.
- No scattered original source was deleted or replaced.
