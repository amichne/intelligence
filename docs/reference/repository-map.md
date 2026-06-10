# Repository Map

| Path | Purpose |
|---|---|
| `source/adaptable.marketplace.json` | Minimal CLI validation catalog that imports `slopsentral/kotlin-engineering`. |
| `.intelligence/adaptable.marketplace.json` | Install-only adaptable marketplace intent for consumer repositories. |
| `.intelligence/marketplace-lock.json` | Source-controlled lock evidence for imported marketplace references. |
| `cli/` | Kotlin Clikt command-line application. |
| `tui/` | Ratatui marketplace browser launched by bare `intelligence` in interactive terminals. |
| `schemas/` | Public provider-neutral and adapter schema contracts. |
| `packaging/` | Homebrew formula and packaging checks for the CLI. |
| `docs/` | Hand-authored Zensical documentation source. |
| `zensical.toml` | Documentation navigation, theme, and Markdown feature contract. |

Reusable marketplace primitives live in `amichne/slopsentral`.
