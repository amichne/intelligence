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
- `garden/manifests/source-roots.json` lists the local places currently scanned.
- `garden/manifests/promotions.json` records copied-in canonical primitives and their
  original sources.
- `garden/manifests/primitive-audits.json` records quality and readiness decisions
  before using them for runtime activation or cleanup.
- `garden/manifests/discovered-primitives.json` records the generated inventory.
- `garden/manifests/consolidation-report.json` records the derived review queue.
- `garden/manifests/source-review-decisions.json` records keep, cover, defer, retain,
  rewrite, and review decisions for every generated name-review group before
  those groups guide trimming.
- `garden/manifests/digest-review-decisions.json` records cover, retain, and review
  decisions for every generated duplicate-content group before those groups
  guide runtime replacement or cleanup.
- `garden/manifests/source-root-decisions.json` records manual root-level decisions
  for scanned roots that need explicit ownership handling outside generated
  duplicate-name or duplicate-content queues.
- `garden/manifests/plugin-coverage.json` records generated evidence that canonical
  primitives are either composed by referential plugins, exposed through the
  marketplace, or intentionally scoped as repository instructions.
- `garden/manifests/primitive-decision-coverage.json` records generated per-entry
  evidence for whether every discovered primitive has a canonical, duplicate
  review, digest review, source-root, or unreviewed-singleton decision path.
- `garden/manifests/review-completeness.json` records generated evidence comparing
  canonical primitives with durable audit decisions.
- `garden/manifests/source-root-retirement.json` records generated root-by-root
  evidence for which scanned roots are canonical, retained, review-required, or
  ready for replacement approval.
- `garden/manifests/source-turnoff-readiness.json` records generated readiness evidence
  for replacing scattered sources with canonical primitives and runtime links.
- `garden/manifests/runtime-activation-plan.json` records generated dry-run operations
  for future symlink replacement and runtime activation.
- `garden/manifests/runtime-activation-preflight.json` records generated filesystem
  checks for the dry-run activation operations.
- `garden/manifests/runtime-activation-approvals.json` records generated approval
  packets joining activation previews, preflight evidence, rollback notes, and
  explicit approval boundaries.
- `garden/manifests/runtime-links.json` records planned runtime symlink and marketplace
  activation targets without executing them.
- `garden/manifests/cleanup-ledger.json` records executed broken-link cleanup and
  proposed future replacements. `PROPOSED` entries are review records, not write
  or delete permission.
- `garden/schemas/intelligence/` owns the repo-local JSON Schema contracts for
  manifests and generated reports.
- `schemas/core/` vendors the public provider-neutral schemas for skills,
  agents, hooks, instructions, plugins, marketplaces, references, and locks.
- `schemas/adapters/` owns adapter-specific marketplace and hook schemas for
  Codex, Claude, GitHub, and future runtime projections.
- `schemas/hooks/` owns public hook support schemas such as required skill-read
  configuration.
- `garden/docs/source-of-truth.md` documents the working source-of-truth rules.
- `garden/docs/inventory-summary.md` summarizes the current inventory pass.
- `garden/docs/consolidation-queue.md` summarizes promotion and cleanup candidates.
- `garden/docs/plugin-coverage.md` summarizes canonical primitive plugin coverage.
- `garden/docs/primitive-decision-coverage.md` summarizes per-entry review coverage
  gaps across the generated inventory.
- `garden/docs/review-completeness.md` summarizes remaining canonical primitive audit
  gaps.
- `garden/docs/source-root-retirement.md` summarizes source-root retirement and
  retention posture across all scanned roots.
- `garden/docs/source-turnoff-readiness.md` summarizes source turnoff gates and
  approval requirements.
- `garden/docs/runtime-activation-plan.md` summarizes future activation operations
  without executing them.
- `garden/docs/runtime-activation-preflight.md` summarizes real filesystem readiness
  for those dry-run operations.
- `garden/docs/runtime-activation-approvals.md` packages approval-ready and
  review-required activation packets without executing them.

Refresh and validate with:

```sh
python3 garden/scripts/check-source-graph.py --refresh
python3 garden/scripts/check-source-graph.py
```

The expanded validation path is:

```sh
python3 garden/scripts/inventory-primitives.py
python3 garden/scripts/inventory-primitives.py --check
python3 garden/scripts/analyze-consolidation.py
python3 garden/scripts/analyze-consolidation.py --check
python3 garden/scripts/analyze-plugin-coverage.py
python3 garden/scripts/analyze-plugin-coverage.py --check
python3 garden/scripts/analyze-primitive-decision-coverage.py
python3 garden/scripts/analyze-primitive-decision-coverage.py --check
python3 garden/scripts/analyze-review-completeness.py
python3 garden/scripts/analyze-review-completeness.py --check
python3 garden/scripts/analyze-source-turnoff-readiness.py
python3 garden/scripts/analyze-source-turnoff-readiness.py --check
python3 garden/scripts/analyze-runtime-activation.py
python3 garden/scripts/analyze-runtime-activation.py --check
python3 garden/scripts/preflight-runtime-activation.py
python3 garden/scripts/preflight-runtime-activation.py --check
python3 garden/scripts/analyze-runtime-activation-approvals.py
python3 garden/scripts/analyze-runtime-activation-approvals.py --check
python3 garden/scripts/analyze-source-root-retirement.py
python3 garden/scripts/analyze-source-root-retirement.py --check
python3 garden/scripts/analyze-toolbox-readiness.py
python3 garden/scripts/analyze-toolbox-readiness.py --check
node scripts/validate-manifests.mjs
bash -n hooks/*.sh
```

`python3 garden/scripts/check-source-graph.py` is the source graph gate. It runs the
generated-report checks, `node scripts/validate-manifests.mjs`, schema policy,
Python syntax checks, hook shell syntax checks, and `git diff --check`.
`node scripts/validate-manifests.mjs` is the structured-data gate:
`schemas/core/` covers marketplace/plugin/hook primitives,
`schemas/adapters/` covers adapter output, and `garden/schemas/intelligence/`
covers garden manifests.

## Publish Marketplace

`main` keeps referential source only. The generated `marketplace` branch is
published from hydrated output with one orphan commit.

Preview the branch output locally:

```sh
python3 scripts/publish-marketplace.py materialize --out /tmp/intelligence-marketplace
node scripts/validate-manifests.mjs --hydrated /tmp/intelligence-marketplace
python3 scripts/publish-marketplace.py publish-branch --branch marketplace --no-push
```

Merges to `main` run `.github/workflows/publish-marketplace.yml`, validate the
source contracts, materialize the marketplace, and force-update `marketplace`.
