# Marketplace

The marketplace is the curated distribution surface for project-agnostic
plugin families. The source of truth is `marketplace.json` on `main`; provider
payloads are generated into the orphan `marketplace/codex` branch. `main` keeps
only referential plugin manifests and primitive source files.

## Local Preview

Preview the hydrated marketplace before publishing.

```sh
npm ci
python3 scripts/publish-marketplace.py materialize --provider codex --out /tmp/intelligence-codex-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-codex-marketplace
python3 scripts/publish-marketplace.py publish-branch --provider codex --branch marketplace/codex --no-push
```

The materialized output is a Codex marketplace root. Codex reads
`.agents/plugins/marketplace.json` and resolves fully hydrated plugin payloads
from root-level `plugins/<name>` directories.

## Published Shape

The generated branch publishes only the plugin families listed in
`marketplace.json`. It does not make every local primitive public.

| Surface | Owner | Purpose |
|---|---|---|
| `marketplace.json` | Hand-authored source on `main` | Curated provider-neutral catalog. |
| `scripts/publish-marketplace.py` | Generator | Hydrates the Codex marketplace payload. |
| `.agents/plugins/marketplace.json` | Generated `marketplace/codex` output | Codex marketplace entrypoint. |
| `plugins/<name>/.codex-plugin/plugin.json` | Generated `marketplace/codex` output | Codex plugin manifest and embedded payload root. |

## Publication Gate

Merges to `main` run `.github/workflows/publish-marketplace.yml`. The workflow
validates source contracts, materializes the marketplace, validates the
hydrated output, and force-updates the generated branch.

`.github/workflows/sync-provider-marketplaces.yml` checks pull requests and
pushes by materializing the same Codex payload and validating it without
checking generated payloads into `main`.

Run the same checks locally when changing marketplace exposure.
