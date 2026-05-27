# Intelligence

Intelligence is a public source repository and marketplace for reusable AI
tooling primitives: skills, agent profiles, hooks, concepts, schemas, workflow
profiles, and referential plugin families.

The public boundary is intentionally narrow. This repository publishes
generally useful, project-agnostic primitives. Private cleanup and migration
material lives outside this public repository.

## Documentation

The Zensical documentation site lives under `docs/`.

```sh
zensical build --clean
```

If Zensical is not installed:

```sh
python3 -m venv .venv-docs
. .venv-docs/bin/activate
python -m pip install -r requirements-docs.txt
zensical build --clean
```

## Repository Shape

- `adaptable.marketplace.json` is the curated provider-neutral marketplace catalog.
- `agents/` contains independent reusable agent profiles.
- `skills/` contains independent reusable skills.
- `concepts/` contains portable instruction and principle documents.
- `hooks/` contains hook metadata, implementations, requirements, and provider
  adapters.
- `plugins/` contains referential plugin composition manifests.
- `profiles/` contains workflow profiles for target repositories.
- `templates/` contains primitive scaffold templates used by `bin/intelligence`.
- `schemas/` contains public provider-neutral and adapter schema contracts.
- `scripts/` contains root validation, packaging, and marketplace publication
  tooling.
- `docs/` contains the public documentation site source.

## Validation

Install pinned validation dependencies and run the manifest gate:

```sh
npm ci
node scripts/validate-manifests.mjs
```

Use the CLI wrapper for ordinary repository work:

```sh
bin/intelligence validate
```

Build the docs after documentation or navigation changes:

```sh
zensical build --clean
```

## Marketplace Publication

`adaptable.marketplace.json` keeps the provider-neutral source catalog. The
generated `codex` and `github` branches are materialized from that source.
`main` also keeps the adapted marketplace manifests at
`.agents/plugins/marketplace.json` and `.github/plugin/marketplace.json`; the
hydrated plugin payloads stay on the provider branches.

Preview the branch output locally:

```sh
npm ci
python3 scripts/publish-marketplace.py materialize --provider codex --out /tmp/intelligence-codex-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-codex-marketplace
python3 scripts/publish-marketplace.py publish-branch --provider codex --branch codex --no-push
python3 scripts/publish-marketplace.py materialize --provider github --out /tmp/intelligence-github-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-github-marketplace
python3 scripts/publish-marketplace.py publish-branch --provider github --branch github --no-push
python3 scripts/publish-marketplace.py sync-main-marketplaces --check
```

Merges to `main` run `.github/workflows/publish-marketplace.yml`, which
validates source contracts, materializes the Codex and GitHub marketplace
roots, force-updates `codex` and `github`, then writes the adapted marketplace
manifests back to `main` if they changed.

## CLI Archives

Build local distribution archives:

```sh
npm run package:cli -- --version local
```

The packager writes `dist/intelligence-<version>.tar.gz`,
`dist/intelligence-<version>.zip`, and `dist/SHA256SUMS`.
