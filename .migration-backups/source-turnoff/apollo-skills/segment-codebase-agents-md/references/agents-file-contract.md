# AGENTS.md File Contract

Write each `AGENTS.md` as scoped operating guidance for the directory tree it
governs.

## Include

- A clear scope statement if the boundary is not obvious from the directory.
- The local build, test, lint, or codegen commands that apply there.
- Edit boundaries:
  what is safe to edit, what is generated, what should be regenerated instead.
- Validation steps that are realistic for that subtree.
- Local conventions only when they differ from the parent or from obvious repo
  defaults.
- Assumptions when the repository does not fully prove the intended workflow.

## Avoid

- Repeating global guidance that already belongs in a parent `AGENTS.md`.
- Architecture essays with no actionable effect on implementation.
- Ownership or process claims that are not evidenced in the repo.
- Guessed commands.
- Vague rules like "be careful" or "follow best practices" without a local
  action attached.

## Root file expectations

- Capture repository-wide constraints, shared safety rules, and common
  validation posture.
- Point agents toward the repo's major build and test entry points.
- Prefer repo-root entry points and area pointers over subtree-only detail.
- Leave module-specific or language-specific exceptions to child files.

## Child file expectations

- Describe only the local delta from the parent guidance.
- Name the local toolchain, commands, and edit restrictions.
- Mention nearest source-of-truth inputs when the subtree contains generated
  outputs.
- Omit commands that are already clear at the parent level unless the local
  invocation, prerequisites, or edit policy differ.
- Keep the file short enough that a contributor can act on it immediately.

## Minimal structure

Use the smallest structure that stays clear. A simple pattern is usually enough:

```md
# Scope

- This file applies to `path/` and its descendants.

# Work Here

- Build:
- Test:
- Lint:
- Generate:

# Edit Rules

- Edit:
- Do not edit:
- Regenerate via:

# Verify

- Run:
```

Do not force this template if the repository already has a clearer house style.
Keep the content concrete and aligned with the existing repo conventions.
