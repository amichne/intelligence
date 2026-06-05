# Primitives

Primitives are package-owned APM assets.

## Skills

Skills live under `packages/*/.apm/skills/<name>/SKILL.md` and may include
`references/`, `scripts/`, `assets/`, and examples.

## Agents

Agents live under `packages/*/.apm/agents/*.agent.md`.

## Instructions

Instructions live under `packages/*/.apm/instructions/*.instructions.md`.

## Hooks

Hooks live under `packages/*/.apm/hooks/*.json`. Target-specific Codex hooks
use names such as `<name>-codex-hooks.json`. Executable scripts referenced by
hook JSON live under package `hooks/`, while JSON sidecar config belongs under
`hook-config/` so APM does not scan it as a hook definition.
