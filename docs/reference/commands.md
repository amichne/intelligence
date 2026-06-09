# Commands

This catalog describes the CLI operations by job. Use `intelligence --help` for
the top-level command list, and add `--help` after any command group or command
for focused usage.

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

## Project

Materialize only when authoring or publishing provider marketplace payloads. The
output directory is replaced.

| Operation | Command | Use When |
|---|---|---|
| Render Codex output | `intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace` | You need the Codex marketplace payload. |
| Render GitHub output | `intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace` | You need the GitHub Copilot marketplace payload. |
| Render every provider | `intelligence marketplace materialize --provider all --out /tmp/intelligence-marketplace` | You changed provider-neutral exposure or projection logic. |

## Publish

Publish commands prepare generated orphan branches from source. Use `--no-push`
for a local proof without updating remotes.

| Operation | Command | Use When |
|---|---|---|
| Preview Codex branch | `intelligence marketplace publish-branch --provider codex --branch codex --no-push` | You want to inspect the generated Codex branch locally. |
| Preview GitHub branch | `intelligence marketplace publish-branch --provider github --branch github --no-push` | You want to inspect the generated GitHub branch locally. |

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
