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

- `source/adaptable.marketplace.json` is the provider-neutral curated marketplace catalog.
- `source/agents/` contains independent reusable agent profiles.
- `source/skills/` contains independent reusable skills.
- `source/concepts/` contains portable concept primitives meant to be copied, linked,
  or projected into other projects.
- `source/hooks/` contains reusable hook assets and runtime adapter configs.
- `source/plugins/` contains plugin composition manifests.
- `source/profiles/` contains schema-validated workflow profiles that select existing
  marketplace plugins, hooks, and validation commands.
- `source/templates/` contains primitive scaffold templates used by local tooling.
- `bin/` contains thin local command wrappers for repository tooling.
- `source/schemas/` contains public-facing JSON Schema contracts for primitive,
  plugin, marketplace, hook, and adapter surfaces.
- `apm.yml` is the checked APM marketplace manifest generated from the curated
  source catalog.
- `scripts/` contains root validation, packaging, APM staging, and marketplace
  publication tooling.
- `build/apm-marketplace/` is an untracked APM workspace generated from
  `source/` for local preview and release artifacts. Do not hand-edit it or
  commit hydrated package payloads.
- `package.json` and `package-lock.json` pin local validator dependencies.

## Terminology

- A primitive is an independent building block that can compose a plugin:
  skills, agents, hooks, instructions, and concept/principle documents.
- A plugin is a composition surface for primitives. Do not use plugin payloads
  as the only source of truth for a primitive.
- Marketplace exposure is curated to generally useful, project-agnostic
  primitives and plugin families. Private cleanup and migration material is not
  public repository content.

## Source Rules

- Keep primitives useful outside plugins. Plugins compose existing primitives;
  they do not own the only copy of a primitive.
- Keep `source/adaptable.marketplace.json` aligned with
  `source/schemas/marketplace/` and provider-neutral definitions in
  `source/schemas/core/`.
- Keep checked `apm.yml` aligned with `source/adaptable.marketplace.json` by
  running `python3 scripts/prepare-apm-marketplace.py manifest --check`.
- Every persisted structured data file must have an owning schema, typed parser,
  generator, or equivalent boundary assertion. For JSON in this repository,
  `node scripts/validate-manifests.mjs` is the coverage gate and must reject
  unvalidated files.

## Hook Rules

- Keep provider-neutral hook primitive metadata aligned with
  `source/schemas/core/hook.schema.json`.
- Keep runtime event names and matcher syntax in adapter configs such as
  `source/hooks/codex/*.json`; do not put adapter-specific behavior into the hook
  primitive metadata.
- Keep hook implementations host-portable. Prefer shell entrypoints with narrow,
  typed JSON parsing delegated to Python when the behavior is stateful.
- Store turn-local hook state outside tracked source. This repository uses
  `.agent-turn/` as local untracked state.

## Verification

- Run `bash -n source/hooks/*.sh` after editing shell hook entrypoints.
- Parse changed JSON hook assets with `python3 -m json.tool`.
- Run `node scripts/validate-manifests.mjs` after changing
  `source/adaptable.marketplace.json`, `source/plugins/*/plugin.json`, hooks,
  schemas, profiles, or any JSON manifest.
- Run `python3 scripts/prepare-apm-marketplace.py stage --out build/apm-marketplace --check-root-manifest`
  and `apm pack --marketplace=all --dry-run --check-versions --json` from
  `build/apm-marketplace` after changing marketplace exposure.
- When changing schema-aligned hook metadata, check required fields,
  `additionalProperties`, relative paths, and kebab-case names against
  `source/schemas/core/hook.schema.json`.
