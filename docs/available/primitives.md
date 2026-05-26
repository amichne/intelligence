# Primitives

Primitives are the building blocks. They are kept outside plugin payloads so
they can be inspected, tested, reused, and composed independently.

## Skill Groups

Skills live under `skills/<name>/SKILL.md`. Many include references, scripts,
templates, or examples next to the entrypoint.

| Group | Examples |
|---|---|
| Authoring | `skill-primitive-authoring`, `agent-profile-authoring`, `hook-primitive-authoring`, `plugin-composition-authoring`. |
| Repository orientation | `repo-instruction-topology`, `repository-signature-indexing`, `local-repository-navigation`. |
| Documentation and planning | `define-goal`, `reference-doc-workflow`, `site-docs-authoring`. |
| Kotlin and validation | `kotlin-standards`, `kotlin-gradle-validation`, `manage-json-schemas`, `tdd`. |
| OpenAPI | `openapi-schema-modeling`, `openapi-contract-authoring`, `openapi-contract-rating`. |
| Version control | `git-change-flow`, `github-ci-operations`, `pull-request-lifecycle`. |

## Agent Profiles

Agent profiles live under `agents/`. They are focused reviewer or enforcement
surfaces that plugins can compose.

| Agent | Focus |
|---|---|
| `schema-type-enforcer` | Schema-backed type and structured-data boundaries. |
| `kotlin-review-captain` | Kotlin review coordination. |
| `kotlin-type-safety-reviewer` | Kotlin type-safety review. |
| `kotlin-boundary-contract-reviewer` | Kotlin module and boundary contract review. |
| `kotlin-package-cohesion-reviewer` | Kotlin package layout and cohesion review. |
| `openapi-contract-rater` | OpenAPI contract quality review. |

## Hooks

Hook primitive metadata lives at `hooks/*.hook.json`. Runtime adapter configs
live under provider folders such as `hooks/codex/`.

| Hook | Purpose |
|---|---|
| `agents-md-turn-refresh` | Refreshes scoped agent instructions at turn boundaries. |
| `required-skill-read` | Checks that required skill instructions were read when configured. |
| `kotlin-horizontalization-check` | Guards Kotlin layout and package horizontalization expectations. |
| `gradle-check-green` | Runs the repository Gradle wrapper when Kotlin or Gradle-owned files changed. |

!!! note "Provider boundary"
    Keep provider-neutral hook metadata separate from adapter-specific matcher
    syntax and runtime event names. Adapter files belong under provider
    directories such as `hooks/codex/`.

## Concepts And Schemas

Concept primitives live under `concepts/`. They hold portable principles such
as `type-safety` and `schema-driven-design`.

Public provider-neutral schemas live under `schemas/core/`. Adapter schemas
live under `schemas/adapters/`.
