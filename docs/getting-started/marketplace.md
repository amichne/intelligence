# Marketplace

The marketplace is the curated distribution surface for project-agnostic plugin
families. The source of truth is `source/adaptable.marketplace.json` on `main`;
provider payloads are generated into the orphan `codex` and `github` branches.

`main` keeps only the authored reference graph under `source/`. Provider-native
payloads are produced by the Kotlin CLI in explicit output directories or
published branches.

## Consumer Setup

Preview the provider payloads locally.

```sh
./gradlew installDevelopmentCli
.local/intelligence/bin/intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace
.local/intelligence/bin/intelligence validate --portable --hydrated /tmp/intelligence-codex-marketplace
.local/intelligence/bin/intelligence marketplace publish-branch --provider codex --branch codex --no-push
.local/intelligence/bin/intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace
.local/intelligence/bin/intelligence validate --portable --hydrated /tmp/intelligence-github-marketplace
.local/intelligence/bin/intelligence marketplace publish-branch --provider github --branch github --no-push
```

Use plugin names from [Plugin families](../available/plugin-families.md) as
needed.

The GitHub marketplace branch publishes its runtime entrypoint at
`.github/plugin/marketplace.json` with plugin payloads under
`.github/plugin/plugins/<name>`. Each plugin `source` is the plugin directory
name resolved under `metadata.pluginRoot`.

## Published Shape

| Surface | Owner | Purpose |
|---|---|---|
| `source/adaptable.marketplace.json` | Hand-authored source on `main` | Curated provider-neutral catalog. |
| `source/plugins/*/plugin.json` | Hand-authored source on `main` | Plugin composition over source primitives. |
| `cli/` | Kotlin CLI source | Hydrates provider marketplace payloads. |
| `.agents/plugins/marketplace.json` | Generated on `codex` | Codex marketplace entrypoint. |
| `plugins/<name>/.codex-plugin/plugin.json` | Generated on `codex` | Codex plugin manifest and embedded payload root. |
| `.github/plugin/marketplace.json` | Generated on `github` | GitHub marketplace entrypoint. |
| `.github/plugin/plugins/<name>` | Generated on `github` | GitHub plugin payload root. |

## Publication Gate

Merges to `main` run `.github/workflows/publish-marketplace.yml`. The workflow
validates source contracts, materializes the marketplace, validates the
hydrated output, and force-updates the generated branches.

`.github/workflows/sync-provider-marketplaces.yml` checks pull requests by
materializing the same provider payloads, validating them, and asserting
materialized marketplace payloads are absent from the source branch.

Run the same checks locally when changing marketplace exposure.
