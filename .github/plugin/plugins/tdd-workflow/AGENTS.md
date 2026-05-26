# TDD Workflow Plugin Instructions

## Scope

This generated adapter applies to the `tdd-workflow` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.

## Runtime Boundary

The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.

## Plugin Intent

Test-driven workflow composed from an independent TDD skill and source-of-truth design concepts.

## Operating Rules

- Treat this file as an adapter, not a new source of truth.
- Use bundled skills for step-by-step workflows.
- Apply bundled instructions as normative guidance when their scope matches the task.
- Treat bundled agent profiles as review criteria or focused review passes.
- Keep hook behavior in bundled hook files and runtime adapter configs.
- When guidance conflicts with the target repository's nearest `AGENTS.md`, follow the target repository unless the user explicitly chooses this plugin's rule.

## Instruction Primitives

- `schema-driven-design`: `instructions/schema-driven-design.md` (source: `concepts/schema-driven-design/core.md`)
- `type-safety`: `instructions/type-safety.md` (source: `concepts/type-safety/core.md`)

## Skill Primitives

- `tdd`: `skills/tdd` (source: `skills/tdd`)
