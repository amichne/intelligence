# Marketplace

The marketplace is the portable distribution surface for project-agnostic plugin
families. The source of truth is `source/adaptable.marketplace.json`; public
contracts live under root `schemas/` so maintainers and consumers can inspect
content shape without digging through generated provider output.

`source/` keeps the authored reference graph. Provider-native payloads are
produced by the Kotlin CLI in explicit output directories, CI-owned default
harness paths, or published branches. Harness-specific semantics stay at the
projection edge; the authored marketplace remains provider-neutral.

## Browse First

After installing the CLI, browse a repository marketplace by giving the CLI the
repository reference. The command discovers supported marketplace entrypoints and
shows plugins separately from standalone primitives.

```sh
intelligence marketplace browse amichne/intelligence
intelligence marketplace browse amichne/intelligence --format json
intelligence marketplace ui
```

Standalone primitives are shown only when they are exposed directly by the
marketplace. Primitives that only exist inside a plugin payload remain bundled
under that plugin.

## Recorded Walkthrough

The marketplace flow has an asciinema walkthrough covering browse, JSON output,
direct git-backed import, validation, Codex and GitHub materialization, default
publish, and interactive UI import:

- [Published asciinema recording](https://asciinema.org/a/04spSj54iNerpDpc)
- [Repository asciicast](../assets/asciinema/marketplace-referential-import.cast)

## Referential Imports

Import plugins by reference instead of copying provider payloads. The direct
form resolves the remote marketplace from the repository reference, defaults to
`main` when `--ref` is omitted, writes a `MARKETPLACE_SOURCE` plugin entry, and
records exact reconstruction evidence in `.intelligence/marketplace-lock.json`.

```sh
intelligence marketplace import amichne/intelligence/kotlin-engineering
intelligence marketplace import amichne/intelligence/kotlin-engineering --ref v0.1.2
```

The CLI leaves Codex or GitHub Copilot installation as a provider-specific next
step.

## Manage Marketplaces

Direct imports add external marketplace metadata automatically. Name external
marketplace repositories explicitly when a project wants stable local aliases.
The names are repository-local and source-controlled so CI and other users
resolve the same marketplace graph.

```sh
intelligence marketplace remote add shared-tools acme/shared-tools
intelligence marketplace remote list
intelligence marketplace remote remove shared-tools
```

Managed external marketplaces are recorded as `externalMarketplaces` entries and
allowed through `management.allowExternalMarketplaces`.

Named aliases can still be imported directly:

```sh
intelligence marketplace import shared-tools/review-stack
```

When `--version` is omitted, the CLI imports the exact version declared by the
remote plugin reference or manifest. Floating version ranges remain rejected.

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
| `.intelligence/marketplace-lock.json` | CLI-written source-controlled state | Resolved imported marketplace references and integrity evidence. |
| `schemas/` | Hand-authored public contracts | Provider-neutral and adapter JSON Schema definitions. |
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
