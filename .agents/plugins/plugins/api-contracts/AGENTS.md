# API Contracts Plugin Instructions

## Scope

This generated adapter applies to the `api-contracts` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.

## Runtime Boundary

The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.

## Plugin Intent

API contract workflow for JSON Schema and OpenAPI boundaries: schema modeling, operation authoring, typed errors, examples, and review.

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

## Agent Profile Primitives

- `openapi-contract-rater`: `agents/openapi-contract-rater.agent.md` (source: `source/agents/openapi/openapi-contract-rater.agent.md`)
- `schema-type-enforcer`: `agents/schema-type-enforcer.agent.md` (source: `source/agents/schema-type-enforcer.agent.md`)

## Skill Primitives

- `manage-json-schemas`: `skills/manage-json-schemas` (source: `source/skills/manage-json-schemas`)
- `openapi-contract-authoring`: `skills/openapi-contract-authoring` (source: `source/skills/openapi-contract-authoring`)
- `openapi-contract-rating`: `skills/openapi-contract-rating` (source: `source/skills/openapi-contract-rating`)
- `openapi-schema-modeling`: `skills/openapi-schema-modeling` (source: `source/skills/openapi-schema-modeling`)
