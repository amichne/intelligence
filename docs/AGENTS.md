# Docs Agent Guide

The `docs` tree is the hand-authored source for the Zensical site.
`zensical.toml` owns navigation, Markdown features, and theme configuration.

## Site Structure

The site is organized around the reader's job instead of the repository tree.

- `docs/index.md` introduces the CLI and points reusable marketplace content to
  `slopsentral`.
- `docs/getting-started/` covers the sole source-to-harness projection command.
- `docs/available/` points to the canonical `slopsentral` marketplace.
- `docs/how-it-works/` documents projection and its internal validation contracts.
- `docs/reference/` maps repository paths, commands, docs tooling, and
  terminology.

## Ownership

Keep these docs aligned with repository contracts.

- Treat `zensical.toml` as the source of truth for navigation.
- Add pages and nav entries in the same change.
- Link concepts back to concrete files, commands, or schemas.
- Keep `README.md` consistent when public commands, marketplace behavior, or
  validation gates change.

## Authoring Conventions

Use the Zensical and Material-style features already enabled in
`zensical.toml`.

- Use admonitions for safety boundaries and generated-file warnings.
- Use tables for lookup surfaces such as plugin families, commands, and file
  ownership.
- Use Mermaid for projection flows when it clarifies composition.
- Wrap prose near 80 characters unless tables or links require longer lines.
- Put at least one orienting paragraph after each major heading before lists or
  subheadings.

## Verification

Run the documentation build after changing navigation, config, Markdown
features, or linked page structure.

```sh
zensical build --clean
```

Install the local docs toolchain when the command is unavailable:

```sh
python3 -m venv .venv-docs
. .venv-docs/bin/activate
python -m pip install -r requirements-docs.txt
```
