# Marketplace

The marketplace is the curated distribution surface for project-agnostic plugin
families. The source of truth is `source/adaptable.marketplace.json` on `main`;
default provider payloads are generated on `main` by CI, and provider-specific
orphan branches remain available for legacy marketplace consumers.

`source/` keeps the authored reference graph. Provider-native payloads are
produced by the Kotlin CLI in explicit output directories, CI-owned default
harness paths, or published branches.

## Browse First

After installing the CLI, browse a repository marketplace by giving the CLI the
repository reference. The command discovers supported marketplace entrypoints and
shows plugins separately from standalone primitives.

```sh
intelligence marketplace browse amichne/intelligence
intelligence marketplace browse amichne/intelligence --format json
```

Standalone primitives are shown only when they are exposed directly by the
marketplace. Primitives that only exist inside a plugin payload remain bundled
under that plugin.

## Projection Preview

Preview provider payloads locally when changing marketplace projection logic.

```sh
intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace
intelligence validate --portable --hydrated /tmp/intelligence-codex-marketplace
intelligence marketplace publish --codex --no-push
intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace
intelligence validate --portable --hydrated /tmp/intelligence-github-marketplace
intelligence marketplace publish --github --no-push
```

Use plugin names from [Plugin families](../available/plugin-families.md) as
needed.

The GitHub Copilot marketplace publishes its runtime entrypoint at
`.github/plugin/marketplace.json` with plugin payloads under
`.github/plugin/plugins/<name>`. Each plugin `source` is the plugin directory
name resolved under `metadata.pluginRoot`.

## Published Shape

| Surface | Owner | Purpose |
|---|---|---|
| `source/adaptable.marketplace.json` | Hand-authored source on `main` | Curated provider-neutral catalog. |
| `source/plugins/*/plugin.json` | Hand-authored source on `main` | Plugin composition over source primitives. |
| `cli/` | Kotlin CLI source | Hydrates provider marketplace payloads. |
| `.agents/plugins/marketplace.json` | CI-generated on `main` and generated on `codex` | Codex marketplace entrypoint. |
| `.agents/plugins/plugins/<name>/.codex-plugin/plugin.json` | CI-generated on `main` and generated on `codex` | Codex plugin manifest and embedded payload root. |
| `.github/plugin/marketplace.json` | CI-generated on `main` and generated on `github` | GitHub Copilot marketplace entrypoint. |
| `.github/plugin/plugins/<name>` | CI-generated on `main` and generated on `github` | GitHub Copilot plugin payload root. |

## Publication Gate

Merges to `main` run `.github/workflows/sync-provider-marketplaces.yml`. The
workflow validates source contracts, materializes the marketplace, validates the
hydrated output, and commits default harness payloads back to `main` when they
change.

`.github/workflows/sync-provider-marketplaces.yml` checks pull requests by
materializing the same provider payloads and validating them without committing
generated output from the pull request.

Run the same checks locally when changing marketplace exposure.
