# Plugin Families

Plugins are composition surfaces. They assemble existing primitives by
reference, which keeps the primitive useful outside any one plugin payload.

## Marketplace Families

These families are exposed through `source/adaptable.marketplace.json`.

| Plugin | What It Provides |
|---|---|
| `engineering-baseline` | Repository onboarding, shared instructions, schemas, and turn-level hooks. |
| `api-contracts` | JSON Schema and OpenAPI authoring, modeling, and review workflows. |
| `kotlin-engineering` | Kotlin typed design, Gradle proof, review agents, and delivery workflows. |
| `git-ci-operations` | Git hygiene, TDD, pull requests, CI triage, and release procedures. |
| `agent-platform-authoring` | Skills, agents, hooks, schemas, plugins, repository maps, and docs-as-code authoring. |

## How To Inspect A Plugin

Open the plugin manifest and follow its primitive references.

```sh
sed -n '1,220p' source/plugins/kotlin-engineering/plugin.json
```

Validate plugin and marketplace structure after edits.

```sh
.local/intelligence/bin/intelligence validate
```
