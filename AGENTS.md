# amichne-intelligence Agent Instructions

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

- `source/adaptable.marketplace.json` is the provider-neutral curated
  marketplace catalog and the source of truth for plugin exposure.
- `source/agents/` contains independent reusable agent profiles.
- `source/skills/` contains independent reusable skills.
- `source/concepts/` contains portable concept primitives meant to be copied,
  linked, or projected into other projects.
- `source/hooks/` contains reusable hook assets and runtime adapter configs.
- `source/plugins/` contains plugin composition manifests.
- `source/profiles/` contains schema-validated workflow profiles that select
  marketplace plugins, hooks, and validation commands.
- `source/templates/` contains primitive scaffold templates used by local tooling.
- `schemas/` contains public-facing JSON Schema contracts for primitive,
  plugin, marketplace, hook, and adapter surfaces.
- `cli/` contains the Kotlin Clikt command-line application.
- Provider marketplace payloads are materialized outputs. `main` carries
  CI-generated default harness payloads under `.agents/plugins/` and
  `.github/plugin/`; do not edit those by hand. Provider orphan branches are
  still generated through the Kotlin CLI with `marketplace publish --codex` or
  `marketplace publish --github`.

## Terminology

- A primitive is an independent building block: skills, agents, hooks,
  instructions, prompts, and concepts.
- A plugin is a composed installable workflow defined under `source/plugins/`.
- Marketplace exposure is curated through `source/adaptable.marketplace.json`.
- Provider payloads are generated marketplace output, not authoring source.
  Treat tracked `.agents/plugins/` and `.github/plugin/` files as CI-owned
  publication artifacts.

## Source Rules

- Keep primitives useful outside plugins. Plugins compose existing primitives;
  they do not own the only copy of a primitive.
- Keep `source/adaptable.marketplace.json` aligned with
  `schemas/marketplace/` and provider-neutral definitions in
  `schemas/core/`.
- Every persisted structured data file must have an owning schema, typed parser,
  generator, or equivalent boundary assertion. For JSON in this repository,
  `.local/intelligence/bin/intelligence validate` is the coverage gate and must
  reject unvalidated files.

## Hook Rules

- Keep provider-neutral hook metadata in `source/hooks/*.hook.json`.
- Keep adapter-specific hook JSON under directories such as `source/hooks/codex/`.
- Keep hook sidecar JSON next to the hook implementation or metadata that owns it.
- Keep hook implementations host-portable. Prefer shell entrypoints with narrow,
  typed JSON parsing delegated to Python when the behavior is stateful.
- Store turn-local hook state outside tracked source. This repository uses
  `.agent-turn/` as local untracked state.

## Verification

- Run `bash -n source/hooks/*.sh` after editing shell hook entrypoints.
- Parse changed JSON hook assets with `python3 -m json.tool`.
- Run `./gradlew :cli:test installDevelopmentCli` after changing Kotlin CLI code.
- Run `.local/intelligence/bin/intelligence validate` after changing
  `source/adaptable.marketplace.json`, `source/plugins/*/plugin.json`, hooks,
  schemas, profiles, or any JSON manifest.
- When changing schema-aligned hook metadata, check required fields,
  `additionalProperties`, relative paths, and kebab-case names against
  `schemas/core/hook.schema.json`.
