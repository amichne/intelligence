# amichne-apm Agent Instructions

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
- `cli/` contains the Kotlin Clikt command-line application.
- `source/schemas/` contains public-facing JSON Schema contracts for primitive,
  plugin, marketplace, hook, and adapter surfaces.
- `scripts/` contains root validation helpers invoked by the Kotlin CLI.
- Provider marketplace payloads are materialized outputs. Generate them outside
  the source tree or publish them to generated branches through the Kotlin CLI;
  do not check in `.agents/plugins/`, `.github/plugin/`, `plugins/`, or
  `marketplace-lock.json`.
- `package.json` and `package-lock.json` pin local validator dependencies.

## Terminology

- A primitive is an independent building block that ships inside an APM package:
  skills, agents, hooks, instructions, and prompts.
- A package is the installable APM unit under `packages/<name>/`.
- Marketplace exposure is curated through the root `apm.yml`
  `marketplace.packages` list.
- Generated marketplace JSON is output, not authoring source.

## Source Rules

- Keep primitives useful outside plugins. Plugins compose existing primitives;
  they do not own the only copy of a primitive.
- Keep `source/adaptable.marketplace.json` aligned with
  `source/schemas/marketplace/` and provider-neutral definitions in
  `source/schemas/core/`.
- Every persisted structured data file must have an owning schema, typed parser,
  generator, or equivalent boundary assertion. For JSON in this repository,
  `.local/intelligence/bin/intelligence validate` is the coverage gate and must
  reject unvalidated files.

## Hook Rules

- Keep hook JSON under `packages/*/.apm/hooks/`.
- Use APM target suffixes such as `<name>-codex-hooks.json` for target-specific
  hook files.
- Keep hook sidecar JSON under `packages/*/hook-config/`.
- Keep hook implementations host-portable. Prefer shell entrypoints with narrow,
  typed JSON parsing delegated to Python when the behavior is stateful.
- Store turn-local hook state outside tracked source. This repository uses
  `.agent-turn/` as local untracked state.

## Verification

- Run `bash -n source/hooks/*.sh` after editing shell hook entrypoints.
- Parse changed JSON hook assets with `python3 -m json.tool`.
- Run `.local/intelligence/bin/intelligence validate` after changing
  `source/adaptable.marketplace.json`, `source/plugins/*/plugin.json`, hooks,
  schemas, profiles, or any JSON manifest.
- When changing schema-aligned hook metadata, check required fields,
  `additionalProperties`, relative paths, and kebab-case names against
  `source/schemas/core/hook.schema.json`.
