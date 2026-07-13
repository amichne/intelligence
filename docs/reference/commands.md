# Commands

This catalog describes the portable marketplace operator surface by job. The
The CLI is the human and automation workflow. Use `intelligence --help` for the
top-level command list, and add `--help` after any command group or command for
focused usage.

## Doctor

Start with `doctor` when remote discovery depends on GitHub host configuration.
The command never prints tokens; JSON output reports whether `gh` is available,
which hosts are configured, which account is active, and which host owner/repo
shorthand will target.

| Operation | Command | Use When |
|---|---|---|
| Print packaged version | `intelligence --version` | You want to confirm which published or development build is on PATH. |
| Check CLI dependencies | `intelligence doctor` | You want human-readable repository and GitHub host state. |
| Check machine-readable dependencies | `intelligence doctor --format json` | Another tool needs stable host/auth metadata. |

## Setup

Use `setup` for the first write in a new consumer repository. It imports the
default `kotlin-engineering` plugin from `amichne/slopsentral`, creates
`.intelligence/adaptable.marketplace.json`, records exact resolved evidence in
`.intelligence/marketplace-lock.json`, and runs portable validation. It is a
convenience wrapper over the same import and lock semantics used by
`marketplace import`.

| Operation | Command | Use When |
|---|---|---|
| Set up the current repo | `intelligence setup` | You want the default locked Intelligence workflow in the current repository. |
| Set up another repo | `intelligence setup --repo /path/to/repo` | You are initializing a different repository. |
| Set up from another marketplace | `intelligence setup --marketplace acme/tools --plugin review-stack` | You want the same first-run flow with a different marketplace plugin. |
| Lock an exact plugin version | `intelligence setup --version 1.2.3` | You want the first import held to one exact plugin version. |
| Resolve from a source ref | `intelligence setup --ref main` | You want the initial import resolved from a branch, tag, or SHA. |

## Discover

Use direct discovery when you need printable or machine-readable output.
Owner/repo shorthand resolves through the active host reported by
`gh auth status --json hosts`; pass `--host` to target a specific GitHub or
GitHub Enterprise host.

| Operation | Command | Use When |
|---|---|---|
| Search GitHub repositories | `intelligence marketplace search kotlin` | You want candidate repositories through the active `gh` host. |
| Search an enterprise host | `intelligence marketplace search kotlin --host github.enterprise.example` | You want a configured GitHub Enterprise host. |
| Inspect a marketplace | `intelligence marketplace inspect amichne/slopsentral` | You want provider, entrypoint, plugin, primitive, and next-command guidance. |
| Search one marketplace catalog | `intelligence marketplace search kotlin --repository amichne/slopsentral` | You want offerings from a known marketplace repository. |
| Inspect one plugin | `intelligence marketplace inspect amichne/slopsentral --plugin kotlin-engineering` | You want one marketplace plugin entry. |
| Browse published offerings | `intelligence marketplace browse amichne/slopsentral` | You want the legacy marketplace view from a repository reference. |
| Browse local source | `intelligence marketplace browse /path/to/slopsentral --provider source` | You are developing the marketplace repo and want the authored source catalog. |
| Browse machine output | `intelligence marketplace browse amichne/slopsentral --format json` | You want a script-readable offering catalog. |

## Installed State

Inspect installed marketplace state before updating, pinning, or debugging lock
evidence.

| Operation | Command | Use When |
|---|---|---|
| List installed plugins | `intelligence marketplace installed list` | You want installed plugin, lock, source, and target state. |
| List installed plugins as JSON | `intelligence marketplace installed list --format json` | Another tool needs stable installed-state output. |
| Check installed versions | `intelligence marketplace versions kotlin-engineering` | You want installed and remote-current version evidence. |
| Check update state while listing | `intelligence marketplace installed list --check-updates` | You want remote current versions resolved when possible. |

## Validate

Validate before trusting source changes or generated provider output.

| Operation | Command | Use When |
|---|---|---|
| Validate adaptable marketplace | `intelligence validate` | You changed authored source, installed marketplace state, hooks, schemas, profiles, or marketplace entries. |
| Validate portable marketplace | `intelligence validate --portable` | You want checks that avoid host-local assumptions. |
| Validate hydrated output | `intelligence validate --portable --hydrated /tmp/intelligence-marketplace` | You materialized provider output and want to check the generated shape. |

## Advanced Aliases

Manage named external marketplaces in repository-local, source-controlled
metadata. Direct repository imports add this metadata automatically; these
commands are for explicit aliases and cleanup.

| Operation | Command | Use When |
|---|---|---|
| Add external marketplace | `intelligence marketplace remote add shared-tools acme/shared-tools` | You want this repo to use a stable alias for another marketplace. |
| List external marketplaces | `intelligence marketplace remote list` | You want to inspect the repo-local marketplace registry. |
| Remove external marketplace | `intelligence marketplace remote remove shared-tools` | You no longer want imports to resolve from that marketplace. |

## Import

Import by reference. The CLI writes `MARKETPLACE_SOURCE` entries into the
authored marketplace or `.intelligence/adaptable.marketplace.json`, then records
`.intelligence/marketplace-lock.json` evidence. It does not vendor provider
payloads or mutate local harness config. Direct repository imports and installs
default to `main` unless `--ref` is supplied. Resolved source assets are stored
in the global marketplace asset root, which defaults to
`~/.local/share/intelligence/marketplace-assets` and can be overridden with
`INTELLIGENCE_MARKETPLACE_ASSET_ROOT`. Human-facing mutation commands validate
the target repository after they write marketplace state; pass `--no-validate`
only when automation already runs validation separately.

| Operation | Command | Use When |
|---|---|---|
| Import direct reference | `intelligence marketplace import amichne/slopsentral/kotlin-engineering` | You want a portable plugin entry from a remote marketplace without cloning it. |
| Import pinned ref | `intelligence marketplace import amichne/slopsentral/kotlin-engineering --ref main` | You want to resolve from a specific branch, tag, or SHA. |
| Install whole marketplace | `intelligence marketplace install amichne/slopsentral` | You want every plugin exposed by an adaptable marketplace repository. |
| Install pinned marketplace | `intelligence marketplace install amichne/slopsentral --ref main` | You want the whole marketplace resolved from a specific branch, tag, or SHA. |
| Import named alias | `intelligence marketplace import shared-tools/review-stack` | You want a portable plugin entry resolved through managed marketplace metadata. |
| Import into another repo | `intelligence marketplace import amichne/slopsentral/kotlin-engineering --repo /path/to/repo` | You are managing a marketplace repo other than the current directory. |

## Maintain

Update and pin installed marketplace references directly.

| Operation | Command | Use When |
|---|---|---|
| Update one plugin | `intelligence marketplace update kotlin-engineering` | You want one imported plugin refreshed to the remote current version. |
| Update every plugin | `intelligence marketplace update --all` | You want all imported plugins checked and refreshed. |
| Pin a plugin | `intelligence marketplace pin kotlin-engineering 1.2.3` | You want a plugin held at one exact version. |
| Unpin a plugin | `intelligence marketplace unpin kotlin-engineering` | You want a plugin to follow the remote current version again. |

## Project

Materialize only when authoring or publishing provider marketplace payloads. The
output directory is replaced. Imported `MARKETPLACE_SOURCE` plugins are read
from the lock-backed global asset cache first, so materialization does not need
the original remote source checkout when the assets were already resolved. By
default this writes every provider projection to
`build/intelligence/marketplace` under the repository.

| Operation | Command | Use When |
|---|---|---|
| Render every provider | `intelligence marketplace materialize --repo /path/to/slopsentral` | You changed provider-neutral exposure or projection logic. |
| Render Codex output elsewhere | `intelligence marketplace materialize --repo /path/to/slopsentral --provider codex --out /tmp/slopsentral-codex` | You need a custom Codex-only output root. |
| Render GitHub output elsewhere | `intelligence marketplace materialize --repo /path/to/slopsentral --provider github --out /tmp/slopsentral-github` | You need a custom GitHub-only output root. |

## Publish

The default publish command writes harness payloads into a marketplace
repository root. Use `--check` to validate source and hydrated output before
publishing. Provider flags publish generated orphan branches from source; use
`--no-push` with provider flags for a local proof without updating remotes.

| Operation | Command | Use When |
|---|---|---|
| Publish after validation | `intelligence marketplace publish --repo /path/to/slopsentral --check` | A maintainer needs checked `.agents/plugins/marketplace.json` and `.github/plugin/marketplace.json` refreshed. |
| Preview Codex branch | `intelligence marketplace publish --repo /path/to/slopsentral --codex --no-push` | You want to inspect the generated Codex branch locally. |
| Preview GitHub Copilot branch | `intelligence marketplace publish --repo /path/to/slopsentral --github --no-push` | You want to inspect the generated GitHub branch locally. |

## Automate

Use `intelligence rpc` when another program needs the same marketplace and
validation semantics as the CLI. The contract is JSON-RPC 2.0 over stdio: send
one compact request object per line and read one compact response object per
line. The public schema lives at `schemas/rpc/marketplace.schema.json`.

```sh
printf '%s\n' '{"jsonrpc":"2.0","id":"browse","method":"marketplace.browse","params":{"repository":"amichne/slopsentral","provider":"auto"}}' \
  | intelligence rpc
```

## Build

Build commands are for repository development and release work, not normal
marketplace browsing.

| Operation | Command | Use When |
|---|---|---|
| Run CLI tests and install dev binary | `./gradlew :cli:test installDevelopmentCli` | You changed Kotlin CLI code. |
| Build JVM distribution | `./gradlew :cli:distTar` | You need the platform-neutral release archive. |
| Install released CLI | `brew install amichne/intelligence/intelligence` | You want the stable installed `intelligence` command. |

Use [Publication](publication.md) for the full release checklist, including tag
source, JVM asset verification, reproducibility checks, and Homebrew proof.

## Help

Every command group supports focused help.

```sh
intelligence --help
intelligence --version
intelligence doctor --help
intelligence setup --help
intelligence marketplace --help
intelligence marketplace search --help
intelligence marketplace inspect --help
intelligence marketplace browse --help
intelligence marketplace installed list --help
intelligence marketplace versions --help
intelligence marketplace import --help
intelligence marketplace install --help
intelligence marketplace remote --help
intelligence marketplace update --help
intelligence marketplace pin --help
intelligence marketplace unpin --help
intelligence rpc --help
intelligence validate --help
```

The help text is the shortest way to confirm available options before running a
command.

## Documentation

Build the Zensical site from the repository root.

```sh
zensical build --clean
```
