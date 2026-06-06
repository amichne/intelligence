# Why It Matters

Reusable agent behavior becomes difficult to trust when it is scattered across
runtime caches, one-off prompts, or provider-specific plugin folders.

`amichne-apm` keeps the durable boundary simple:

| Problem | APM-native answer |
|---|---|
| Scattered skills and instructions | Canonical primitives under `source/skills/`, `source/agents/`, `source/hooks/`, and `source/concepts/`. |
| Plugin payloads becoming the only copy | Plugins compose primitives by reference instead of owning them. |
| Unclear marketplace surface | `source/adaptable.marketplace.json` curates generally useful, project-agnostic plugin families. |
| Structured data drift | JSON manifests are covered by schemas and `.local/intelligence/bin/intelligence validate`. |

The repository is useful when the same agent behavior should survive across
repositories, machines, runtimes, or release cycles.
