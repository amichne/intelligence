# Plugin Families

Plugins are composition surfaces. They assemble existing primitives by
reference, which keeps the primitive useful outside any one plugin payload.

## Marketplace Families

These families are exposed through `marketplace.json`.

| Plugin | What It Provides |
|---|---|
| `intelligence-core` | Repository onboarding, portable principles, and core hook primitives. |
| `kotlin-review` | Kotlin standards, Gradle validation, review agents, and layout checks. |
| `primitive-authoring` | Skill, agent, hook, shell script, schema, and plugin authoring workflows. |
| `repository-orientation` | Repository boundary mapping, scoped instruction authoring, and signature indexing. |
| `planning-and-docs` | Goal-definition and reference-document workflows. |
| `documentation-workflow` | MkDocs, Zensical, and docs-as-code site authoring workflows. |
| `schema-governance` | Schema contract workflow, schema review, and type-safety concepts. |
| `tdd-workflow` | Language-agnostic test-driven workflow with design concepts. |
| `version-control` | Git process, GitHub CI, Actions workflow, and release operations. |

## How To Inspect A Plugin

Open the plugin manifest and follow its primitive references.

```sh
sed -n '1,220p' plugins/kotlin-review/plugin.json
```

Validate plugin and marketplace structure after edits.

```sh
node scripts/validate-manifests.mjs
```
