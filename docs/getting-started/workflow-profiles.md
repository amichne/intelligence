# Workflow Profiles

Workflow profiles are checked-in target-repository contracts. A profile selects
the marketplace, plugin families, hooks, runtime links, and validation commands
that should apply to a repository.

## Create A Profile

Create a profile from the built-in Kotlin default.

```sh
bin/intelligence profile init --repo /path/to/repo --profile kotlin-repo-default
```

The default profile uses the generated marketplace branch and selects
`intelligence-core`, `kotlin-review`, and `version-control` with Codex hook
adapters.

## Dry-Run Installation

Inspect the planned writes before applying anything.

```sh
bin/intelligence install --repo /path/to/repo \
  --profile .agents/intelligence-profile.json \
  --runtime codex
```

The dry run shows the marketplace and runtime changes implied by the profile.
It is the default mode because runtime paths can point outside the current
repository.

## Apply Approved Changes

Apply only after reviewing the dry run. Marketplace imports can be opened during
`--apply`; runtime path mutations still require explicit packet approval.

```sh
bin/intelligence install --repo /path/to/repo \
  --profile .agents/intelligence-profile.json \
  --runtime codex \
  --apply \
  --approve-runtime-link codex-hook-adapters
```

!!! warning "Runtime mutation boundary"
    `--apply` is not blanket permission to rewrite runtime paths. Runtime link
    packets must be approved by name so the activation boundary stays explicit.

## Profile Shape

Profiles live under `profiles/` in this repository and are validated against
`schemas/core/workflow-profile.schema.json`.

The profile shape answers these questions:

| Field | Purpose |
|---|---|
| `marketplaces` | Where provider-native marketplace payloads come from. |
| `plugins` | Which plugin families the target repo should use. |
| `hooks` | Which hook primitives and adapters should be active. |
| `runtimeLinks` | Which runtime activation packets are in scope. |
| `validation` | Which commands should prove the profile remains valid. |
