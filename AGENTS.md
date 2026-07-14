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

- `source/adaptable.marketplace.json` is the provider-neutral marketplace
  fixture used to prove source resolution and projection.
- `/Users/amichne/code/slopsentral` is the canonical local source for reusable
  personal skills, plugin composition, hooks, agents, concepts, profiles, and
  generated provider payloads.
- `.intelligence/marketplace-lock.json` records exact source-resolution
  evidence for imported marketplace references.
- `schemas/` contains public-facing JSON Schema contracts for primitive,
  plugin, marketplace, hook, and adapter surfaces.
- `cli/` contains the Kotlin Clikt projector and its validation boundary.
- Provider marketplace payloads for personal tooling are materialized from
  `slopsentral`. This repository owns the CLI that performs the projection, not
  the personal marketplace source.

## Terminology

- A primitive is an independent building block: skills, agents, hooks,
  instructions, prompts, and concepts.
- A plugin is a composed workflow projected as one harness-native unit.
- Marketplace exposure is curated in the owning marketplace repository.
- Marketplace imports may be resolved through exact lock evidence as source
  input; resolution is not provider installation.
- Provider payloads are generated marketplace output, not authoring source.

## Source Rules

- Keep the CLI validation catalog aligned with `schemas/marketplace/` and
  provider-neutral definitions in `schemas/core/`.
- Keep installation, registration, publication, discovery, and consumer-state
  mutation outside the CLI. Its public job is source-to-harness conversion.
- Every persisted structured data file must have an owning schema, typed parser,
  generator, or equivalent boundary assertion. For JSON in this repository,
  projector tests are the coverage gate and must reject unvalidated files.

## Verification

- Run `./gradlew :cli:test installDevelopmentCli` after changing Kotlin CLI code.
- Run focused projection tests after changing source manifests, schemas, or
  adapter layouts.
- Validate marketplace content changes by projecting
  `/Users/amichne/code/slopsentral` to each supported harness.

<kast>
## Kast routing
Use `/Users/amichne/code/intelligence/.agents/skills/kast/SKILL.md` before Kotlin or Gradle semantic work.
Use `kast agent verify --workspace-root "$PWD"` to verify the plugin-prepared workspace.
Use typed commands such as `kast agent symbol --query <name>`, `kast agent diagnostics --file-path <path>`, and `kast agent rename --symbol <fq-name> --new-name <name> --apply`.
Do not run `kast setup` on macOS; the IntelliJ plugin owns workspace bootstrap.
</kast>
