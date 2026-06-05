# Plugin Families

Plugins are composition surfaces. They assemble existing primitives by
reference, which keeps the primitive useful outside any one plugin payload.

## Marketplace Families

These families are exposed through `source/adaptable.marketplace.json`.

| Plugin | What It Provides |
|---|---|
| `engineering-baseline` | Repository onboarding, shared instructions, and turn-level hooks. |
| `api-contracts` | JSON Schema and OpenAPI contract authoring, modeling, and review. |
| `kotlin-engineering` | Kotlin typed design, Kast semantics, Gradle proof, CI, and PR delivery. |
| `git-ci-operations` | Goal framing, TDD, Git hygiene, CI triage, releases, and shell-safe automation. |
| `agent-platform-authoring` | Skills, agents, hooks, schemas, plugin manifests, repo maps, and docs surfaces. |

## How To Inspect A Plugin

Open the plugin manifest and follow its primitive references.

```sh
sed -n '1,220p' source/plugins/kotlin-engineering/plugin.json
```

Validate plugin and marketplace structure after edits.

```sh
node scripts/validate-manifests.mjs
```
