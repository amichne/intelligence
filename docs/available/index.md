# What Is Available

The repository contains reusable AI tooling in several forms. The stable shape
is referential composition: primitives are independent, plugins compose them by
reference, and the marketplace exposes a curated subset.

## Current Surfaces

The current tree includes skills, agent profiles, hook metadata,
instruction/concept documents, workflow profiles, public schemas, and plugin
manifests.

| Surface | Directory | Use It For |
|---|---|---|
| Skills | `skills/` | Reusable agent workflows and operating procedures. |
| Agents | `agents/` | Focused review or enforcement profiles. |
| Hooks | `hooks/` | Provider-neutral hook metadata, implementations, and adapters. |
| Concepts | `concepts/` | Portable principles and instruction primitives. |
| Plugins | `plugins/` | Referential composition of existing primitives. |
| Profiles | `profiles/` | Target-repository workflow selections. |
| Schemas | `schemas/` | Structured-data contracts. |

## Distribution Boundary

`adaptable.marketplace.json` exposes generally useful, project-agnostic plugin families.
Private cleanup and activation utilities are intentionally kept outside this
public repository.

Use [Plugin families](plugin-families.md) for the curated plugin map and
[Primitives](primitives.md) for the building blocks beneath it.
