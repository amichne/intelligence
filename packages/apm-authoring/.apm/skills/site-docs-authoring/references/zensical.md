# Zensical Sites

Use this reference when a repository owns docs through `zensical.toml`.
Zensical sites often expose MkDocs-compatible page features while moving the
site contract into TOML.

## Discovery

- Read `zensical.toml` before editing docs.
- Find the docs root, usually `docs/`, and any local `docs/AGENTS.md`.
- Treat the TOML nav as the source of truth for page discovery.
- Identify enabled Markdown extensions and theme features before using tabs,
  grids, icons, admonitions, Mermaid, or custom overrides.

## Authoring Pattern

- Keep config examples in TOML unless the page is intentionally comparing
  dialects.
- Add a new page and its nav entry in the same change.
- Use front matter only when it drives page title, description, icon, status,
  sidebar visibility, or template choice.
- Use landing pages to route readers into task, concept, recipe, and reference
  sections.
- Prefer a small set of repeated components across the site so pages feel like
  the same product surface.

## Common Page Features

- Grids: entry pages, option overviews, and "where to go next" sections.
- Tabs: command/API alternatives, platform variants, or config dialects.
- Admonitions: constraints, generated-file warnings, version notes, or safety
  caveats that should not interrupt the main narrative.
- Tables: lookup data, capability comparison, option matrices, and command
  summaries.
- Mermaid: lifecycle, state, dependency, request, or architecture flows.

## Validation

- Prefer `zensical build --clean` after nav, config, extension, or layout
  changes.
- If the repo wraps the command, use the wrapper.
- If generated reference pages are involved, run the generator or the repo's
  stale-docs check before the site build.
