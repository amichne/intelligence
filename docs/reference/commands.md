# Commands

This catalog describes the portable marketplace operator surface by job. The
terminal UI is the primary human workflow; direct commands are the scriptable
forms behind the same marketplace and validation semantics. Use
`intelligence --help` for the top-level command list, and add `--help` after any
command group or command for focused usage.

## Terminal UI

Open the full-screen browser when you are deciding what to install, update, pin,
or validate in a repository.

| Operation | Command Or Key | Use When |
|---|---|---|
| Open marketplace browser | `intelligence` | You are in an interactive terminal and want the primary marketplace workflow. |
| Open browser explicitly | `intelligence marketplace ui` | A script, alias, or docs snippet should name the UI command. |
| Select target repository | `intelligence marketplace ui --repo /path/to/repo` | You want the TUI to write install intent somewhere other than the current directory. |
| Resolve imports from a ref | `intelligence marketplace ui --ref main` | Direct imports selected in the UI should use a branch, tag, or SHA. |
| Search loaded resources | `/` | You want to filter the current marketplace catalog, locally installed plugins, and guided flow rows. |
| Change search type | `Tab` while searching | You want to search by all fields, repository, user, plugin, primitive, or installed state. |
| Move panes | `Tab` / `Shift-Tab` | You want to move focus through context, resources, details, and actions panes. |
| Show shortcuts | `?` | You want the keyboard reference without leaving the TUI. |
| Stage selected action | `i` | You want to inspect an install, import, or update before confirming it. |
| Stage install all | `a` | You want to stage installation of every exposed plugin from the loaded marketplace. |
| Confirm pending action | `y` / `Enter` | You want to run the pending install, import, update, pin, unpin, or validation operation. |
| Cancel pending action | `Esc` / `n` | You want to leave the pending operation untouched. |
| Preview repository search | `r` | You want to preview the repository in the search box using the configured default Git host. |
| Validate target | `v` | You want portable validation for the selected install target. |
| Open command palette | `:` | You want to browse another repository, set host/target/search scope, import, install, update, pin, list remotes, validate, inspect author/edit guidance, inspect output guidance, or quit. |

The bottom mode bar shows the active mode, status or prompt, ranked command
suggestions, search match counts, confirmation provenance, and contextual keys.
Repository-scoped search names the repository that `Enter` will preview.
Single selected actions confirm inline; batch actions keep the modal with raw
RPC method and params for inspection.
The Context, Resources, Details, and Actions panes keep the source, operation,
and target split visible while browsing.

The command palette accepts these operation names.
Suggestions are context-ranked by the current selection, staged state, and
installed/update state, then grouped by flow: discover, add, installed, author,
edit, and outputs. Unavailable commands remain visible but dimmed so the
available command surface does not disappear while browsing.

| Palette Command | Result |
|---|---|
| `browse amichne/slopsentral` | Preview another repository marketplace. |
| `preview amichne/slopsentral` | Preview a repository using the host-aware repository rules. |
| `host github.enterprise.example` | Set the default host for `owner/repo` repository previews. |
| `target /path/to/repo` | Select where install intent and validation should be applied. |
| `scope plugin` | Switch the active search scope. |
| `search installed kotlin` | Set both search scope and query from the palette. |
| `stage` / `stage all` | Stage the selected action or full marketplace install. |
| `run staged` / `clear staged` | Open the selected staged confirmation or clear staged actions. |
| `import` or `install` | Import the selected offering. |
| `install all` | Install every exposed plugin from the loaded marketplace. |
| `update` / `update all` | Update selected or all imported plugins. |
| `pin 1.2.3` / `unpin` | Manage the selected installed plugin pin. |
| `remote list` | Inspect configured external marketplaces. |
| `validate` | Run portable validation for the target repository. |
| `author` / `create skill` | Show where reusable resources should be authored. |
| `edit` / `open source` | Show where existing resources should be edited. |
| `outputs` / `materialize` / `publish` / `validate hydrated` | Show provider-output generation guidance. |

## Discover

Use direct discovery when you need printable or machine-readable output instead
of the full-screen TUI.

| Operation | Command | Use When |
|---|---|---|
| Browse published offerings | `intelligence marketplace browse amichne/slopsentral` | You want the default marketplace view from a repository reference. |
| Browse local source | `intelligence marketplace browse /path/to/slopsentral --provider source` | You are developing the marketplace repo and want the authored source catalog. |
| Browse machine output | `intelligence marketplace browse amichne/slopsentral --format json` | You want a script-readable offering catalog. |

## Validate

Validate before trusting source changes or generated provider output.

| Operation | Command | Use When |
|---|---|---|
| Validate adaptable marketplace | `intelligence validate` | You changed authored source, installed marketplace state, hooks, schemas, profiles, or marketplace entries. |
| Validate portable marketplace | `intelligence validate --portable` | You want checks that avoid host-local assumptions. |
| Validate hydrated output | `intelligence validate --portable --hydrated /tmp/intelligence-marketplace` | You materialized provider output and want to check the generated shape. |

## Manage

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
`INTELLIGENCE_MARKETPLACE_ASSET_ROOT`.

| Operation | Command | Use When |
|---|---|---|
| Import direct reference | `intelligence marketplace import amichne/slopsentral/kotlin-engineering` | You want a portable plugin entry from a remote marketplace without cloning it. |
| Import pinned ref | `intelligence marketplace import amichne/slopsentral/kotlin-engineering --ref main` | You want to resolve from a specific branch, tag, or SHA. |
| Install whole marketplace | `intelligence marketplace install amichne/slopsentral` | You want every plugin exposed by an adaptable marketplace repository. |
| Install pinned marketplace | `intelligence marketplace install amichne/slopsentral --ref main` | You want the whole marketplace resolved from a specific branch, tag, or SHA. |
| Import named alias | `intelligence marketplace import shared-tools/review-stack` | You want a portable plugin entry resolved through managed marketplace metadata. |
| Import into another repo | `intelligence marketplace import amichne/slopsentral/kotlin-engineering --repo /path/to/repo` | You are managing a marketplace repo other than the current directory. |

## Project

Materialize only when authoring or publishing provider marketplace payloads. The
output directory is replaced. Imported `MARKETPLACE_SOURCE` plugins are read
from the lock-backed global asset cache first, so materialization does not need
the original remote source checkout when the assets were already resolved.

| Operation | Command | Use When |
|---|---|---|
| Render Codex output | `intelligence marketplace materialize --repo /path/to/slopsentral --provider codex --out /tmp/slopsentral-codex` | You need the Codex marketplace payload. |
| Render GitHub output | `intelligence marketplace materialize --repo /path/to/slopsentral --provider github --out /tmp/slopsentral-github` | You need the GitHub Copilot marketplace payload. |
| Render every provider | `intelligence marketplace materialize --repo /path/to/slopsentral --provider all --out /tmp/slopsentral-marketplace` | You changed provider-neutral exposure or projection logic. |

## Publish

The default publish command writes harness payloads into a marketplace repository
root. Provider flags publish generated orphan branches from source. Use
`--no-push` with provider flags for a local proof without updating remotes.

| Operation | Command | Use When |
|---|---|---|
| Publish default harness payloads | `intelligence marketplace publish --repo /path/to/slopsentral` | A local maintainer needs `.agents/plugins/marketplace.json` and `.github/plugin/marketplace.json` refreshed in the marketplace repo. |
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

The Ratatui TUI uses this boundary instead of reimplementing marketplace
normalization or referential resolution.

## Build

Build commands are for repository development and release work, not normal
marketplace browsing.

| Operation | Command | Use When |
|---|---|---|
| Run CLI tests and install dev binary | `./gradlew :cli:test installDevelopmentCli` | You changed Kotlin CLI code. |
| Run TUI tests | `cargo test --manifest-path tui/Cargo.toml` | You changed the Ratatui browser. |
| Build native executable | `./gradlew :cli:nativeCompile` | You need the self-contained GraalVM binary. |
| Install released CLI | `brew install amichne/intelligence/intelligence` | You want the stable installed `intelligence` command. |

## Help

Every command group supports focused help.

```sh
intelligence --help
intelligence marketplace --help
intelligence marketplace browse --help
intelligence marketplace import --help
intelligence marketplace install --help
intelligence marketplace ui --help
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
