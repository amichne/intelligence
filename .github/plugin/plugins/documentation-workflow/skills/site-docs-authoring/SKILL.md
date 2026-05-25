---
name: site-docs-authoring
description: Create, revise, or restructure technical documentation sites that use MkDocs, Zensical, Material-style Markdown features, or similar docs-as-code navigation. Use when documentation work needs site config awareness, page structure, nav updates, generated-doc boundaries, or consistent prose across docs pages.
---

# Site Docs Authoring

Use this skill when the work is not just a single document, but part of a
documentation site. Keep the skill independent of any plugin: inspect the local
site, preserve its dialect, edit the docs and navigation together, and verify
the rendered contract when layout or nav changes.

## Operating Contract

- Treat the docs site as a source-of-truth surface, not a prose dump.
- Read the local docs config before choosing structure: `zensical.toml`,
  `mkdocs.yml`, `mkdocs.yaml`, `docs/AGENTS.md`, and nearby page patterns.
- Preserve the current site dialect unless the user explicitly asks to migrate.
- Do not hand-edit generated reference pages; find and update the generator or
  record the generated boundary.
- Keep primary rules in the main page and move long examples, variants, or
  background into linked reference pages.
- Use site features to improve comprehension: tabs for alternatives, grids for
  entry points, admonitions for side constraints, tables for lookup, and Mermaid
  for flow or structure.
- Ground claims in code, configs, command output, schema files, or documented
  decisions.

## Workflow

1. Map the site.
   Identify the config file, docs root, nav owner, theme features, Markdown
   extensions, generated directories, and validation commands.

2. Define the reader path.
   Establish the page type, audience, intended reader action, source material,
   sibling pages, and whether the page belongs in existing navigation.

3. Match the house structure.
   Reuse the site's front matter, heading style, lead paragraphs, page cards,
   callouts, diagrams, examples, and link style. If the repo has docs
   instructions, follow them first.

4. Draft or edit in place.
   Update Markdown, config, nav, assets, and cross-links as one change set.
   Keep headings task- or concept-oriented and put an orienting paragraph after
   each major heading before lists or subheadings.

5. Verify.
   Run the repo's docs build when nav, config, theme features, links, or
   generated pages changed. Prefer `zensical build --clean` for Zensical sites
   and `mkdocs build --strict` or the repo's wrapper for MkDocs sites. If a
   build cannot run, report the missing tool or dependency.

## Reference Routing

- Load [zensical.md](references/zensical.md) when the site uses
  `zensical.toml` or Zensical-specific page features.
- Load [mkdocs.md](references/mkdocs.md) when the site uses `mkdocs.yml` or
  Material for MkDocs directly.
- Load [page-structures.md](references/page-structures.md) when creating a new
  page, reorganizing navigation, or changing the reader journey.
- Load [prose-structure.md](references/prose-structure.md) when the request is
  mainly about style consistency, agent-readable reference material, or
  preserving a site's prose shape.

## Completion Criteria

- The page has an explicit reader job and fits the surrounding nav.
- Site config and nav are updated when discoverability changes.
- Generated docs boundaries are respected.
- Examples are current, executable where practical, and tied to real files or
  commands.
- The relevant docs build or validation command passed, or the residual blocker
  is stated.
