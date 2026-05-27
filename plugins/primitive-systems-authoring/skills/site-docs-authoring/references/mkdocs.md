# MkDocs Sites

Use this reference when a repository owns docs through `mkdocs.yml` or
`mkdocs.yaml`.

## Discovery

- Read the MkDocs config before editing pages.
- Confirm `docs_dir`, `site_name`, `repo_url`, `edit_uri`, `theme`, `plugins`,
  `markdown_extensions`, and `nav`.
- Read local docs instructions before applying generic style.
- Check whether the repo uses a wrapper such as `make docs`, `npm run docs`, or
  a Python environment manager.

## Authoring Pattern

- Keep `nav` and page files in sync.
- Preserve the configured theme and extension set instead of assuming a default
  Material configuration.
- Keep Markdown portable unless an enabled extension clearly supports the
  feature.
- Use descriptive links and repository-relative paths where possible.
- When adding code examples, prefer commands that can be run from the repo root.

## Material-Style Features

- Content tabs are useful for CLI/API/language alternatives.
- Admonitions carry side constraints, warnings, and context.
- `pymdownx.superfences` commonly enables Mermaid and richer fenced blocks.
- Tables work best for options and compatibility data, not narrative prose.
- Cards and grids should route readers, not replace normal documentation.

## Validation

- Prefer `mkdocs build --strict` when available.
- If the project pins dependencies, use its lockfile or documented virtual
  environment.
- If strict mode is not configured, still run the repo's docs build and inspect
  warnings for broken links, missing nav entries, or unsupported Markdown
  features.
