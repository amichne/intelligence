# Repository Map

This page maps the important source locations. Use it when deciding where a
change belongs.

## Top-Level Sources

| Path | Owner |
|---|---|
| `marketplace.json` | Provider-neutral curated marketplace catalog. |
| `agents/` | Independent reusable agent profiles. |
| `skills/` | Independent reusable skills. |
| `concepts/` | Portable concept and principle primitives. |
| `hooks/` | Hook metadata, implementations, requirements, and provider adapters. |
| `plugins/` | Referential plugin composition manifests. |
| `profiles/` | Schema-validated workflow profiles. |
| `templates/` | Primitive scaffold templates used by `bin/intelligence`. |
| `bin/` | Thin local command wrappers. |
| `scripts/` | Root validation, packaging, and marketplace publication tooling. |
| `schemas/` | Public provider-neutral and adapter schema contracts. |
| `garden/` | Inventory, generated reports, review ledgers, activation plans, and scripts. |
| `docs/` | Hand-authored Zensical documentation source. |
| `zensical.toml` | Documentation navigation, theme, and Markdown feature contract. |

## Garden Layout

`garden/` exists to keep source graph evidence separate from canonical
primitive source.

| Path | Role |
|---|---|
| `garden/manifests/source-roots.json` | Hand-authored scan boundary. |
| `garden/manifests/discovered-primitives.json` | Generated inventory evidence. |
| `garden/manifests/promotions.json` | Provenance for copied-in canonical primitives. |
| `garden/manifests/primitive-audits.json` | Durable quality and readiness decisions. |
| `garden/manifests/cleanup-ledger.json` | Approval gate for cleanup and replacement records. |
| `garden/docs/` | Generated human-readable summaries. |
| `garden/scripts/` | Generators and checkers for the evidence files. |
| `garden/schemas/intelligence/` | Repository-specific schema contracts. |

## Generated Boundaries

Generated garden outputs should be changed through their owner scripts. The
documentation site may reference them, but it should not copy their detailed
tables unless there is a clear reason.
