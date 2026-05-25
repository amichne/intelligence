---
name: hook-primitive-authoring
description: Create, revise, validate, or consolidate hook primitives using provider-neutral metadata, runtime adapter projections, executable implementations, and repo-local schema validation. Use when adding hooks, wiring hooks into plugins, authoring Codex hook adapters, or checking hook dependencies against skills, agents, concepts, and scripts.
---

# Hook Primitive Authoring

Use this skill to create reusable hook primitives. A hook should be useful as a
repo-owned primitive before any plugin composes it.

## Operating Contract

- Keep provider-neutral hook metadata in `hooks/<name>.hook.json`.
- Keep executable implementations at `hooks/<name>.*` unless they are
  provider-specific.
- Keep runtime adapter projections under adapter-named directories such as
  `hooks/codex/`.
- Reference related skills, agents, hooks, or concepts through metadata instead
  of copying their guidance into hook files.
- Validate hook metadata against the repo-local schemas through
  `node scripts/validate-manifests.mjs`.
- Treat every hook metadata file and runtime adapter as structured data with a
  mandatory schema-backed validation path.
- Run syntax checks for every touched executable hook implementation.
- Keep promoted hook source provenance public-safe.

## Workflow

1. Define the hook contract.
   Identify the event, trigger timing, expected input, output behavior, failure
   policy, timeout, and the primitive or workflow it enforces.

2. Choose the local files.
   Add neutral metadata at `hooks/<name>.hook.json`, implementation code at the
   hook root, and adapter config under `hooks/<adapter>/`.

3. Identify schemas.
   For neutral metadata, use the local `schemas/core/hook.schema.json` path
   through `node scripts/validate-manifests.mjs`. For runtime adapters, use the
   adapter schema when one exists and still parse JSON locally.

4. Declare dependencies.
   Use `dependsOn` in neutral metadata when the hook enforces or references a
   skill, agent, instruction, concept, or another hook. Do not embed the full
   upstream primitive text.

5. Implement thinly.
   Keep runtime adapters small. They should call the owning script or CLI and
   avoid reimplementing business logic in JSON config.

6. Validate locally.
   Run schema validation, JSON parsing, and executable syntax checks. For hooks
   that inspect changed files or repo state, run a representative local command
   with explicit arguments.

7. Compose.
   Add hooks to plugins by reference only after the hook exists independently.
   Keep plugin manifests declarative.

## Reference Routing

- Load [local-layout.md](references/local-layout.md) before adding or moving
  files under `hooks/`.
- Load [codex-adapters.md](references/codex-adapters.md) when writing
  `hooks/codex/*.hooks.json`.
- Load [schema-validation.md](references/schema-validation.md) when checking
  repo-local schema coverage, local references, and validation commands.
- Load [implementation-guidance.md](references/implementation-guidance.md)
  when writing or reviewing hook scripts.

## Completion Criteria

- Neutral metadata, runtime adapter, and implementation ownership are clear.
- Metadata validates through the repo-local manifest validator.
- Hook dependencies point at canonical primitives.
- Executable syntax or representative local execution has been checked.
- Plugins compose the hook by reference rather than copying hook payloads.
