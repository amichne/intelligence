# Documentation Site

This documentation uses Zensical with a TOML site contract at
`zensical.toml`. The site source lives under `docs/`, and the generated output
is written to `site/`.

## Local Build

Build the site from the repository root.

```sh
zensical build --clean
```

If Zensical is unavailable, install the local docs requirements.

```sh
python3 -m venv .venv-docs
. .venv-docs/bin/activate
python -m pip install -r requirements-docs.txt
zensical build --clean
```

## Site Contract

`zensical.toml` owns:

| Setting | Purpose |
|---|---|
| `docs_dir` | Source directory for authored docs. |
| `site_dir` | Generated output directory. |
| `nav` | Sidebar and page discovery. |
| `markdown_extensions` | Enabled Material-style Markdown features. |
| `theme` | Navigation behavior, code-copy controls, icons, and palettes. |

## Authoring Rules

Add a page and its navigation entry together. Prefer stable conceptual pages
over copying generated tables from `garden/docs/`.

When a docs page needs current generated state, point readers to the owning
manifest or generated report and include the regeneration command.

## Verification

Run the docs build after changing `docs/`, `zensical.toml`, or the docs
toolchain.

```sh
zensical build --clean
```
