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
- `plugins/kotlin-review/plugin.json` composes Kotlin standards, Gradle
  validation, review agents, and layout hook by reference.
- `plugins/primitive-authoring/plugin.json` composes skill, agent, hook, shell
  script, schema, source-graph consolidation, and plugin authoring workflows by
  reference.
- `plugins/primitive-governance/plugin.json` composes quality audit, routing
  evaluation, source-graph governance, runtime readiness, and schema validation
  workflows by reference.
- `plugins/repository-orientation/plugin.json` composes repository boundary,
  scoped instruction authoring, and signature indexing workflows by reference.
- `plugins/runtime-activation/plugin.json` composes runtime linking and
  source-graph activation workflows by reference.
- `plugins/planning-and-docs/plugin.json` composes goal definition and
  reference-document workflow skills by reference.
- `plugins/documentation-workflow/plugin.json` composes reference-document and
  MkDocs/Zensical site-authoring skills by reference.
- `plugins/schema-governance/plugin.json` composes the schema contract skill,
  schema review agent, and schema/type concepts by reference.
- `plugins/tdd-workflow/plugin.json` composes the TDD workflow skill with the
  type/schema concepts by reference.
- `plugins/version-control/plugin.json` composes Git process and GitHub
  CI/workflow skills by reference.
- `manifests/source-roots.json` lists the local places currently scanned.
- `manifests/promotions.json` records copied-in canonical primitives and their
  original sources.
- `manifests/primitive-audits.json` records quality and readiness decisions
  before using them for runtime activation or cleanup.
- `manifests/discovered-primitives.json` records the generated inventory.
- `manifests/consolidation-report.json` records the derived review queue.
- `manifests/source-review-decisions.json` records keep, cover, defer, retain,
  rewrite, and review decisions for every generated name-review group before
  those groups guide trimming.
- `manifests/digest-review-decisions.json` records cover, retain, and review
  decisions for every generated duplicate-content group before those groups
  guide runtime replacement or cleanup.
- `manifests/source-root-decisions.json` records manual root-level decisions
  for scanned roots that need explicit ownership handling outside generated
  duplicate-name or duplicate-content queues.
- `manifests/plugin-coverage.json` records generated evidence that canonical
  primitives are either composed by referential plugins, exposed through the
  marketplace, or intentionally scoped as repository instructions.
- `manifests/primitive-decision-coverage.json` records generated per-entry
  evidence for whether every discovered primitive has a canonical, duplicate
  review, digest review, source-root, or unreviewed-singleton decision path.
- `manifests/review-completeness.json` records generated evidence comparing
  canonical primitives with durable audit decisions.
- `manifests/source-root-retirement.json` records generated root-by-root
  evidence for which scanned roots are canonical, retained, review-required, or
  ready for replacement approval.
- `manifests/source-turnoff-readiness.json` records generated readiness evidence
  for replacing scattered sources with canonical primitives and runtime links.
- `manifests/runtime-activation-plan.json` records generated dry-run operations
  for future symlink replacement and runtime activation.
- `manifests/runtime-activation-preflight.json` records generated filesystem
  checks for the dry-run activation operations.
- `manifests/runtime-activation-approvals.json` records generated approval
  packets joining activation previews, preflight evidence, rollback notes, and
  explicit approval boundaries.
- `manifests/runtime-links.json` records planned runtime symlink and marketplace
  activation targets without executing them.
- `manifests/cleanup-ledger.json` records executed broken-link cleanup and
  proposed future replacements. `PROPOSED` entries are review records, not write
  or delete permission.
- `schemas/intelligence/` owns the repo-local JSON Schema contracts for
  manifests and generated reports.
- `docs/source-of-truth.md` documents the working source-of-truth rules.
- `docs/inventory-summary.md` summarizes the current inventory pass.
- `docs/consolidation-queue.md` summarizes promotion and cleanup candidates.
- `docs/plugin-coverage.md` summarizes canonical primitive plugin coverage.
- `docs/primitive-decision-coverage.md` summarizes per-entry review coverage
  gaps across the generated inventory.
- `docs/review-completeness.md` summarizes remaining canonical primitive audit
  gaps.
- `docs/source-root-retirement.md` summarizes source-root retirement and
  retention posture across all scanned roots.
- `docs/source-turnoff-readiness.md` summarizes source turnoff gates and
  approval requirements.
- `docs/runtime-activation-plan.md` summarizes future activation operations
  without executing them.
- `docs/runtime-activation-preflight.md` summarizes real filesystem readiness
  for those dry-run operations.
- `docs/runtime-activation-approvals.md` packages approval-ready and
  review-required activation packets without executing them.

Refresh and validate with:

```sh
python3 scripts/check-source-graph.py --refresh
python3 scripts/check-source-graph.py
```

The expanded validation path is:

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
python3 scripts/analyze-toolbox-readiness.py
python3 scripts/analyze-toolbox-readiness.py --check
node scripts/validate-manifests.mjs
bash -n hooks/*.sh
```

`python3 scripts/check-source-graph.py` is the source graph gate. It runs the
generated-report checks, `node scripts/validate-manifests.mjs`, schema policy,
Python syntax checks, hook shell syntax checks, and `git diff --check`.
`node scripts/validate-manifests.mjs` is the structured-data gate:
Concordance schemas cover marketplace/plugin/hook primitives, Concordance
standards cover Codex hook adapters, and `schemas/intelligence/` covers
repo-owned manifests.
