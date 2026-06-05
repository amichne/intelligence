# Plugin Families

These APM package families are exposed through the root `apm.yml` marketplace.

| Package | What It Provides |
|---|---|
| `engineering-baseline` | Repository onboarding, shared instructions, and Codex turn-level hooks. |
| `api-contracts` | JSON Schema and OpenAPI contract authoring, modeling, and review. |
| `kotlin-engineering` | Kotlin typed design, review agents, Gradle proof, CI, and PR delivery. |
| `git-ci-operations` | Goal framing, TDD, Git hygiene, CI triage, releases, and shell-safe automation. |
| `apm-authoring` | APM package authoring, marketplace metadata, hooks, schemas, repo maps, and docs surfaces. |

Inspect a package directly.

```sh
sed -n '1,160p' packages/apm-authoring/apm.yml
find packages/apm-authoring/.apm -maxdepth 3 -type f
```
