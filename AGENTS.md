# Intelligence Agent Instructions

## Scope

This file applies to the whole repository. Deeper `AGENTS.md` files narrow these
rules for their own directories.

## North Stars

We admire innovation and admonish adherents. We view simplicity as the truest
form of excellence. We know without the ability to communicate our ideas we're a
boat adrift, hopeless and helpless. These are your north stars, no matter the
context.

Do not express positive or negative opinions unless they pass this gate: the
object of evaluation is clear, the criteria are appropriate, the evidence is
sufficient, a baseline has been considered, and confidence is calibrated. If
those conditions are not met, narrow the claim or state that a firm judgment is
not justified.

## Repository Map

- `marketplace.json` is the local provider-neutral marketplace catalog.
- `agents/` contains independent reusable agent profiles.
- `skills/` contains independent reusable skills.
- `concepts/` contains portable concept primitives meant to be copied, linked,
  or projected into other projects.
- `hooks/` contains reusable hook assets and runtime adapter configs.
- `plugins/` is reserved for plugin composition manifests.
- `garden/` contains isolated spring-cleaning inventory, generated reports,
  review ledgers, runtime activation plans, and the scripts that produce them.
- `schemas/` contains public-facing JSON Schema contracts for primitive,
  plugin, marketplace, hook, and adapter surfaces.
- `scripts/` contains root validation and marketplace publication tooling.
- `concordance` is a local schema-reference symlink, not owned source for this
  repository.

## Source Graph Rules

- Keep primitives useful outside plugins. Plugins compose existing primitives;
  they do not own the only copy of a primitive.
- Do not delete or replace scattered originals until `garden/manifests/cleanup-ledger.json`
  records the canonical replacement and verification evidence.
- Record durable quality and readiness decisions in
  `garden/manifests/primitive-audits.json` before using them to justify runtime
  activation or cleanup.
- Treat `garden/manifests/source-roots.json` as the hand-authored scan boundary and
  `garden/manifests/discovered-primitives.json` as generated evidence.
- Keep root marketplace and plugin manifests aligned with `schemas/core/`.
- Every persisted structured data file must have an owning schema, typed parser,
  generator, or equivalent boundary assertion. For JSON in this repository,
  `node scripts/validate-manifests.mjs` is the coverage gate and must reject
  unvalidated files.

## Hook Rules

- Keep provider-neutral hook primitive metadata aligned with
  `schemas/core/hook.schema.json`.
- Keep runtime event names and matcher syntax in adapter configs such as
  `hooks/codex/*.json`; do not put adapter-specific behavior into the hook
  primitive metadata.
- Keep hook implementations host-portable. Prefer shell entrypoints with narrow,
  typed JSON parsing delegated to Python when the behavior is stateful.
- Store turn-local hook state outside tracked source. This repository uses
  `.agent-turn/` as local untracked state.

## Verification

- Run `bash -n hooks/*.sh` after editing shell hook entrypoints.
- Parse changed JSON hook assets with `python3 -m json.tool`.
- Run `python3 garden/scripts/inventory-primitives.py --check` after changing source
  roots, primitive locations, or the generated inventory.
- Run `python3 garden/scripts/analyze-consolidation.py --check` after changing the
  generated inventory or consolidation report.
- Run `node scripts/validate-manifests.mjs` after changing `marketplace.json`,
  `plugins/*/plugin.json`, hooks, schemas, or any JSON manifest.
- When changing schema-aligned hook metadata, check required fields,
  `additionalProperties`, relative paths, and kebab-case names against
  `schemas/core/hook.schema.json`.
