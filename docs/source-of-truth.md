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
- `manifests/primitive-audits.json` records durable quality, schema,
  provenance, runtime, and cleanup-readiness decisions before those decisions
  are used to turn off older sources.
- `manifests/discovered-primitives.json` is generated inventory evidence.
- `manifests/consolidation-report.json` is the generated review queue derived
  from inventory evidence.
- `manifests/source-review-decisions.json` records explicit trim decisions for
  generated duplicate-name groups. The validator keeps it synchronized with the
  generated queue.
- `manifests/digest-review-decisions.json` records explicit trim decisions for
  generated duplicate-content groups. The validator keeps it synchronized with
  the generated queue before those groups can guide runtime replacement.
- `manifests/source-root-decisions.json` records explicit root-level decisions
  for scanned roots that do not need or receive generated duplicate-name or
  duplicate-content review entries.
- `manifests/plugin-coverage.json` is generated coverage evidence for the
  canonical source graph. It records whether each canonical primitive is
  composed by a referential plugin, exposed as a marketplace plugin, scoped as a
  repository instruction, or left standalone.
- `manifests/primitive-decision-coverage.json` is generated per-entry review
  coverage evidence. It records whether each discovered primitive is canonical,
  part of a duplicate-name decision, part of a duplicate-content decision,
  covered by a source-root decision, or still an unreviewed singleton.
- `manifests/review-completeness.json` is generated review evidence for the
  canonical source graph. It records whether each canonical primitive has a
  durable audit decision.
- `manifests/source-root-retirement.json` is generated retirement evidence for
  scanned roots. It records whether each root is the canonical owner, ready for
  partial replacement approval, retained externally, missing a replacement
  plan, or still blocked on runtime review.
- `manifests/source-turnoff-readiness.json` is generated readiness evidence for
  replacing scattered sources. It joins plugin coverage, review decisions,
  cleanup-ledger status, and runtime-link approval requirements.
- `manifests/runtime-activation-plan.json` is generated dry-run activation
  evidence. It turns proposed cleanup and runtime-link plans into explicit
  future operations, but it does not authorize execution.
- `manifests/runtime-activation-preflight.json` is generated filesystem
  evidence for the dry-run activation plan. It records existing sources,
  targets, backups, symlink targets, and blockers before any approval.
- `manifests/runtime-activation-approvals.json` is generated approval evidence
  for dry-run activation. It packages each operation with preflight status,
  rollback notes, command previews, and the explicit approval boundary.
- `manifests/runtime-links.json` records planned runtime symlink and marketplace
  activation targets. It is a plan, not permission to write runtime paths.
- `schemas/intelligence/` contains repository-owned JSON Schema contracts for
  manifests, ledgers, and generated reports that are not Concordance-owned.
- `docs/inventory-summary.md` summarizes the current generated inventory.
- `docs/consolidation-queue.md` summarizes the promotion, duplicate, and broken
  symlink queues.
- `docs/plugin-coverage.md` summarizes canonical primitive coverage through
  referential plugins and marketplace entries.
- `docs/primitive-decision-coverage.md` summarizes per-entry decision coverage
  gaps for the full generated primitive inventory.
- `docs/review-completeness.md` summarizes remaining canonical primitive audit
  gaps.
- `docs/source-root-retirement.md` summarizes root-by-root retirement,
  retention, and review posture for the full scan boundary.
- `docs/source-turnoff-readiness.md` summarizes whether source replacement is
  blocked, review-ready, approved for execution, or complete.
- `docs/toolbox-readiness.md` maps the original toolbox objective to current
  generated evidence and names the remaining review or approval boundary.
- `docs/runtime-activation-plan.md` summarizes dry-run activation operations,
  including backup, symlink, and manual marketplace import previews.
- `docs/runtime-activation-preflight.md` summarizes current filesystem
  readiness and review blockers for those activation operations.
- `docs/runtime-activation-approvals.md` summarizes which activation packets
  are ready for approval, ready for manual import, or still need review.
- `manifests/cleanup-ledger.json` is the approval gate before deletion,
  replacement of scattered originals, or removal of broken runtime symlinks.
  `PROPOSED` entries are review records only; they do not authorize deletion or
  symlink writes.

The local `concordance` symlink supplies the provider-neutral schemas used to
validate marketplace, plugin, and hook primitive files. It is reference material,
not owned source for this repository.

## Structured Data Rule

All structured data in this repository must follow a schema-driven workflow.
There are no exceptions for small manifests, local ledgers, hook adapters,
plugin catalogs, generated reports, fixtures, or examples.

- Identify the owning schema, typed parser, generator, or equivalent boundary
  assertion before editing persisted JSON, YAML, TOML, or other structured data.
- Add or update the schema path first when the shape changes.
- Validate the data with the owning command before calling the change complete.
- Use the local `concordance/schemas/core/` schemas for marketplace, plugin,
  hook, and primitive-reference manifests.
- Use `concordance/standards/codex-hooks.schema.json` for Codex adapter files
  under `hooks/codex/`.
- Use `schemas/intelligence/*.schema.json` for repository-owned manifests and
  generated inventory/report files.
- Use `skills/manage-json-schemas` and `concepts/schema-driven-design/core.md`
  when creating new JSON schema contracts or changing existing ones.
- `node scripts/validate-manifests.mjs` is the JSON coverage gate. A new JSON
  file should fail validation until it has an owning schema path or is a schema
  document that compiles.

## Workflow

1. Run `python3 scripts/inventory-primitives.py` to refresh the local primitive
   inventory.
2. Run `python3 scripts/analyze-consolidation.py` to rebuild the consolidation
   queue.
3. Review authored/local candidates before importing runtime or cache material.
4. Run `python3 scripts/analyze-plugin-coverage.py` to rebuild canonical
   primitive coverage evidence.
5. Run `python3 scripts/analyze-primitive-decision-coverage.py` to rebuild
   per-entry review coverage evidence for the discovered inventory.
6. Run `python3 scripts/analyze-review-completeness.py` to rebuild canonical
   primitive audit-completeness evidence.
7. Run `python3 scripts/analyze-source-turnoff-readiness.py` to rebuild the
   source turnoff readiness view.
8. Run `python3 scripts/analyze-runtime-activation.py` to rebuild the dry-run
   activation plan.
9. Run `python3 scripts/preflight-runtime-activation.py` to inspect the dry-run
   activation plan against the current filesystem.
10. Run `python3 scripts/analyze-runtime-activation-approvals.py` to rebuild
   approval packets before asking for or acting on runtime activation approval.
11. Run `python3 scripts/analyze-source-root-retirement.py` to rebuild
   root-by-root retention and retirement evidence after the approval queue is
   current.
12. Promote one canonical primitive at a time into this repository.
13. Add the promoted primitive to `marketplace.json` and any composed
   `plugins/*/plugin.json` files.
14. Record the source of promoted primitives in `manifests/promotions.json`.
15. Record quality and readiness decisions in `manifests/primitive-audits.json`
   when they will guide promotion, activation, or cleanup.
16. Record source-review decisions in
   `manifests/source-review-decisions.json` before treating generated duplicate
   groups as trim candidates.
17. Record digest-review decisions in
   `manifests/digest-review-decisions.json` before treating byte-identical
   groups as runtime replacement or cleanup candidates.
18. Record source-root decisions in `manifests/source-root-decisions.json`
   when a scanned root needs manual retain/promote/review handling outside
   generated duplicate queues.
19. Record cleanup intent in `manifests/cleanup-ledger.json` only after the
   canonical replacement is verified, or after a broken runtime symlink is
   proven to point at a missing target. Keep future replacements as
   `PROPOSED` until the user explicitly approves execution.

Plugins remain composition surfaces. The primitive must be useful without the
plugin, and the plugin must only assemble primitives that already exist
independently.

## Current Plugin Families

- `intelligence-core`: portable instruction and hook primitives.
- `kotlin-review`: Kotlin standards, Gradle validation, review agents, and
  layout checks.
- `primitive-authoring`: skill, agent, hook, shell script, schema,
  source-graph consolidation, and referential plugin authoring workflows.
- `primitive-governance`: primitive quality audit, routing evaluation,
  source-graph consolidation, runtime-link readiness, and schema validation
  workflows.
- `repository-orientation`: repository boundary mapping, scoped instruction
  authoring, and signature indexing workflows.
- `runtime-activation`: runtime linking and activation planning for exposing
  canonical primitives without mutating targets by default.
- `schema-governance`: schema contract skill plus schema/type review surface.
- `tdd-workflow`: language-agnostic TDD workflow with design concepts.
- `planning-and-docs`: goal definition and reference-document workflow.
- `documentation-workflow`: documentation-site authoring for MkDocs,
  Zensical, and related docs-as-code sites.
- `version-control`: local Git process plus GitHub CI, Actions workflow, and
  release-operation skills.

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
python3 scripts/check-source-graph.py --refresh
python3 scripts/check-source-graph.py
```

The expanded check sequence is:

```sh
python3 scripts/inventory-primitives.py
python3 scripts/inventory-primitives.py --check
python3 scripts/analyze-consolidation.py
python3 scripts/analyze-consolidation.py --check
python3 scripts/analyze-plugin-coverage.py
python3 scripts/analyze-plugin-coverage.py --check
python3 scripts/analyze-primitive-decision-coverage.py
python3 scripts/analyze-primitive-decision-coverage.py --check
python3 scripts/analyze-review-completeness.py
python3 scripts/analyze-review-completeness.py --check
python3 scripts/analyze-source-turnoff-readiness.py
python3 scripts/analyze-source-turnoff-readiness.py --check
python3 scripts/analyze-runtime-activation.py
python3 scripts/analyze-runtime-activation.py --check
python3 scripts/preflight-runtime-activation.py
python3 scripts/preflight-runtime-activation.py --check
python3 scripts/analyze-runtime-activation-approvals.py
python3 scripts/analyze-runtime-activation-approvals.py --check
python3 scripts/analyze-source-root-retirement.py
python3 scripts/analyze-source-root-retirement.py --check
node scripts/validate-manifests.mjs
bash -n hooks/*.sh
python3 -m json.tool marketplace.json >/dev/null
```
