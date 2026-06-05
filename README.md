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

- `source/adaptable.marketplace.json` is the curated provider-neutral marketplace catalog.
- `source/agents/` contains independent reusable agent profiles.
- `source/skills/` contains independent reusable skills.
- `source/concepts/` contains portable instruction and principle documents.
- `source/hooks/` contains hook metadata, implementations, requirements, and provider
  adapters.
- `source/plugins/` contains referential plugin composition manifests.
- `source/profiles/` contains workflow profiles for target repositories.
- `source/templates/` contains primitive scaffold templates used by `bin/intelligence`.
- `source/schemas/` contains public provider-neutral and adapter schema contracts.
- `apm.yml` is the checked APM marketplace manifest generated from
  `source/adaptable.marketplace.json`.
- `scripts/` contains root validation, packaging, APM staging, and marketplace
  publication tooling.
- `build/apm-marketplace/` is the ignored APM workspace generated for local
  preview and release artifact creation.
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

`source/adaptable.marketplace.json` keeps the provider-neutral source catalog,
and `apm.yml` is the APM marketplace manifest generated from that catalog. The
hydrated APM package workspace is generated under ignored `build/` output, not
checked into `main`.

Preview the APM marketplace locally:

```sh
npm ci
node scripts/validate-manifests.mjs --portable
python3 scripts/prepare-apm-marketplace.py manifest --check
python3 scripts/prepare-apm-marketplace.py stage --out build/apm-marketplace --check-root-manifest
cd build/apm-marketplace
apm pack --marketplace=all --dry-run --check-versions --json
apm audit --ci --no-policy
```

Pull requests run `.github/workflows/sync-provider-marketplaces.yml`, which
validates source contracts, prepares the APM workspace, and runs the APM pack
and audit gates. Tags and manual publish dispatches run
`.github/workflows/publish-marketplace.yml`, which uploads the generated APM
marketplace root, marketplace JSON files, pack report, and checksums as release
artifacts. No generated provider branches or hydrated payload trees are pushed
back to `main`.

## CLI Archives

Build local distribution archives:

```sh
npm run package:cli -- --version local
```

The packager writes `dist/intelligence-<version>.tar.gz`,
`dist/intelligence-<version>.zip`, and `dist/SHA256SUMS`.
