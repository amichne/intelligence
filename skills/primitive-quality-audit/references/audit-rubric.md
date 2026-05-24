# Audit Rubric

Use these checks separately. A primitive can pass one dimension and still be
blocked by another.

## Capability Boundary

- The primitive has one recognizable job.
- The trigger or invocation language names the situations where it should be
  used.
- It does not absorb unrelated workflows just because they came from the same
  source directory.

## Independence And Composition

- The primitive remains useful without any plugin.
- Plugin manifests reference canonical primitive paths instead of copying
  payloads into plugin folders.
- Cross-primitive references are intentional and visible.

## Progressive Disclosure

- The entry file contains trigger, workflow, and routing guidance.
- Long policies, examples, variants, and command catalogs live in references or
  scripts.
- A reader can follow the core workflow without loading every supporting file.

## Schema Coverage

- Persisted structured data has an owning schema, generator, typed parser, or
  validation command.
- New JSON files are covered by `node scripts/validate-manifests.mjs`.
- Schema shape changes land before or with the data they govern.
- Examples and fixtures are validated when they are meant to define behavior.

## Provenance And Collision Safety

- Promotion records preserve source roots and resolved paths.
- First-party sources are renamed, rewritten, and recorded with explicit
  handling.
- Canonical names avoid installed OpenAI, Anthropic, or marketplace collisions
  unless replacement is intentional and manifest-recorded.
- Raw digest matches against first-party sources are treated as blockers.

## Executable Evidence

- The audit cites commands, generated reports, schema validation, syntax checks,
  or deterministic scripts where possible.
- Manual judgment is labeled as such and bounded to the evidence available.
- Failing checks become findings, not footnotes.

## Runtime And Cleanup Safety

- Runtime exposure is represented in `manifests/runtime-links.json` before any
  write to runtime targets.
- Cleanup intent is represented in `manifests/cleanup-ledger.json` before any
  source deletion or symlink replacement.
- The rollback path is concrete enough for another agent to follow.
