# Primitive Systems Authoring Plugin Instructions

## Scope

This generated adapter applies to the `primitive-systems-authoring` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.

## Runtime Boundary

The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.

## Plugin Intent

Authoring procedures for independent skills, agents, hooks, schemas, plugins, repository maps, and documentation systems.

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

- `agent-profile-authoring`: `skills/agent-profile-authoring` (source: `skills/agent-profile-authoring`)
- `hook-primitive-authoring`: `skills/hook-primitive-authoring` (source: `skills/hook-primitive-authoring`)
- `local-repository-navigation`: `skills/local-repository-navigation` (source: `skills/local-repository-navigation`)
- `manage-json-schemas`: `skills/manage-json-schemas` (source: `skills/manage-json-schemas`)
- `plugin-composition-authoring`: `skills/plugin-composition-authoring` (source: `skills/plugin-composition-authoring`)
- `reference-doc-workflow`: `skills/reference-doc-workflow` (source: `skills/reference-doc-workflow`)
- `repo-instruction-topology`: `skills/repo-instruction-topology` (source: `skills/repo-instruction-topology`)
- `repository-signature-indexing`: `skills/repository-signature-indexing` (source: `skills/repository-signature-indexing`)
- `shell-script-safety`: `skills/shell-script-safety` (source: `skills/shell-script-safety`)
- `site-docs-authoring`: `skills/site-docs-authoring` (source: `skills/site-docs-authoring`)
- `skill-primitive-authoring`: `skills/skill-primitive-authoring` (source: `skills/skill-primitive-authoring`)
