# Marketplace

The marketplace is the curated distribution surface for project-agnostic plugin
families. The source of truth remains the `source/` graph:
`source/adaptable.marketplace.json` selects the public plugin families, and
`source/plugins/*/plugin.json` defines each referential composition.

`apm.yml` is the checked APM marketplace manifest generated from that catalog.
Hydrated package payloads are prepared only in ignored build output.

## Local Preview

Preview the APM marketplace before publishing.

```sh
npm ci
node scripts/validate-manifests.mjs --portable
python3 scripts/prepare-apm-marketplace.py manifest --check
python3 scripts/prepare-apm-marketplace.py stage --out build/apm-marketplace --check-root-manifest
cd build/apm-marketplace
apm pack --marketplace=all --dry-run --check-versions --json
apm audit --ci --no-policy
```

The staging command writes `build/apm-marketplace/` with a root `apm.yml`,
`packages/<name>/apm.yml`, and APM-native `.apm/` primitive layouts. That
directory is generated from source and ignored by Git.

## Published Shape

Release artifacts contain the generated APM marketplace root and the marketplace
indexes produced by `apm pack`.

| Surface | Owner | Purpose |
|---|---|---|
| `source/adaptable.marketplace.json` | Hand-authored source on `main` | Curated provider-neutral catalog. |
| `source/plugins/*/plugin.json` | Hand-authored source on `main` | Referential plugin composition. |
| `apm.yml` | Checked generated contract | APM marketplace manifest aligned with the source catalog. |
| `scripts/prepare-apm-marketplace.py` | Generator | Builds the ignored APM workspace from source. |
| `build/apm-marketplace/` | Ignored generated output | Local preview and release artifact root. |
| `marketplace.json` | APM release artifact | Claude-format marketplace index. |
| `codex-marketplace.json` | APM release artifact | Codex-format marketplace index. |

## Publication Gate

`.github/workflows/sync-provider-marketplaces.yml` checks pull requests and
pushes to `main` by validating source contracts, checking `apm.yml` freshness,
staging the APM workspace, and running APM pack/audit gates.

`.github/workflows/publish-marketplace.yml` runs on tags and manual dispatches.
It prepares the same APM workspace, writes marketplace artifacts, archives the
workspace, records checksums, uploads the artifact bundle, and publishes GitHub
Release assets when requested.

No generated marketplace payloads are committed to `main`, and no provider
branches are force-updated.
