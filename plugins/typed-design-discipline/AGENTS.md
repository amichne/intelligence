# Typed Design Discipline Plugin Instructions

## Scope

This generated adapter applies to the `typed-design-discipline` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.

## Runtime Boundary

The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.

## Plugin Intent

Core type-safety, schema-driven design, onboarding, and turn-time hooks for rigorous repository work.

## Operating Rules

- Treat this file as an adapter, not a new source of truth.
- Use bundled skills for step-by-step workflows.
- Apply bundled instructions as normative guidance when their scope matches the task.
- Treat bundled agent profiles as review criteria or focused review passes.
- Keep hook behavior in bundled hook files and runtime adapter configs.
- When guidance conflicts with the target repository's nearest `AGENTS.md`, follow the target repository unless the user explicitly chooses this plugin's rule.

## Instruction Primitives

- `schema-driven-design`: `instructions/schema-driven-design.md` (source: `source/concepts/schema-driven-design/core.md`)
- `type-safety`: `instructions/type-safety.md` (source: `source/concepts/type-safety/core.md`)

## Skill Primitives

- `repository-onboarding`: `skills/repository-onboarding` (source: `source/skills/repository-onboarding`)

## Hook Primitives

- `agents-md-turn-refresh`: `hooks/agents-md-turn-refresh.hooks.json` (source: `source/hooks/agents-md-turn-refresh.hook.json`)
- `required-skill-read`: `hooks/required-skill-read.hooks.json` (source: `source/hooks/required-skill-read.hook.json`)
