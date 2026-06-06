# Progressive Disclosure

Use this reference to keep skills compact and loadable.

## File Roles

- `SKILL.md`: trigger conditions, workflow, completion criteria, and reference
  routing.
- `references/`: detailed policies, schemas, examples, provider variants, or
  domain context that should be loaded only when needed.
- `scripts/`: deterministic utilities that are safer to execute than rewrite.
- `assets/`: output resources such as templates, icons, fixtures, or starter
  files.
- `evals/` and `history/`: durable proof assets for skills with objective
  evaluation needs.

## Splitting Rules

- Keep the main body under the threshold where an agent can skim it quickly.
- Split when examples outnumber rules, variants dominate the core workflow, or
  the same details would only apply to some requests.
- Keep references one level below `SKILL.md`.
- Link every reference from `SKILL.md` with a condition for when to read it.
- Do not create README, changelog, install guide, or status files inside a
  skill unless the skill's output explicitly requires that asset.

## Naming Rules

- Use lower-case hyphenated skill names.
- Choose capability names that do not collide with first-party or installed
  skill names.
- Prefer local semantic names such as `reference-doc-workflow` over upstream
  names when synthesizing from distributed material.

## Review Questions

- Does this sentence change how the agent works?
- Can a detail move to a reference without weakening the trigger?
- Would a deterministic script reduce repeated fragile code generation?
- Is this path stable if the skill is installed outside the current repo?
- Is the skill still useful if no plugin composes it?
