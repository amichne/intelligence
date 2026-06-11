# Marketplace

The marketplace is the portable distribution surface for project-agnostic plugin
families. Most users should operate it through the
[Terminal UI](tui.md), then drop to commands when they need a scriptable result,
CI step, provider projection, or publication flow.

## TUI First

Open the browser from the repository that should receive install intent.

```sh
intelligence
```

Inside the browser, use the command palette for marketplace operations.

| Goal | TUI Action |
|---|---|
| Preview a repository marketplace | `:browse amichne/slopsentral` |
| Search loaded offerings | `/` |
| Import the selected offering | `:import` |
| Install every exposed plugin | `:install all` |
| Update imported plugins | `:update` or `:update all` |
| Pin an installed plugin | `:pin 1.2.3` |
| Validate the target repository | `:validate` |

The TUI discovers supported marketplace entrypoints and shows plugins separately
from standalone primitives. It uses the same JSON-RPC boundary as the CLI
commands, so confirmed operations write the same install intent and lock
evidence.

## Browse Without The TUI

Use direct browsing when output needs to be copied, piped, or inspected outside
the full-screen interface.

```sh
intelligence marketplace browse amichne/slopsentral
intelligence marketplace browse amichne/slopsentral --format json
intelligence marketplace browse /path/to/slopsentral --provider source
```

## Referential Imports

Import plugins by reference instead of copying provider payloads. In the TUI,
`:import` imports the selected offering and `:install all` installs the loaded
marketplace. The command equivalents are useful for automation and docs.

The CLI writes a portable `MARKETPLACE_SOURCE` plugin entry into the existing
authored marketplace or `.intelligence/adaptable.marketplace.json`, then records
exact reconstruction evidence in `.intelligence/marketplace-lock.json`.

```sh
intelligence marketplace import amichne/slopsentral/kotlin-engineering
intelligence marketplace import amichne/slopsentral/kotlin-engineering --ref main
intelligence marketplace install amichne/slopsentral
```

These commands validate the target repository after they write marketplace
state. Use `--no-validate` only when a surrounding script runs validation
separately.

The CLI leaves Codex or GitHub Copilot installation as a provider-specific next
step.

## Advanced Aliases

Direct imports add external marketplace metadata automatically. Name external
marketplaces explicitly when a project wants stable local aliases.

```sh
intelligence marketplace remote add slopsentral amichne/slopsentral
intelligence marketplace remote list
intelligence marketplace import slopsentral/kotlin-engineering
```

## Projection Preview

Preview provider payloads locally when changing marketplace projection logic or
validating a marketplace repository.

```sh
intelligence marketplace materialize --repo /path/to/slopsentral
```

The default materializes all provider payloads into
`build/intelligence/marketplace`. Add `--provider` or `--out` only for a custom
proof target.

## Published Shape

| Surface | Owner | Purpose |
|---|---|---|
| `source/adaptable.marketplace.json` | Marketplace repo | Provider-neutral catalog. |
| `source/plugins/*/plugin.json` | Marketplace repo | Plugin composition over source primitives. |
| `.intelligence/adaptable.marketplace.json` | Consumer repo | Install-only marketplace intent. |
| `.intelligence/marketplace-lock.json` | Consumer repo | Resolved imported marketplace references and integrity evidence. |
| `schemas/` | This repo | Provider-neutral and adapter JSON Schema definitions. |
| `cli/` | This repo | Marketplace browsing, import, validation, materialization, and publication. |
| `tui/` | This repo | Ratatui marketplace browsing, search, install, update, and pin workflows. |

## Publication

Publish generated payloads from the marketplace repository.

```sh
intelligence marketplace publish --repo /path/to/slopsentral --check
intelligence marketplace publish --repo /path/to/slopsentral --codex --no-push
intelligence marketplace publish --repo /path/to/slopsentral --github --no-push
```
