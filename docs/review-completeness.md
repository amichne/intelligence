# Review Completeness

This generated report compares canonical primitive coverage with durable audit entries.

## Summary

| Measure | Value |
|---|---:|
| Total canonical | 48 |
| Audited | 48 |
| Needs audit | 0 |
| Promoted | 30 |
| Native canonical | 18 |
| All canonical audited | `true` |

## Needs Audit

| Type | Name | Coverage | Promotion | Paths | Notes |
|---|---|---|---|---|---|
| - | - | - | - | - | - |

## Audited

| Type | Name | Decision | Coverage | Promotion |
|---|---|---|---|---|
| `AGENT` | `kotlin-boundary-contract-reviewer` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `AGENT` | `kotlin-package-cohesion-reviewer` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `AGENT` | `kotlin-review-captain` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `AGENT` | `kotlin-type-safety-reviewer` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `AGENT` | `schema-type-enforcer` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `HOOK` | `agents-md-turn-refresh` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `NATIVE_CANONICAL` |
| `HOOK` | `kotlin-horizontalization-check` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `HOOK` | `required-skill-read` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `INSTRUCTION` | `agents-agents-instructions` | `PROMOTE_READY` | `SCOPED_INSTRUCTION` | `NATIVE_CANONICAL` |
| `INSTRUCTION` | `concepts-agents-instructions` | `PROMOTE_READY` | `SCOPED_INSTRUCTION` | `NATIVE_CANONICAL` |
| `INSTRUCTION` | `hooks-agents-instructions` | `PROMOTE_READY` | `SCOPED_INSTRUCTION` | `NATIVE_CANONICAL` |
| `INSTRUCTION` | `intelligence-agents-instructions` | `PROMOTE_READY` | `SCOPED_INSTRUCTION` | `NATIVE_CANONICAL` |
| `INSTRUCTION` | `schema-driven-design` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `NATIVE_CANONICAL` |
| `INSTRUCTION` | `schemas-agents-instructions` | `PROMOTE_READY` | `SCOPED_INSTRUCTION` | `NATIVE_CANONICAL` |
| `INSTRUCTION` | `skills-agents-instructions` | `PROMOTE_READY` | `SCOPED_INSTRUCTION` | `NATIVE_CANONICAL` |
| `INSTRUCTION` | `type-safety` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `documentation-workflow` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `intelligence-core` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `kotlin-review` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `planning-and-docs` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `primitive-authoring` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `primitive-governance` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `PROMOTED` |
| `PLUGIN` | `repository-orientation` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `runtime-activation` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `PROMOTED` |
| `PLUGIN` | `schema-governance` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `tdd-workflow` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `PLUGIN` | `version-control` | `PROMOTE_READY` | `MARKETPLACE_EXPOSED` | `NATIVE_CANONICAL` |
| `SKILL` | `agent-profile-authoring` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `define-goal` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `git-change-flow` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `github-ci-operations` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `hook-primitive-authoring` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `kotlin-gradle-validation` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `kotlin-standards` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `local-repository-navigation` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `manage-json-schemas` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `plugin-composition-authoring` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `primitive-quality-audit` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `primitive-routing-evaluation` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `reference-doc-workflow` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `repo-instruction-topology` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `repository-signature-indexing` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `runtime-linking` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `shell-script-safety` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `site-docs-authoring` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `skill-primitive-authoring` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `source-graph-consolidation` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
| `SKILL` | `tdd` | `PROMOTE_READY` | `PLUGIN_COMPOSED` | `PROMOTED` |
