# Instruction File Contract

Use this reference when drafting or reviewing scoped instruction files.

## Include

- A scope statement when the path alone is not enough.
- Local build, test, lint, codegen, docs, or release commands.
- Edit boundaries: what to edit, what is generated, and what to regenerate.
- Source-of-truth files or manifests that govern the subtree.
- Local conventions only when they differ from the parent or obvious repo
  defaults.
- Verification steps realistic for that subtree.
- Assumptions when the repository does not fully prove the workflow.

## Avoid

- Repeating parent guidance without narrowing or overriding it.
- Architecture essays with no actionable effect.
- Ownership claims unsupported by files, manifests, or commands.
- Guessed commands.
- Vague rules like "be careful" unless tied to a concrete action.

## Root File

The root instruction file should cover repo-wide constraints, shared safety
rules, source-of-truth layout, global validation posture, and pointers to major
work surfaces.

## Child File

A child instruction file should describe only the local delta:

- local commands;
- language or framework conventions;
- codegen or generated-output policy;
- subtree-specific validation;
- source ownership boundaries;
- local risks or approval gates.

## Minimal Shape

Use the repository's house style first. When no style exists, this shape is
usually enough:

```md
# Scope

- This file applies to `path/` and descendants.

# Work Here

- Build:
- Test:
- Generate:

# Edit Rules

- Edit:
- Do not edit:
- Regenerate via:

# Verify

- Run:
```
