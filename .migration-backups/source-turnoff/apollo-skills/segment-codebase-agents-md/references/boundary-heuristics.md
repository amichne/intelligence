# Boundary Heuristics

Use these signals to decide whether a subtree deserves its own `AGENTS.md`.
Create the file only when the local instructions would materially change how an
agent should work there.

## Strong split signals

- Distinct build, test, lint, codegen, or release commands.
- Distinct language or framework conventions.
- Distinct deployment or runtime surface.
- Generated code or "do not hand-edit" zones.
- Contract source-of-truth directories that feed generation elsewhere.
- Infrastructure directories with plan or apply loops that differ from app code.
- Templates or scaffolds where regeneration policy matters.
- Safety-critical or high-blast-radius directories with stricter validation.

## Weak split signals

- Extra folder depth with no command or policy change.
- Pure package layout differences inside one module.
- Empty directories or directories that only mirror the parent workflow.
- Cosmetic naming differences.
- A desire for symmetry without evidence that the instructions differ.

## Parent and child rules

- Put shared rules at the highest ancestor that truly owns them.
- Put local deltas at the nearest subtree that needs them.
- Do not create both a parent and a child file if the child would only restate
  the parent.
- Add a child file when a contributor working inside that subtree would need
  different commands, constraints, or edit policies from the parent.

## Polyglot patterns

### Gradle multi-module

- Start with the root build, `settings.gradle*`, version catalogs, convention
  plugins, included builds, and codegen tasks.
- Split by module group only when those modules share a local workflow that
  differs from the rest of the repo.
- Split by individual module only when the module has unique commands, codegen,
  or operational rules.

### Custom contract trees

- Separate source contracts from generated clients or stubs.
- Isolate handwritten adapters or extensions if they are edited differently
  from the generated outputs.
- Make regeneration entry points explicit.

### Mixed app, library, and infra repos

- Keep one repo-level file for global rules.
- Add child files for deployable apps, shared libraries with special release
  rules, and infra roots with non-code validation loops.

### Templates and scaffolds

- Create a local file where the template is the source of truth.
- Create another local file for downstream generated instances only when their
  local edit policy differs.

## Anti-patterns

- One `AGENTS.md` per directory.
- A root file that tries to encode every local exception.
- Child files that copy the root file verbatim.
- Instructions based on guessed commands rather than repository evidence.
- Treating generated output as the authoritative place to edit behavior unless
  the repo explicitly works that way.
