---
name: "skill-primitive-authoring"
description: "Create, revise, consolidate, or evaluate reusable skill primitives while keeping them host-neutral, compact, reference-driven, and independently useful without any plugin. Use when turning repeated agent work into a skill, splitting skill details into references/scripts, evaluating skill usefulness, or reducing overlap between existing skills."
---

# Skill Primitive Authoring

Use this skill to turn repeated work into a durable skill primitive. The skill
must stand on its own: a plugin may compose it later, but the skill cannot rely
on the plugin to explain its behavior.

## Operating Contract

- Start from observed repeated work, user corrections, logs, or existing source
  material rather than a generic capability wish.
- Keep `SKILL.md` as the trigger, workflow, and reference router.
- Put variant details, schemas, examples, and long policies in `references/`.
- Put deterministic repeated work in `scripts/`.
- Keep runtime/UI metadata optional and adapter-owned.
- For structured data owned or emitted by the skill, require a schema,
  generator, parser, or validation command before persisting the data.
- Avoid first-party or installed-skill name collisions when synthesizing from
  OpenAI, Anthropic, or other distributed sources.
- Keep source provenance public-safe when promoting a skill into this
  repository.

## Workflow

1. Define the contract.
   Identify what the skill should help an agent do, when it should trigger,
   what success means, and what source material proves the workflow.

2. Check overlap.
   Search existing `skills/`. Prefer improving or synthesizing an existing skill
   over adding a narrow duplicate.

3. Design the shape.
   Use a compact `SKILL.md` plus one-level references. Add scripts only when a
   step needs deterministic reliability or would otherwise be rewritten often.

4. Write the skill.
   Put the trigger language in frontmatter `description`. Keep the body
   procedural and concrete. Link every optional reference from `SKILL.md` with
   a clear "read this when..." condition.

5. Validate.
   Confirm frontmatter parses, references exist, paths are stable, and the
   skill does not raw-copy first-party content. If the skill has eval assets,
   validate the eval scaffold and keep transient benchmark output outside the
   skill folder.

6. Promote.
   Add the skill to `adaptable.marketplace.json` and any referential plugin that should
   compose it. If promotion changes any JSON shape, update the owning schema
   first and run manifest validation.

## Reference Routing

- Load [progressive-disclosure.md](references/progressive-disclosure.md) when
  deciding what belongs in `SKILL.md`, `references/`, `scripts/`, or `assets/`.
- Load [evaluation-scaffold.md](references/evaluation-scaffold.md) when a skill
  needs objective proof, holdouts, or consolidation evidence.
- Load [interface-adapters.md](references/interface-adapters.md) when adding
  UI-facing metadata or host-specific adapters.

## Completion Criteria

- The skill has a clear trigger, workflow, and success contract.
- Related details are discoverable without loading unnecessary context.
- Existing overlap was checked and either avoided or intentionally synthesized.
- Source provenance and first-party handling are recorded in public-safe form
  when applicable.
- Structured data created or changed by the skill has an owning schema,
  generator, parser, or validation command.
- Manifest validation passes after promotion.
