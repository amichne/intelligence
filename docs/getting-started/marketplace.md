# Marketplace

The marketplace is the curated distribution surface for project-agnostic
plugin families. The source of truth is `adaptable.marketplace.json` on `main`;
provider payloads are generated into the orphan `codex` and `github` branches.
`main` keeps referential plugin manifests, primitive source files, and the
adapted marketplace manifests for providers that expect them in-place. It also
keeps the fully materialized GitHub payloads under `.github/plugin/plugins/` so
GitHub can resolve them without touching the referential root `plugins/` tree.

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
python3 scripts/publish-marketplace.py sync-main-marketplaces --check
```

The materialized output is a Codex marketplace root. Codex reads
`.agents/plugins/marketplace.json` and resolves fully hydrated plugin payloads
from root-level `plugins/<name>` directories.

The GitHub marketplace branch publishes its runtime entrypoint at
`.github/plugin/marketplace.json` with plugin payloads under
`.github/plugin/plugins/<name>`. The checked-in main copy uses the same
`.github/plugin/plugins/<name>` source paths.

## Published Shape

The generated branches publish only the plugin families listed in
`adaptable.marketplace.json`. It does not make every local primitive public.

| Surface | Owner | Purpose |
|---|---|---|
| `adaptable.marketplace.json` | Hand-authored source on `main` | Curated provider-neutral catalog. |
| `scripts/publish-marketplace.py` | Generator | Hydrates provider marketplace payloads. |
| `.agents/plugins/marketplace.json` | Generated on `main` and `codex` | Codex marketplace entrypoint. |
| `plugins/<name>/.codex-plugin/plugin.json` | Generated `codex` output | Codex plugin manifest and embedded payload root. |
| `.github/plugin/marketplace.json` | Generated on `main` and `github` | GitHub marketplace entrypoint. |
| `.github/plugin/plugins/<name>` | Generated on `main` and `github` | GitHub plugin payload root. |

## Publication Gate

Merges to `main` run `.github/workflows/publish-marketplace.yml`. The workflow
validates source contracts, materializes the marketplace, validates the
hydrated output, force-updates the generated branches, then writes the adapted
marketplace manifests and GitHub payload tree back to `main`.

`.github/workflows/sync-provider-marketplaces.yml` checks pull requests and
pushing by materializing the same provider payloads, validating them, and
checking the adapted marketplace manifests and GitHub payload tree on `main`
for drift.

Run the same checks locally when changing marketplace exposure.
