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
| `source/templates/` | Primitive scaffold templates used by `bin/intelligence`. |
| `bin/` | Thin local command wrappers. |
| `scripts/` | Root validation, packaging, APM staging, and marketplace publication tooling. |
| `source/schemas/` | Public provider-neutral and adapter schema contracts. |
| `apm.yml` | Checked APM marketplace manifest generated from the curated source catalog. |
| `build/apm-marketplace/` | Ignored APM marketplace workspace generated for preview and release artifacts. |
| `docs/` | Hand-authored Zensical documentation source. |
| `zensical.toml` | Documentation navigation, theme, and Markdown feature contract. |
