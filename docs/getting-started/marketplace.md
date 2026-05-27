# Marketplace

The marketplace is the curated distribution surface for project-agnostic
plugin families. The source of truth is `adaptable.marketplace.json` on `main`;
provider payloads are generated into the orphan `codex` and `github` branches.
`main` keeps only referential plugin manifests and primitive source files.

## Local Preview

Preview the hydrated marketplace before publishing.

```sh
npm ci
python3 scripts/publish-marketplace.py materialize --provider codex --out /tmp/intelligence-codex-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-codex-marketplace
python3 scripts/publish-marketplace.py publish-branch --provider codex --branch codex --no-push
python3 scripts/publish-marketplace.py materialize --provider github --out /tmp/intelligence-github-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-github-marketplace
python3 scripts/publish-marketplace.py publish-branch --provider github --branch github --no-push
```

The materialized output is a Codex marketplace root. Codex reads
`.agents/plugins/marketplace.json` and resolves fully hydrated plugin payloads
from root-level `plugins/<name>` directories.

The GitHub marketplace branch publishes its runtime entrypoint at
`.github/plugin/marketplace.json` with plugin payloads under
`.github/plugin/plugins/<name>`.

## Published Shape

The generated branch publishes only the plugin families listed in
`adaptable.marketplace.json`. It does not make every local primitive public.

| Surface | Owner | Purpose |
|---|---|---|
| `adaptable.marketplace.json` | Hand-authored source on `main` | Curated provider-neutral catalog. |
| `scripts/publish-marketplace.py` | Generator | Hydrates provider marketplace payloads. |
| `.agents/plugins/marketplace.json` | Generated `codex` output | Codex marketplace entrypoint. |
| `plugins/<name>/.codex-plugin/plugin.json` | Generated `codex` output | Codex plugin manifest and embedded payload root. |
| `.github/plugin/marketplace.json` | Generated `github` output | GitHub marketplace entrypoint. |
| `.github/plugin/plugins/<name>` | Generated `github` output | GitHub plugin payload root. |

## Publication Gate

Merges to `main` run `.github/workflows/publish-marketplace.yml`. The workflow
validates source contracts, materializes the marketplace, validates the
hydrated output, and force-updates the generated branches.

`.github/workflows/sync-provider-marketplaces.yml` checks pull requests and
pushing by materializing the same provider payloads and validating them without
checking generated payloads into `main`.

Run the same checks locally when changing marketplace exposure.
