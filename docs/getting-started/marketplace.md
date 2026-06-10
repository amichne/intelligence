# Marketplace

The marketplace is the portable distribution surface for project-agnostic plugin
families. This repository owns the CLI; `amichne/slopsentral` owns the reusable
personal marketplace content.

## Browse First

Browse a repository marketplace by reference. The command discovers supported
entrypoints and shows plugins separately from standalone primitives.

```sh
intelligence
intelligence marketplace browse amichne/slopsentral
intelligence marketplace browse amichne/slopsentral --format json
intelligence marketplace ui
```

## Referential Imports

Import plugins by reference instead of copying provider payloads. The CLI writes
a portable `MARKETPLACE_SOURCE` plugin entry into the existing authored
marketplace or `.intelligence/adaptable.marketplace.json`, then records exact
reconstruction evidence in `.intelligence/marketplace-lock.json`.

```sh
intelligence marketplace import amichne/slopsentral/kotlin-engineering
intelligence marketplace import amichne/slopsentral/kotlin-engineering --ref main
intelligence marketplace install amichne/slopsentral
```

The CLI leaves Codex or GitHub Copilot installation as a provider-specific next
step.

## Manage Marketplaces

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
intelligence marketplace materialize --repo /path/to/slopsentral --provider codex --out /tmp/slopsentral-codex
intelligence validate --repo /path/to/slopsentral --portable --hydrated /tmp/slopsentral-codex
intelligence marketplace materialize --repo /path/to/slopsentral --provider github --out /tmp/slopsentral-github
intelligence validate --repo /path/to/slopsentral --portable --hydrated /tmp/slopsentral-github
```

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
intelligence marketplace publish --repo /path/to/slopsentral
intelligence marketplace publish --repo /path/to/slopsentral --codex --no-push
intelligence marketplace publish --repo /path/to/slopsentral --github --no-push
```
