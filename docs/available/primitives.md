# Primitives

Primitives are source-owned assets that can be composed into plugin families.

## Skills

Skills live under `source/skills/<name>/SKILL.md` and may include `references/`,
`scripts/`, `assets/`, and examples.

## Agents

Agents live under `source/agents/`.

## Instructions

Portable instructions and concepts live under `source/concepts/`.

## Hooks

Provider-neutral hook metadata lives in `source/hooks/*.hook.json`.
Provider-specific hook adapters live under directories such as
`source/hooks/codex/`. Executable scripts referenced by hook JSON live under the
hook root unless the adapter owns them.
