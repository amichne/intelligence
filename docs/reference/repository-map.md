# Repository Map

This page maps the important source locations. Use it when deciding where a
change belongs.

## Top-Level Sources

| Path | Owner |
|---|---|
| `source/adaptable.marketplace.json` | Provider-neutral curated marketplace catalog. |
| `source/agents/` | Independent reusable agent profiles. |
| `source/skills/` | Independent reusable skills. |
| `source/concepts/` | Portable concept and principle primitives. |
| `source/hooks/` | Hook metadata, implementations, requirements, and provider adapters. |
| `source/plugins/` | Referential plugin composition manifests. |
| `source/profiles/` | Schema-validated workflow profiles. |
| `source/templates/` | Primitive scaffold templates used by repository CLI tooling. |
| `cli/` | Kotlin Clikt command-line application. |
| `scripts/` | Root validation helpers invoked by the Kotlin CLI. |
| `source/schemas/` | Public provider-neutral and adapter schema contracts. |
| Generated `codex` branch | Codex marketplace projection generated from `source/`. |
| Generated `github` branch | GitHub Copilot marketplace projection generated from `source/`. |
| `docs/` | Hand-authored Zensical documentation source. |
| `zensical.toml` | Documentation navigation, theme, and Markdown feature contract. |
