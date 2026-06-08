# Hook Primitive Instructions

## Scope

This file applies to reusable hook assets under `source/hooks/`.

## Contract

- Keep hook implementations usable without installing any plugin.
- Store provider-neutral hook metadata in `*.hook.json`.
- Store runtime adapter projections under adapter-named directories such as
  `source/hooks/codex/`.
- Keep executable scripts at the hook root unless they are adapter-specific.
- Reference related skills, agents, or concepts through `dependsOn` in the hook
  metadata rather than embedding their full guidance.
- Do not point hook metadata at runtime caches, installed plugin copies, or
  generated bundles as authority.
- If a hook came from another source, keep public-safe provenance in the hook or
  plugin documentation.

## Verify

- Run `.local/intelligence/bin/intelligence validate` after changing hook metadata.
- Run syntax checks for every executable hook implementation touched.
