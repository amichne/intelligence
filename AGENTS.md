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

- `source/adaptable.marketplace.json` is a minimal CLI validation catalog that
  imports `slopsentral/kotlin-engineering`.
- `/Users/amichne/code/slopsentral` is the canonical local source for reusable
  personal skills, plugin composition, hooks, agents, concepts, profiles, and
  generated provider payloads.
- `.intelligence/adaptable.marketplace.json` records install-only adaptable
  marketplace intent for consumer repositories that do not carry authored
  `source/` resources.
- `.intelligence/marketplace-lock.json` records resolved imported marketplace
  references and integrity evidence needed to reconstruct remote imports.
- `schemas/` contains public-facing JSON Schema contracts for primitive,
  plugin, marketplace, hook, and adapter surfaces.
- `cli/` contains the Kotlin Clikt command-line application.
- Provider marketplace payloads for personal tooling are materialized from
  `slopsentral`. This repository owns the CLI that performs the projection, not
  the personal marketplace source.

## Terminology

- A primitive is an independent building block: skills, agents, hooks,
  instructions, prompts, and concepts.
- A plugin is a composed installable workflow defined by a marketplace repository
  such as `slopsentral`.
- Marketplace exposure is curated in the owning marketplace repository, or
  through
  `.intelligence/adaptable.marketplace.json` for install-only consumer state.
- Marketplace imports are reconstructed through
  `.intelligence/marketplace-lock.json` and the global resolved asset cache.
- Provider payloads are generated marketplace output, not authoring source.

## Source Rules

- Keep the CLI validation catalog aligned with `schemas/marketplace/` and
  provider-neutral definitions in `schemas/core/`.
- Do not require consumer repositories to retain imported marketplace source
  directories. Installed intent belongs in `.intelligence/adaptable.marketplace.json`;
  exact resolved content belongs in `.intelligence/marketplace-lock.json`.
- Every persisted structured data file must have an owning schema, typed parser,
  generator, or equivalent boundary assertion. For JSON in this repository,
  `.local/intelligence/bin/intelligence validate` is the coverage gate and must
  reject unvalidated files.

## Verification

- Run `./gradlew :cli:test installDevelopmentCli` after changing Kotlin CLI code.
- Run `.local/intelligence/bin/intelligence validate --portable` after changing
  `source/adaptable.marketplace.json`, `.intelligence/adaptable.marketplace.json`,
  schemas, or any JSON manifest.
- Validate marketplace content changes in `/Users/amichne/code/slopsentral`.
