# Workflow Profiles

Workflow profiles are checked-in target-repository contracts. A profile selects
the marketplace, plugin families, hooks, and validation commands that should
apply to a repository.

## Create A Profile

Profile creation is being moved into the Kotlin CLI. Until that command is
ported, use the checked-in JSON profiles under `source/profiles/` as the
authoritative examples and validate changes with the repository gate.

The default profile uses the published Codex marketplace branch and selects
`typed-design-discipline`, `kotlin-correctness`, and
`evidence-driven-delivery` with Codex hook adapters. That pulls in Kotlin
standards, TDD, Gradle validation, type-safety and schema-driven-design
concepts, PR lifecycle guidance, and the Kotlin layout and Gradle green-check
hooks.

## Dry-Run Installation

Inspect profile changes in review before applying them to another repository.

## Apply Changes

Apply only after reviewing the profile and marketplace references.

## Profile Shape

Profiles live under `source/profiles/` in this repository and are validated
against `schemas/core/workflow-profile.schema.json`.

The profile shape answers these questions:

| Field | Purpose |
|---|---|
| `marketplaces` | Where provider-native marketplace payloads come from. |
| `plugins` | Which plugin families the target repo should use. |
| `hooks` | Which hook primitives and adapters should be active. |
| `validation` | Which commands should prove the profile remains valid. |
