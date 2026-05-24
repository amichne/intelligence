# Skill Primitive Instructions

## Scope

This file applies to reusable skills under `skills/`.

## Contract

- Keep each skill usable without installing any plugin.
- Keep `SKILL.md` as the trigger, workflow, and reference router. Move detailed
  examples, policies, and variant-specific guidance into `references/`.
- Keep references one level below the skill unless a tool-specific format
  requires otherwise.
- Reference shared concepts with repository-relative paths such as
  `concepts/type-safety/core.md` instead of duplicating the full concept text.
- For any structured data a skill creates, updates, validates, or documents,
  require a schema-driven workflow. Name the schema, parser, generator, or
  validation command; do not leave JSON/YAML/TOML shape as prose-only guidance.
- When synthesizing from OpenAI, Anthropic, or other first-party skills, use a
  local non-colliding skill name and rewrite the body instead of raw-copying the
  first-party text.
- Do not point a skill at a runtime cache, installed plugin copy, or generated
  bundle as its authority.
- If a skill came from another local source, record that source in
  `garden/manifests/promotions.json`.

## Verify

- Run `python3 garden/scripts/inventory-primitives.py --check` after changing skill
  files.
- Run `node scripts/validate-manifests.mjs` after adding promoted skills to a
  plugin or marketplace manifest.
