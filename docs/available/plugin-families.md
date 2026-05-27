# Plugin Families

Plugins are composition surfaces. They assemble existing primitives by
reference, which keeps the primitive useful outside any one plugin payload.

## Marketplace Families

These families are exposed through `marketplace.json`.

| Plugin | What It Provides |
|---|---|
| `typed-design-discipline` | Type-safety, schema-driven design, onboarding, and turn-time hooks. |
| `contract-governance` | JSON Schema and OpenAPI contracts, explicit variants, and contract review. |
| `kotlin-correctness` | Kotlin type modeling, package cohesion, Gradle validation, and review agents. |
| `evidence-driven-delivery` | Goal definition, TDD, Git hygiene, CI triage, and PR lifecycle procedures. |
| `primitive-systems-authoring` | Skills, agents, hooks, schemas, plugins, repository maps, and docs-as-code authoring. |

## How To Inspect A Plugin

Open the plugin manifest and follow its primitive references.

```sh
sed -n '1,220p' plugins/typed-design-discipline/plugin.json
```

Validate plugin and marketplace structure after edits.

```sh
node scripts/validate-manifests.mjs
```
