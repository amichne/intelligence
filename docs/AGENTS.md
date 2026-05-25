# Docs Agent Guide

The `docs` tree is the hand-authored source for the Zensical site.
`zensical.toml` owns navigation, Markdown features, and theme configuration.

## Site Structure

The site is organized around the reader's job instead of the repository tree.

- `docs/index.md` introduces the repository, the operating model, and the
  fastest useful commands.
- `docs/getting-started/` covers how to use the workflow profile, marketplace,
  and primitive scaffolding paths.
- `docs/available/` explains the plugin families and primitive types that are
  currently curated here.
- `docs/how-it-works/` documents source graph, validation, and runtime
  activation contracts.
- `docs/reference/` maps repository paths, commands, generated evidence, docs
  tooling, and terminology.

## Ownership

Keep these docs aligned with repository contracts.

- Treat `zensical.toml` as the source of truth for navigation.
- Add pages and nav entries in the same change.
- Link concepts back to concrete files, commands, schemas, or generated
  reports.
- Do not hand-edit generated evidence under `garden/docs/`; update the owning
  generator and re-run the relevant check.
- Keep `README.md` consistent when public commands, marketplace behavior, or
  validation gates change.

## Authoring Conventions

Use the Zensical and Material-style features already enabled in
`zensical.toml`.

- Use admonitions for safety boundaries, generated-file warnings, and approval
  caveats.
- Use tables for lookup surfaces such as plugin families, commands, and file
  ownership.
- Use Mermaid for source graph or runtime activation flows.
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
