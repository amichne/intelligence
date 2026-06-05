# Agent Platform Authoring Plugin Instructions

## Scope

This generated adapter applies to the `agent-platform-authoring` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.

## Runtime Boundary

The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.

## Plugin Intent

Agent platform authoring kit for maintaining the source graph: skills, agents, hooks, schemas, plugin manifests, repo maps, and docs surfaces.

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

- `agent-profile-authoring`: `skills/agent-profile-authoring` (source: `source/skills/agent-profile-authoring`)
- `hook-primitive-authoring`: `skills/hook-primitive-authoring` (source: `source/skills/hook-primitive-authoring`)
- `local-repository-navigation`: `skills/local-repository-navigation` (source: `source/skills/local-repository-navigation`)
- `manage-json-schemas`: `skills/manage-json-schemas` (source: `source/skills/manage-json-schemas`)
- `plugin-composition-authoring`: `skills/plugin-composition-authoring` (source: `source/skills/plugin-composition-authoring`)
- `reference-doc-workflow`: `skills/reference-doc-workflow` (source: `source/skills/reference-doc-workflow`)
- `repo-instruction-topology`: `skills/repo-instruction-topology` (source: `source/skills/repo-instruction-topology`)
- `repository-signature-indexing`: `skills/repository-signature-indexing` (source: `source/skills/repository-signature-indexing`)
- `shell-script-safety`: `skills/shell-script-safety` (source: `source/skills/shell-script-safety`)
- `site-docs-authoring`: `skills/site-docs-authoring` (source: `source/skills/site-docs-authoring`)
- `skill-primitive-authoring`: `skills/skill-primitive-authoring` (source: `source/skills/skill-primitive-authoring`)
