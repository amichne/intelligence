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

- `apm.yml` is the authoritative APM marketplace manifest.
- `packages/*/apm.yml` contains package-level APM metadata.
- `packages/*/.apm/skills/` contains package-owned skill primitives.
- `packages/*/.apm/agents/` contains package-owned agent primitives.
- `packages/*/.apm/instructions/` contains package-owned instruction primitives.
- `packages/*/.apm/hooks/` contains package-owned hook JSON. Use APM target
  suffixes such as `-codex-hooks.json` for target-specific hooks.
- `packages/*/hooks/` contains executable hook scripts referenced from hook JSON.
- `packages/*/hook-config/` contains hook sidecar config. Keep JSON sidecars out
  of `packages/*/hooks/` because APM scans `hooks/*.json` as hook definitions.
- `.claude-plugin/marketplace.json` and `.agents/plugins/marketplace.json` are
  APM-generated marketplace outputs when `apm pack --marketplace=all` runs.
- `docs/` contains the public documentation site source.

## Terminology

- A primitive is an independent building block that ships inside an APM package:
  skills, agents, hooks, instructions, and prompts.
- A package is the installable APM unit under `packages/<name>/`.
- Marketplace exposure is curated through the root `apm.yml`
  `marketplace.packages` list.
- Generated marketplace JSON is output, not authoring source.

## Source Rules

- Keep primitives useful beyond any one marketplace listing.
- Keep root `apm.yml` as the only marketplace source of truth.
- Keep package primitives under `.apm/`; do not author source-only primitive
  graphs that require custom generators.
- Prefer APM commands over repository-specific wrappers.
- Every persisted structured data file should have an owning APM validation
  path, schema, typed parser, generator, or equivalent boundary assertion.

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

- Run `python3 -m json.tool <file>` for changed JSON hook assets.
- Run `bash -n packages/*/hooks/*.sh` after editing shell hook entrypoints.
- Run `python3 -m py_compile packages/*/hooks/*.py` after editing Python hook
  entrypoints.
- Run `apm pack --marketplace=all --dry-run --check-versions --json` after
  changing packages or marketplace exposure.
- Run `apm audit --ci --no-policy` before publishing.
- Run `zensical build --clean` after changing docs or navigation.
