# Commands

This catalog describes the portable marketplace operator commands by job. Use
`intelligence --help` for the top-level command list, and add `--help` after any
command group or command for focused usage.

## Discover

Browse first when you want to see what a repository marketplace offers without
knowing provider branches, entrypoints, plugin paths, or primitive paths.

| Operation | Command | Use When |
|---|---|---|
| Browse published offerings | `intelligence marketplace browse amichne/intelligence` | You want the default marketplace view from a repository reference. |
| Browse local source | `intelligence marketplace browse . --provider source` | You are developing this repository and want the authored source catalog. |
| Browse machine output | `intelligence marketplace browse amichne/intelligence --format json` | You want a script-readable offering catalog. |

## Validate

Validate before trusting source graph changes or generated provider output.

| Operation | Command | Use When |
|---|---|---|
| Validate source | `intelligence validate` | You changed source manifests, hooks, schemas, profiles, or marketplace entries. |
| Validate portable source | `intelligence validate --portable` | You want checks that avoid host-local assumptions. |
| Validate hydrated output | `intelligence validate --portable --hydrated /tmp/intelligence-marketplace` | You materialized provider output and want to check the generated shape. |

## Manage

Manage named external marketplaces in repository-local, source-controlled
metadata. These names are the only marketplace names imports may reference.

| Operation | Command | Use When |
|---|---|---|
| Add external marketplace | `intelligence marketplace remote add shared-tools acme/shared-tools --ref v1.2.0` | You want this repo to import plugins from another marketplace. |
| List external marketplaces | `intelligence marketplace remote list` | You want to inspect the repo-local marketplace registry. |
| Remove external marketplace | `intelligence marketplace remote remove shared-tools` | You no longer want imports to resolve from that marketplace. |

## Import

Import by reference. The CLI writes `MARKETPLACE_SOURCE` entries and lock
evidence; it does not vendor provider payloads or mutate local harness config.

| Operation | Command | Use When |
|---|---|---|
| Import plugin reference | `intelligence marketplace import shared-tools/review-stack --version 1.2.0` | You want a portable plugin entry resolved through a managed marketplace. |
| Import into another repo | `intelligence marketplace import shared-tools/review-stack --repo /path/to/repo --version 1.2.0` | You are managing a marketplace repo other than the current directory. |

## Project

Materialize only when authoring or publishing provider marketplace payloads. The
output directory is replaced.

| Operation | Command | Use When |
|---|---|---|
| Render Codex output | `intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace` | You need the Codex marketplace payload. |
| Render GitHub output | `intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace` | You need the GitHub Copilot marketplace payload. |
| Render every provider | `intelligence marketplace materialize --provider all --out /tmp/intelligence-marketplace` | You changed provider-neutral exposure or projection logic. |

## Publish

The default publish command writes CI-owned harness payloads into the repository
root. Provider flags publish generated orphan branches from source. Use
`--no-push` with provider flags for a local proof without updating remotes.

| Operation | Command | Use When |
|---|---|---|
| Publish default harness payloads | `intelligence marketplace publish` | CI or a local maintainer needs `.agents/plugins/marketplace.json` and `.github/plugin/marketplace.json` refreshed on `main`. |
| Preview Codex branch | `intelligence marketplace publish --codex --no-push` | You want to inspect the generated Codex branch locally. |
| Preview GitHub Copilot branch | `intelligence marketplace publish --github --no-push` | You want to inspect the generated GitHub branch locally. |

## Build

Build commands are for repository development and release work, not normal
marketplace browsing.

| Operation | Command | Use When |
|---|---|---|
| Run CLI tests and install dev binary | `./gradlew :cli:test installDevelopmentCli` | You changed Kotlin CLI code. |
| Build native executable | `./gradlew :cli:nativeCompile` | You need the self-contained GraalVM binary. |
| Install released CLI | `brew install amichne/intelligence/intelligence` | You want the stable installed `intelligence` command. |

## Help

Every command group supports focused help.

```sh
intelligence --help
intelligence marketplace --help
intelligence marketplace browse --help
intelligence validate --help
```

The help text is the shortest way to confirm available options before running a
command.

## Documentation

Build the Zensical site from the repository root.

```sh
zensical build --clean
```
