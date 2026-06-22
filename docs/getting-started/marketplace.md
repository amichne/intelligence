# Marketplace

The marketplace is the portable distribution surface for project-agnostic plugin
families. Most users should operate it through CLI discovery first, then open
the [Terminal UI](tui.md) only when they want a full-screen browser.

## CLI First

Check local dependencies and GitHub host configuration before exploring remote
repositories. Owner/repo shorthand uses the active host reported by
`gh auth status --json hosts`; pass `--host` to target a specific configured
GitHub or GitHub Enterprise host.

```sh
intelligence doctor
intelligence doctor --format json
intelligence marketplace search kotlin
intelligence marketplace search kotlin --host github.enterprise.example
```

Inspect a marketplace before importing from it. Use JSON when the output should
be parsed by a script or another agent.

```sh
intelligence marketplace inspect amichne/slopsentral
intelligence marketplace search kotlin --repository amichne/slopsentral
intelligence marketplace inspect amichne/slopsentral --format json
```

Useful read-only commands are:

| Goal | Command |
|---|---|
| Check host and auth state | `intelligence doctor` |
| Search repositories through `gh` | `intelligence marketplace search kotlin` |
| Inspect one marketplace | `intelligence marketplace inspect amichne/slopsentral` |
| Search one marketplace catalog | `intelligence marketplace search kotlin --repository amichne/slopsentral` |
| List installed plugins | `intelligence marketplace installed list` |
| Check versions for an import | `intelligence marketplace versions kotlin-engineering` |
| Browse legacy script output | `intelligence marketplace browse amichne/slopsentral --format json` |

## Setup

Use `setup` for a new consumer repository that wants the default locked
Intelligence workflow. The command imports `kotlin-engineering` from
`amichne/slopsentral`, writes `.intelligence/adaptable.marketplace.json`, records
the resolved plugin version and source integrity in
`.intelligence/marketplace-lock.json`, and runs portable validation.

```sh
intelligence setup
intelligence setup --repo /path/to/repo
```

Pass `--marketplace`, `--plugin`, `--version`, or `--ref` when the first import
should use a different source repository, plugin, exact plugin version, or Git
ref. Generated JSON uses the same canonical writer and validation path as
explicit marketplace imports.

## Referential Imports

Import plugins by reference instead of copying provider payloads. In the TUI,
`:import` imports the selected offering and `:install all` installs the loaded
marketplace. The command equivalents are useful for automation and docs.

The CLI writes a portable `MARKETPLACE_SOURCE` plugin entry into the existing
authored marketplace or `.intelligence/adaptable.marketplace.json`, then records
exact reconstruction evidence in `.intelligence/marketplace-lock.json`.

```sh
intelligence setup
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
