# Repository Map

| Path | Purpose |
|---|---|
| `source/adaptable.marketplace.json` | Provider-neutral marketplace fixture and import graph. |
| `.intelligence/marketplace-lock.json` | Exact source-resolution evidence for imported marketplace material. |
| `cli/src/main/kotlin/intelligence/cli/command/` | Root CLI and the sole `project` command. |
| `cli/src/main/kotlin/intelligence/cli/marketplace/` | Source-to-harness projection kernel. |
| `cli/src/main/kotlin/intelligence/cli/validation/` | Source and generated-output assertions used by projection. |
| `schemas/core/` | Provider-neutral marketplace, plugin, and primitive contracts. |
| `schemas/adapters/` and `schemas/marketplace/` | Harness-specific output contracts. |
| `packaging/` | Distribution packaging for the projector binary. |
| `docs/` | Hand-authored Zensical documentation. |
| `zensical.toml` | Site navigation and rendering contract. |

Reusable personal marketplace material lives in
`/Users/amichne/code/slopsentral/source/`.
