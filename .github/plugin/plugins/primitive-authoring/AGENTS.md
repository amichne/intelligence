# Primitive Authoring Plugin Instructions

## Scope

This generated adapter applies to the `primitive-authoring` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.

## Runtime Boundary

The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.

## Plugin Intent

Skill, agent, hook, shell script, schema, and referential plugin authoring workflows composed from independent primitives.

## Operating Rules

- Treat this file as an adapter, not a new source of truth.
- Use bundled skills for step-by-step workflows.
- Apply bundled instructions as normative guidance when their scope matches the task.
- Treat bundled agent profiles as review criteria or focused review passes.
- Keep hook behavior in bundled hook files and runtime adapter configs.
- When guidance conflicts with the target repository's nearest `AGENTS.md`, follow the target repository unless the user explicitly chooses this plugin's rule.

## Instruction Primitives

- `schema-driven-design`: `instructions/schema-driven-design.md` (source: `concepts/schema-driven-design/core.md`)

## Skill Primitives

- `agent-profile-authoring`: `skills/agent-profile-authoring` (source: `skills/agent-profile-authoring`)
- `hook-primitive-authoring`: `skills/hook-primitive-authoring` (source: `skills/hook-primitive-authoring`)
- `manage-json-schemas`: `skills/manage-json-schemas` (source: `skills/manage-json-schemas`)
- `plugin-composition-authoring`: `skills/plugin-composition-authoring` (source: `skills/plugin-composition-authoring`)
- `shell-script-safety`: `skills/shell-script-safety` (source: `skills/shell-script-safety`)
- `skill-primitive-authoring`: `skills/skill-primitive-authoring` (source: `skills/skill-primitive-authoring`)
