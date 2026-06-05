---
name: "hook-primitive-authoring"
description: "Create, revise, validate, or consolidate APM hook primitives using .apm hook JSON, executable implementations, target suffixes, and APM package validation. Use when adding hooks, wiring hooks into packages, authoring Codex hook adapters, or checking hook dependencies against skills, agents, instructions, and scripts."
---

# Hook Primitive Authoring

Use this skill to create reusable hook primitives. A hook should be useful as a
package-owned primitive before any marketplace entry exposes it.

## Operating Contract

- Keep hook JSON in `.apm/hooks/`; use suffixes such as
  `<name>-codex-hooks.json` for target-specific hooks.
- Keep executable implementations under package `hooks/` so APM can rewrite
  command paths during install.
- Reference related skills, agents, hooks, or instructions by package contents
  or docs instead of copying their guidance into hook files.
- Validate package hooks with `apm pack --marketplace=all --dry-run
  --check-versions --json` and representative script checks.
- Treat every hook JSON file as structured data with a validation path.
- Run syntax checks for every touched executable hook implementation.
- Keep promoted hook primitive provenance public-safe.

## Workflow

1. Define the hook contract.
   Identify the event, trigger timing, expected input, output behavior, failure
   policy, timeout, and the primitive or workflow it enforces.

2. Choose the local files.
   Add target-specific hook JSON at `.apm/hooks/<name>-codex-hooks.json` and
   implementation code under package `hooks/`.

3. Identify schemas.
   Use APM package validation for hook discovery and parse changed JSON locally
   with `python3 -m json.tool`. For target adapters, keep the runtime schema in
   mind even when APM is the publishing boundary.

4. Declare dependencies.
   Record related skills, agents, instructions, or scripts in package docs or
   adjacent requirement files when the hook depends on them. Do not embed the
   full upstream primitive text.

5. Implement thinly.
   Keep runtime adapters small. They should call the owning script or CLI and
   avoid reimplementing business logic in JSON config.

6. Validate locally.
   Run schema validation, JSON parsing, and executable syntax checks. For hooks
   that inspect changed files or repo state, run a representative local command
   with explicit arguments.

7. Publish.
   Add or update the package entry in the root `apm.yml` only after the hook
   exists in the package and local validation passes.

## Reference Routing

- Load [local-layout.md](references/local-layout.md) before adding or moving
  files under `package hooks/`.
- Load [codex-adapters.md](references/codex-adapters.md) when writing
  `.apm/hooks/*-codex-hooks.json`.
- Load [schema-validation.md](references/schema-validation.md) when checking
  APM manifest and package coverage, local references, and validation commands.
- Load [implementation-guidance.md](references/implementation-guidance.md)
  when writing or reviewing hook scripts.

## Completion Criteria

- Neutral metadata, runtime adapter, and implementation ownership are clear.
- Hook JSON validates through APM package checks and JSON parsing.
- Hook dependencies point at package-owned primitives or documented inputs.
- Executable syntax or representative local execution has been checked.
- Packages ship the hook from `.apm/hooks/` rather than relying on generated
  runtime output.
