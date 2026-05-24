# Plugin Coverage

This generated report shows how canonical primitives are exposed through referential plugins, marketplace entries, or scoped repository instructions.

All canonical primitives routed: `false`

## Coverage Status

| Status | Count |
|---|---:|
| `MARKETPLACE_EXPOSED` | 11 |
| `PLUGIN_COMPOSED` | 31 |
| `SCOPED_INSTRUCTION` | 6 |
| `STANDALONE_ONLY` | 1 |

## Primitive Types

| Type | Count |
|---|---:|
| `AGENT` | 5 |
| `HOOK` | 4 |
| `INSTRUCTION` | 8 |
| `PLUGIN` | 11 |
| `SKILL` | 21 |

## Entries

| Status | Type | Name | Canonical Paths | References |
|---|---|---|---|---|
| `PLUGIN_COMPOSED` | `AGENT` | `kotlin-boundary-contract-reviewer` | `agents/kotlin-review/kotlin-boundary-contract-reviewer.agent.md` | `kotlin-review` |
| `PLUGIN_COMPOSED` | `AGENT` | `kotlin-package-cohesion-reviewer` | `agents/kotlin-review/kotlin-package-cohesion-reviewer.agent.md` | `kotlin-review` |
| `PLUGIN_COMPOSED` | `AGENT` | `kotlin-review-captain` | `agents/kotlin-review/kotlin-review-captain.agent.md` | `kotlin-review` |
| `PLUGIN_COMPOSED` | `AGENT` | `kotlin-type-safety-reviewer` | `agents/kotlin-review/kotlin-type-safety-reviewer.agent.md` | `kotlin-review` |
| `PLUGIN_COMPOSED` | `AGENT` | `schema-type-enforcer` | `agents/schema-type-enforcer.agent.md` | `kotlin-review`, `primitive-governance`, `schema-governance` |
| `PLUGIN_COMPOSED` | `HOOK` | `agents-md-turn-refresh` | `hooks/agents-md-turn-refresh.hook.json`, `hooks/agents-md-turn-refresh.sh`, `hooks/codex/agents-md-turn-refresh.hooks.json` | `intelligence-core` |
| `PLUGIN_COMPOSED` | `HOOK` | `kotlin-horizontalization-check` | `hooks/codex/kotlin-horizontalization-check.hooks.json`, `hooks/kotlin-horizontalization-check.hook.json`, `hooks/kotlin-horizontalization-check.py` | `kotlin-review` |
| `PLUGIN_COMPOSED` | `HOOK` | `required-skill-read` | `hooks/codex/required-skill-read.hooks.json`, `hooks/required-skill-read.hook.json`, `hooks/required-skill-read.py` | `intelligence-core` |
| `STANDALONE_ONLY` | `HOOK` | `skill-requirements-schema` | `schemas/hooks/skill-requirements.schema.json` | - |
| `SCOPED_INSTRUCTION` | `INSTRUCTION` | `agents-agents-instructions` | `agents/AGENTS.md` | - |
| `SCOPED_INSTRUCTION` | `INSTRUCTION` | `concepts-agents-instructions` | `concepts/AGENTS.md` | - |
| `SCOPED_INSTRUCTION` | `INSTRUCTION` | `hooks-agents-instructions` | `hooks/AGENTS.md` | - |
| `SCOPED_INSTRUCTION` | `INSTRUCTION` | `intelligence-agents-instructions` | `AGENTS.md` | - |
| `PLUGIN_COMPOSED` | `INSTRUCTION` | `schema-driven-design` | `concepts/schema-driven-design/core.md` | `intelligence-core`, `kotlin-review`, `primitive-authoring`, `primitive-governance`, `runtime-activation`, `schema-governance`, `tdd-workflow` |
| `SCOPED_INSTRUCTION` | `INSTRUCTION` | `schemas-agents-instructions` | `schemas/AGENTS.md` | - |
| `SCOPED_INSTRUCTION` | `INSTRUCTION` | `skills-agents-instructions` | `skills/AGENTS.md` | - |
| `PLUGIN_COMPOSED` | `INSTRUCTION` | `type-safety` | `concepts/type-safety/core.md` | `intelligence-core`, `kotlin-review`, `schema-governance`, `tdd-workflow` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `documentation-workflow` | `plugins/documentation-workflow` | `marketplace:documentation-workflow` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `intelligence-core` | `plugins/intelligence-core` | `marketplace:intelligence-core` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `kotlin-review` | `plugins/kotlin-review` | `marketplace:kotlin-review` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `planning-and-docs` | `plugins/planning-and-docs` | `marketplace:planning-and-docs` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `primitive-authoring` | `plugins/primitive-authoring` | `marketplace:primitive-authoring` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `primitive-governance` | `plugins/primitive-governance` | `marketplace:primitive-governance` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `repository-orientation` | `plugins/repository-orientation` | `marketplace:repository-orientation` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `runtime-activation` | `plugins/runtime-activation` | `marketplace:runtime-activation` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `schema-governance` | `plugins/schema-governance` | `marketplace:schema-governance` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `tdd-workflow` | `plugins/tdd-workflow` | `marketplace:tdd-workflow` |
| `MARKETPLACE_EXPOSED` | `PLUGIN` | `version-control` | `plugins/version-control` | `marketplace:version-control` |
| `PLUGIN_COMPOSED` | `SKILL` | `agent-profile-authoring` | `skills/agent-profile-authoring` | `primitive-authoring` |
| `PLUGIN_COMPOSED` | `SKILL` | `define-goal` | `skills/define-goal` | `planning-and-docs` |
| `PLUGIN_COMPOSED` | `SKILL` | `git-change-flow` | `skills/git-change-flow` | `version-control` |
| `PLUGIN_COMPOSED` | `SKILL` | `github-ci-operations` | `skills/github-ci-operations` | `version-control` |
| `PLUGIN_COMPOSED` | `SKILL` | `hook-primitive-authoring` | `skills/hook-primitive-authoring` | `primitive-authoring` |
| `PLUGIN_COMPOSED` | `SKILL` | `kotlin-gradle-validation` | `skills/kotlin-gradle-validation` | `kotlin-review` |
| `PLUGIN_COMPOSED` | `SKILL` | `kotlin-standards` | `skills/kotlin-standards` | `kotlin-review` |
| `PLUGIN_COMPOSED` | `SKILL` | `local-repository-navigation` | `skills/local-repository-navigation` | `repository-orientation` |
| `PLUGIN_COMPOSED` | `SKILL` | `manage-json-schemas` | `skills/manage-json-schemas` | `primitive-authoring`, `primitive-governance`, `schema-governance` |
| `PLUGIN_COMPOSED` | `SKILL` | `plugin-composition-authoring` | `skills/plugin-composition-authoring` | `primitive-authoring` |
| `PLUGIN_COMPOSED` | `SKILL` | `primitive-quality-audit` | `skills/primitive-quality-audit` | `primitive-governance` |
| `PLUGIN_COMPOSED` | `SKILL` | `primitive-routing-evaluation` | `skills/primitive-routing-evaluation` | `primitive-governance` |
| `PLUGIN_COMPOSED` | `SKILL` | `reference-doc-workflow` | `skills/reference-doc-workflow` | `documentation-workflow`, `planning-and-docs`, `repository-orientation` |
| `PLUGIN_COMPOSED` | `SKILL` | `repo-instruction-topology` | `skills/repo-instruction-topology` | `repository-orientation` |
| `PLUGIN_COMPOSED` | `SKILL` | `repository-signature-indexing` | `skills/repository-signature-indexing` | `repository-orientation` |
| `PLUGIN_COMPOSED` | `SKILL` | `runtime-linking` | `skills/runtime-linking` | `primitive-governance`, `runtime-activation` |
| `PLUGIN_COMPOSED` | `SKILL` | `shell-script-safety` | `skills/shell-script-safety` | `primitive-authoring` |
| `PLUGIN_COMPOSED` | `SKILL` | `site-docs-authoring` | `skills/site-docs-authoring` | `documentation-workflow` |
| `PLUGIN_COMPOSED` | `SKILL` | `skill-primitive-authoring` | `skills/skill-primitive-authoring` | `primitive-authoring` |
| `PLUGIN_COMPOSED` | `SKILL` | `source-graph-consolidation` | `skills/source-graph-consolidation` | `primitive-authoring`, `primitive-governance`, `runtime-activation` |
| `PLUGIN_COMPOSED` | `SKILL` | `tdd` | `skills/tdd` | `tdd-workflow` |
