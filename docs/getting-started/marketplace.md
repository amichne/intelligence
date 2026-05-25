# Marketplace

The marketplace is the curated distribution surface for project-agnostic
plugin families. The source of truth is `marketplace.json` on `main`; provider
payloads are generated into the orphan `marketplace` branch.

## Local Preview

Preview the hydrated marketplace before publishing.

```sh
npm ci
python3 scripts/publish-marketplace.py materialize --out /tmp/intelligence-marketplace
node scripts/validate-manifests.mjs --portable --hydrated /tmp/intelligence-marketplace
python3 scripts/publish-marketplace.py publish-branch --branch marketplace --no-push
```

The materialized output contains provider-native entrypoints. Codex consumers
use `codex/marketplace.json`; GitHub Copilot consumers use
`.github/plugin/marketplace.json`.

## Published Shape

The generated branch publishes only the plugin families listed in
`marketplace.json`. It does not make every local primitive public.

| Surface | Owner | Purpose |
|---|---|---|
| `marketplace.json` | Hand-authored source on `main` | Curated provider-neutral catalog. |
| `scripts/publish-marketplace.py` | Generator | Hydrates provider-native payloads. |
| `codex/marketplace.json` | Generated branch output | Codex marketplace entrypoint. |
| `.github/plugin/marketplace.json` | Generated branch output | GitHub Copilot marketplace entrypoint. |

## Publication Gate

Merges to `main` run `.github/workflows/publish-marketplace.yml`. The workflow
validates source contracts, materializes the marketplace, validates the
hydrated output, and force-updates the generated branch.

Run the same checks locally when changing marketplace exposure.
