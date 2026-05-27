# Evidence Driven Delivery Plugin Instructions

## Scope

This generated adapter applies to the `evidence-driven-delivery` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.

## Runtime Boundary

The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.

## Plugin Intent

Goal definition, test-driven implementation, Git hygiene, CI triage, and PR lifecycle procedures with explicit proof.

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

- `define-goal`: `skills/define-goal` (source: `skills/define-goal`)
- `git-change-flow`: `skills/git-change-flow` (source: `skills/git-change-flow`)
- `github-ci-operations`: `skills/github-ci-operations` (source: `skills/github-ci-operations`)
- `pull-request-lifecycle`: `skills/pull-request-lifecycle` (source: `skills/pull-request-lifecycle`)
- `tdd`: `skills/tdd` (source: `skills/tdd`)
