# Workflow Profiles

Workflow profiles are checked-in target-repository contracts. A profile selects
the marketplace, plugin families, hooks, and validation commands that should
apply to a repository.

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
  --profile .agents/intelligence-profile.json
```

The dry run shows the marketplace reference changes implied by the profile.

## Apply Changes

Apply only after reviewing the dry run.

```sh
bin/intelligence install --repo /path/to/repo \
  --profile .agents/intelligence-profile.json \
  --apply
```

## Profile Shape

Profiles live under `profiles/` in this repository and are validated against
`schemas/core/workflow-profile.schema.json`.

The profile shape answers these questions:

| Field | Purpose |
|---|---|
| `marketplaces` | Where provider-native marketplace payloads come from. |
| `plugins` | Which plugin families the target repo should use. |
| `hooks` | Which hook primitives and adapters should be active. |
| `validation` | Which commands should prove the profile remains valid. |
