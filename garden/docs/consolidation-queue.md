# Consolidation Queue

Inventory source: `garden/manifests/discovered-primitives.json`
Inventory generated at: `2026-05-25T13:38:53.197584+00:00`

## Counts

| Bucket | Count |
|---|---:|
| `authored-local` | 87 |
| `backup` | 33 |
| `canonical` | 56 |
| `installed-marketplace` | 608 |
| `runtime` | 37 |
| `runtime-alias` | 48 |

| Type | Count |
|---|---:|
| `AGENT` | 299 |
| `HOOK` | 42 |
| `INSTRUCTION` | 13 |
| `PLUGIN` | 144 |
| `SKILL` | 371 |

## Promotion Candidates

These are canonical or local-authored entries. They are the first review set before importing runtime/cache material.

| Type | Name | Bucket | Source | Path | Action |
|---|---|---|---|---|---|
| `AGENT` | `agent-creation-prompt` | `authored-local` | `kast-agent-skills` | `agent-development/examples/agent-creation-prompt.md` | `REVIEW_PROMOTE` |
| `AGENT` | `agent-creation-system-prompt` | `authored-local` | `kast-agent-skills` | `agent-development/references/agent-creation-system-prompt.md` | `REVIEW_PROMOTE` |
| `AGENT` | `agents-update-contract` | `authored-local` | `kast-agent-skills` | `refresh-affected-agents/references/agents-update-contract.md` | `REVIEW_PROMOTE` |
| `AGENT` | `analyzer` | `authored-local` | `kast-agent-skills` | `skill-creator/agents/analyzer.md` | `REVIEW_PROMOTE` |
| `AGENT` | `bootstrap-prompt` | `authored-local` | `apollo-agents` | `codebase-navigator/copilot/prompts/bootstrap.prompt.md` | `REVIEW_PROMOTE` |
| `AGENT` | `chatmode` | `authored-local` | `apollo-agents` | `codebase-navigator/copilot/chatmode.md` | `REVIEW_PROMOTE` |
| `AGENT` | `codebase-navigator` | `authored-local` | `apollo-agents` | `codebase-navigator/AGENT.md` | `REVIEW_PROMOTE` |
| `AGENT` | `commands` | `authored-local` | `kast-agent-skills` | `kast/references/commands.json` | `REVIEW_PROMOTE` |
| `AGENT` | `comparator` | `authored-local` | `kast-agent-skills` | `skill-creator/agents/comparator.md` | `REVIEW_PROMOTE` |
| `AGENT` | `complete-agent-examples` | `authored-local` | `kast-agent-skills` | `agent-development/examples/complete-agent-examples.md` | `REVIEW_PROMOTE` |
| `AGENT` | `evaluation-scaffold` | `authored-local` | `kast-agent-skills` | `skill-creator/references/evaluation_scaffold.md` | `REVIEW_PROMOTE` |
| `AGENT` | `grader` | `authored-local` | `kast-agent-skills` | `skill-creator/agents/grader.md` | `REVIEW_PROMOTE` |
| `AGENT` | `instructions-snippet` | `authored-local` | `apollo-agents` | `codebase-navigator/copilot/instructions-snippet.md` | `REVIEW_PROMOTE` |
| `AGENT` | `interface-metadata` | `authored-local` | `kast-agent-skills` | `skill-creator/references/interface_metadata.md` | `REVIEW_PROMOTE` |
| `AGENT` | `kast-orchestrator` | `authored-local` | `kast-github-agents` | `kast-orchestrator.md` | `REVIEW_PROMOTE` |
| `AGENT` | `kotlin-boundary-contract-reviewer` | `authored-local` | `examplar-agents` | `kotlin-boundary-contract-reviewer.agent.md` | `REVIEW_PROMOTE` |
| `AGENT` | `kotlin-boundary-contract-reviewer` | `canonical` | `intelligence` | `agents/kotlin-review/kotlin-boundary-contract-reviewer.agent.md` | `KEEP_CANONICAL` |
| `AGENT` | `kotlin-package-cohesion-reviewer` | `authored-local` | `examplar-agents` | `kotlin-package-cohesion-reviewer.agent.md` | `REVIEW_PROMOTE` |
| `AGENT` | `kotlin-package-cohesion-reviewer` | `canonical` | `intelligence` | `agents/kotlin-review/kotlin-package-cohesion-reviewer.agent.md` | `KEEP_CANONICAL` |
| `AGENT` | `kotlin-review-captain` | `authored-local` | `examplar-agents` | `kotlin-review-captain.agent.md` | `REVIEW_PROMOTE` |
| `AGENT` | `kotlin-review-captain` | `canonical` | `intelligence` | `agents/kotlin-review/kotlin-review-captain.agent.md` | `KEEP_CANONICAL` |
| `AGENT` | `kotlin-type-safety-reviewer` | `authored-local` | `examplar-agents` | `kotlin-type-safety-reviewer.agent.md` | `REVIEW_PROMOTE` |
| `AGENT` | `kotlin-type-safety-reviewer` | `canonical` | `intelligence` | `agents/kotlin-review/kotlin-type-safety-reviewer.agent.md` | `KEEP_CANONICAL` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/imagegen/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/openai-docs/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/plugin-creator/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/skill-creator/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/skill-installer/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-agents` | `codebase-navigator/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `jira-resolve-ticket/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `kast-agent-skills` | `llm-wiki/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `page-patterns` | `authored-local` | `kast-agent-skills` | `llm-wiki/references/page-patterns.md` | `REVIEW_PROMOTE` |
| `AGENT` | `process-outdated-prompt` | `authored-local` | `apollo-agents` | `codebase-navigator/copilot/prompts/process-outdated.prompt.md` | `REVIEW_PROMOTE` |
| `AGENT` | `quickstart` | `authored-local` | `kast-agent-skills` | `kast/references/quickstart.md` | `REVIEW_PROMOTE` |
| `AGENT` | `routing-improvement` | `authored-local` | `kast-agent-skills` | `kast/fixtures/maintenance/references/routing-improvement.md` | `REVIEW_PROMOTE` |
| `AGENT` | `routing-improvement` | `authored-local` | `kast-agent-skills` | `kast/references/routing-improvement.md` | `REVIEW_PROMOTE` |
| `AGENT` | `schema-type-enforcer` | `authored-local` | `examplar-agents` | `schema-type-enforcer.agent.md` | `REVIEW_PROMOTE` |
| `AGENT` | `schema-type-enforcer` | `canonical` | `intelligence` | `agents/schema-type-enforcer.agent.md` | `KEEP_CANONICAL` |
| `AGENT` | `schemas` | `authored-local` | `kast-agent-skills` | `skill-creator/references/schemas.md` | `REVIEW_PROMOTE` |
| `AGENT` | `system-prompt-design` | `authored-local` | `kast-agent-skills` | `agent-development/references/system-prompt-design.md` | `REVIEW_PROMOTE` |
| `AGENT` | `triggering-examples` | `authored-local` | `kast-agent-skills` | `agent-development/references/triggering-examples.md` | `REVIEW_PROMOTE` |
| `AGENT` | `update-dir-prompt` | `authored-local` | `apollo-agents` | `codebase-navigator/copilot/prompts/update-dir.prompt.md` | `REVIEW_PROMOTE` |
| `HOOK` | `agents-md-turn-refresh` | `canonical` | `intelligence` | `hooks/agents-md-turn-refresh.hook.json` | `KEEP_CANONICAL` |
| `HOOK` | `agents-md-turn-refresh` | `canonical` | `intelligence` | `hooks/agents-md-turn-refresh.sh` | `KEEP_CANONICAL` |
| `HOOK` | `agents-md-turn-refresh` | `canonical` | `intelligence` | `hooks/codex/agents-md-turn-refresh.hooks.json` | `KEEP_CANONICAL` |
| `HOOK` | `export-session` | `authored-local` | `kast-github-hooks` | `export-session.py` | `REVIEW_PROMOTE` |
| `HOOK` | `hook-state` | `authored-local` | `kast-github-hooks` | `hook-state.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `hooks` | `authored-local` | `kast-github-hooks` | `hooks.json` | `REVIEW_PROMOTE` |
| `HOOK` | `kotlin-horizontalization-check` | `authored-local` | `examplar-hooks` | `copilot/kotlin-horizontalization-check.py` | `REVIEW_PROMOTE` |
| `HOOK` | `kotlin-horizontalization-check` | `canonical` | `intelligence` | `hooks/codex/kotlin-horizontalization-check.hooks.json` | `KEEP_CANONICAL` |
| `HOOK` | `kotlin-horizontalization-check` | `canonical` | `intelligence` | `hooks/kotlin-horizontalization-check.hook.json` | `KEEP_CANONICAL` |
| `HOOK` | `kotlin-horizontalization-check` | `canonical` | `intelligence` | `hooks/kotlin-horizontalization-check.py` | `KEEP_CANONICAL` |
| `HOOK` | `record-paths` | `authored-local` | `kast-github-hooks` | `record-paths.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `require-skills` | `authored-local` | `kast-github-hooks` | `require-skills.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `required-skill-read` | `canonical` | `intelligence` | `hooks/codex/required-skill-read.hooks.json` | `KEEP_CANONICAL` |
| `HOOK` | `required-skill-read` | `canonical` | `intelligence` | `hooks/required-skill-read.hook.json` | `KEEP_CANONICAL` |
| `HOOK` | `required-skill-read` | `canonical` | `intelligence` | `hooks/required-skill-read.py` | `KEEP_CANONICAL` |
| `HOOK` | `resolve-kast-path` | `authored-local` | `kast-github-hooks` | `resolve-kast-path.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `session-end` | `authored-local` | `kast-github-hooks` | `session-end.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `session-start` | `authored-local` | `kast-github-hooks` | `session-start.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `skill-requirements-schema` | `canonical` | `intelligence` | `schemas/hooks/skill-requirements.schema.json` | `KEEP_CANONICAL` |
| `HOOK` | `skill-shadowing` | `authored-local` | `kast-github-hooks` | `skill-shadowing.json` | `REVIEW_PROMOTE` |
| `INSTRUCTION` | `agents-agents-instructions` | `canonical` | `intelligence` | `agents/AGENTS.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `concepts-agents-instructions` | `canonical` | `intelligence` | `concepts/AGENTS.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `hooks-agents-instructions` | `canonical` | `intelligence` | `hooks/AGENTS.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `instructions-agents-instructions` | `authored-local` | `examplar-instructions` | `AGENTS.md` | `REVIEW_PROMOTE` |
| `INSTRUCTION` | `intelligence-agents-instructions` | `canonical` | `intelligence` | `AGENTS.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `schema-driven-design` | `authored-local` | `examplar-instructions` | `schema-driven-design.md` | `REVIEW_PROMOTE` |
| `INSTRUCTION` | `schema-driven-design` | `canonical` | `intelligence` | `concepts/schema-driven-design/core.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `schemas-agents-instructions` | `canonical` | `intelligence` | `schemas/AGENTS.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `skills-agents-instructions` | `canonical` | `intelligence` | `skills/AGENTS.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `type-safety` | `authored-local` | `examplar-instructions` | `type-safety.md` | `REVIEW_PROMOTE` |
| `INSTRUCTION` | `type-safety` | `canonical` | `intelligence` | `concepts/type-safety/core.md` | `KEEP_CANONICAL` |
| `PLUGIN` | `agentic-coding` | `authored-local` | `examplar-plugins` | `agentic-coding` | `REVIEW_PROMOTE` |
| `PLUGIN` | `documentation-workflow` | `canonical` | `intelligence` | `plugins/documentation-workflow` | `KEEP_CANONICAL` |
| `PLUGIN` | `intelligence-core` | `canonical` | `intelligence` | `plugins/intelligence-core` | `KEEP_CANONICAL` |
| `PLUGIN` | `kotlin-review` | `canonical` | `intelligence` | `plugins/kotlin-review` | `KEEP_CANONICAL` |
| `PLUGIN` | `planning-and-docs` | `canonical` | `intelligence` | `plugins/planning-and-docs` | `KEEP_CANONICAL` |
| `PLUGIN` | `plugin-eval` | `authored-local` | `examplar-plugins` | `plugin-eval` | `REVIEW_PROMOTE` |
| `PLUGIN` | `primitive-authoring` | `canonical` | `intelligence` | `plugins/primitive-authoring` | `KEEP_CANONICAL` |
| | | | | plus 63 more in the JSON report | |

## Name Review Queue

Name collisions need semantic review unless every entry has the same digest.

| Priority | Type | Name | Action | Buckets | Entries |
|---:|---|---|---|---|---:|
| 1 | `AGENT` | `routing-improvement` | `SYNTHESIZE` | `authored-local` | 2 |
| 1 | `HOOK` | `agents-md-turn-refresh` | `SYNTHESIZE` | `canonical` | 3 |
| 1 | `HOOK` | `hooks` | `SYNTHESIZE` | `authored-local, installed-marketplace` | 11 |
| 1 | `HOOK` | `kotlin-horizontalization-check` | `SYNTHESIZE` | `authored-local, canonical` | 4 |
| 1 | `HOOK` | `required-skill-read` | `SYNTHESIZE` | `canonical` | 3 |
| 1 | `PLUGIN` | `plugin-eval` | `SYNTHESIZE` | `authored-local, installed-marketplace` | 2 |
| 1 | `SKILL` | `agent-development` | `SYNTHESIZE` | `authored-local, installed-marketplace` | 2 |
| 1 | `SKILL` | `bash-defensive-patterns` | `SYNTHESIZE` | `backup, installed-marketplace` | 2 |
| 1 | `SKILL` | `gh-fix-ci` | `SYNTHESIZE` | `backup, runtime` | 3 |
| 1 | `SKILL` | `llm-native-signature-spec` | `SYNTHESIZE` | `backup` | 2 |
| 2 | `AGENT` | `analyzer` | `DEDUP_IDENTICAL_NAME` | `authored-local, installed-marketplace, runtime` | 5 |
| 2 | `AGENT` | `comparator` | `DEDUP_IDENTICAL_NAME` | `authored-local, installed-marketplace, runtime` | 5 |
| 2 | `AGENT` | `grader` | `DEDUP_IDENTICAL_NAME` | `authored-local, installed-marketplace, runtime` | 5 |
| 2 | `AGENT` | `kotlin-boundary-contract-reviewer` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `AGENT` | `kotlin-package-cohesion-reviewer` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `AGENT` | `kotlin-review-captain` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `AGENT` | `kotlin-type-safety-reviewer` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `AGENT` | `openai` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, runtime` | 26 |
| 2 | `AGENT` | `schema-type-enforcer` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `INSTRUCTION` | `schema-driven-design` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `INSTRUCTION` | `type-safety` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `agent-profile-authoring` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `define-goal` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `git-change-flow` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `github-ci-operations` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `hook-primitive-authoring` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `imagegen` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, runtime` | 3 |
| 2 | `SKILL` | `jira-resolve-ticket` | `DEDUP_IDENTICAL_NAME` | `authored-local, runtime` | 2 |
| 2 | `SKILL` | `kotlin-architect` | `DEDUP_IDENTICAL_NAME` | `backup` | 2 |
| 2 | `SKILL` | `kotlin-gradle-validation` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `kotlin-standards` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 3 |
| 2 | `SKILL` | `local-repository-navigation` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `manage-json-schemas` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 3 |
| 2 | `SKILL` | `openai-docs` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, runtime` | 4 |
| 2 | `SKILL` | `plugin-composition-authoring` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `plugin-creator` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, runtime` | 3 |
| 2 | `SKILL` | `primitive-quality-audit` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `primitive-routing-evaluation` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `reference-doc-workflow` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `repo-instruction-topology` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `repository-signature-indexing` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `runtime-linking` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `shell-script-safety` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `site-docs-authoring` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `skill-creator` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, installed-marketplace, runtime` | 9 |
| 2 | `SKILL` | `skill-installer` | `DEDUP_IDENTICAL_NAME` | `authored-local, backup, runtime` | 4 |
| 2 | `SKILL` | `skill-primitive-authoring` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `source-graph-consolidation` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 2 |
| 2 | `SKILL` | `tdd` | `DEDUP_IDENTICAL_NAME` | `authored-local, canonical` | 3 |
| 2 | `SKILL` | `web-artifacts-builder` | `DEDUP_IDENTICAL_NAME` | `installed-marketplace, runtime` | 2 |

## Source Review Decisions

Hand-authored trim decisions cover generated name-review groups. These decisions do not authorize deletion; cleanup still requires `garden/manifests/cleanup-ledger.json`.

| Decision | Count |
|---|---:|
| `COVERED_BY_CANONICAL` | 35 |
| `KEEP_CANONICAL` | 2 |
| `RETAIN_EXTERNAL` | 13 |

| Status | Count |
|---|---:|
| `DECIDED` | 50 |

| Priority | Type | Name | Decision | Status | Coverage |
|---:|---|---|---|---|---|
| 1 | `AGENT` | `routing-improvement` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/primitive-routing-evaluation` |
| 1 | `HOOK` | `agents-md-turn-refresh` | `KEEP_CANONICAL` | `DECIDED` | `hooks/agents-md-turn-refresh.hook.json`, `hooks/agents-md-turn-refresh.sh`, `hooks/codex/agents-md-turn-refresh.hooks.json` |
| 1 | `HOOK` | `hooks` | `RETAIN_EXTERNAL` | `DECIDED` | `skills/hook-primitive-authoring`, `hooks/required-skill-read.hook.json`, `hooks/agents-md-turn-refresh.hook.json` |
| 1 | `HOOK` | `kotlin-horizontalization-check` | `COVERED_BY_CANONICAL` | `DECIDED` | `hooks/kotlin-horizontalization-check.hook.json`, `hooks/kotlin-horizontalization-check.py`, `hooks/codex/kotlin-horizontalization-check.hooks.json` |
| 1 | `HOOK` | `required-skill-read` | `KEEP_CANONICAL` | `DECIDED` | `hooks/required-skill-read.hook.json`, `hooks/required-skill-read.py`, `hooks/codex/required-skill-read.hooks.json` |
| 1 | `PLUGIN` | `plugin-eval` | `RETAIN_EXTERNAL` | `DECIDED` | `skills/primitive-quality-audit`, `plugins/primitive-governance` |
| 1 | `SKILL` | `agent-development` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/agent-profile-authoring`, `skills/skill-primitive-authoring` |
| 1 | `SKILL` | `bash-defensive-patterns` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/shell-script-safety` |
| 1 | `SKILL` | `gh-fix-ci` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/github-ci-operations`, `plugins/version-control` |
| 1 | `SKILL` | `llm-native-signature-spec` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/repository-signature-indexing` |
| 2 | `AGENT` | `analyzer` | `RETAIN_EXTERNAL` | `DECIDED` | `skills/skill-primitive-authoring` |
| 2 | `AGENT` | `comparator` | `RETAIN_EXTERNAL` | `DECIDED` | `skills/skill-primitive-authoring` |
| 2 | `AGENT` | `grader` | `RETAIN_EXTERNAL` | `DECIDED` | `skills/skill-primitive-authoring` |
| 2 | `AGENT` | `kotlin-boundary-contract-reviewer` | `COVERED_BY_CANONICAL` | `DECIDED` | `agents/kotlin-review/kotlin-boundary-contract-reviewer.agent.md` |
| 2 | `AGENT` | `kotlin-package-cohesion-reviewer` | `COVERED_BY_CANONICAL` | `DECIDED` | `agents/kotlin-review/kotlin-package-cohesion-reviewer.agent.md` |
| 2 | `AGENT` | `kotlin-review-captain` | `COVERED_BY_CANONICAL` | `DECIDED` | `agents/kotlin-review/kotlin-review-captain.agent.md` |
| 2 | `AGENT` | `kotlin-type-safety-reviewer` | `COVERED_BY_CANONICAL` | `DECIDED` | `agents/kotlin-review/kotlin-type-safety-reviewer.agent.md` |
| 2 | `AGENT` | `openai` | `RETAIN_EXTERNAL` | `DECIDED` | `skills/agent-profile-authoring`, `skills/skill-primitive-authoring` |
| 2 | `AGENT` | `schema-type-enforcer` | `COVERED_BY_CANONICAL` | `DECIDED` | `agents/schema-type-enforcer.agent.md` |
| 2 | `INSTRUCTION` | `schema-driven-design` | `COVERED_BY_CANONICAL` | `DECIDED` | `concepts/schema-driven-design/core.md` |
| 2 | `INSTRUCTION` | `type-safety` | `COVERED_BY_CANONICAL` | `DECIDED` | `concepts/type-safety/core.md` |
| 2 | `SKILL` | `agent-profile-authoring` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/agent-profile-authoring` |
| 2 | `SKILL` | `define-goal` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/define-goal` |
| 2 | `SKILL` | `git-change-flow` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/git-change-flow` |
| 2 | `SKILL` | `github-ci-operations` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/github-ci-operations` |
| 2 | `SKILL` | `hook-primitive-authoring` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/hook-primitive-authoring` |
| 2 | `SKILL` | `imagegen` | `RETAIN_EXTERNAL` | `DECIDED` | - |
| 2 | `SKILL` | `jira-resolve-ticket` | `RETAIN_EXTERNAL` | `DECIDED` | - |
| 2 | `SKILL` | `kotlin-architect` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/kotlin-standards`, `plugins/kotlin-review` |
| 2 | `SKILL` | `kotlin-gradle-validation` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/kotlin-gradle-validation` |
| 2 | `SKILL` | `kotlin-standards` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/kotlin-standards` |
| 2 | `SKILL` | `local-repository-navigation` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/local-repository-navigation` |
| 2 | `SKILL` | `manage-json-schemas` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/manage-json-schemas` |
| 2 | `SKILL` | `openai-docs` | `RETAIN_EXTERNAL` | `DECIDED` | - |
| 2 | `SKILL` | `plugin-composition-authoring` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/plugin-composition-authoring` |
| 2 | `SKILL` | `plugin-creator` | `RETAIN_EXTERNAL` | `DECIDED` | `skills/plugin-composition-authoring` |
| 2 | `SKILL` | `primitive-quality-audit` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/primitive-quality-audit` |
| 2 | `SKILL` | `primitive-routing-evaluation` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/primitive-routing-evaluation` |
| 2 | `SKILL` | `reference-doc-workflow` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/reference-doc-workflow` |
| 2 | `SKILL` | `repo-instruction-topology` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/repo-instruction-topology` |
| 2 | `SKILL` | `repository-signature-indexing` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/repository-signature-indexing` |
| 2 | `SKILL` | `runtime-linking` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/runtime-linking` |
| 2 | `SKILL` | `shell-script-safety` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/shell-script-safety` |
| 2 | `SKILL` | `site-docs-authoring` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/site-docs-authoring` |
| 2 | `SKILL` | `skill-creator` | `RETAIN_EXTERNAL` | `DECIDED` | `skills/skill-primitive-authoring` |
| 2 | `SKILL` | `skill-installer` | `RETAIN_EXTERNAL` | `DECIDED` | - |
| 2 | `SKILL` | `skill-primitive-authoring` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/skill-primitive-authoring` |
| 2 | `SKILL` | `source-graph-consolidation` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/source-graph-consolidation` |
| 2 | `SKILL` | `tdd` | `COVERED_BY_CANONICAL` | `DECIDED` | `skills/tdd` |
| 2 | `SKILL` | `web-artifacts-builder` | `RETAIN_EXTERNAL` | `DECIDED` | - |

## Digest Review Queue

Digest duplicates are candidates for symlink/reference replacement after promotion is verified.

| Priority | Entries | Buckets | Digest |
|---:|---:|---|---|
| 2 | 2 | `authored-local, runtime` | `sha256:03f8f25b25be6267c15f42a87c15caf54fe0863d40ebf90104c4a44e93a1864c` |
| 2 | 4 | `authored-local, backup, runtime` | `sha256:104b792090370aff47c4a70411cb84e737b7cfa9699f2f54c916d8946fc98f16` |
| 2 | 2 | `authored-local, runtime` | `sha256:3555f7bdb3a50a4ccb99be8d94d23528509b988f3f72645e03705da25594b66c` |
| 2 | 5 | `authored-local, installed-marketplace, runtime` | `sha256:57134da0c1a4eea33fbd74a1c9c44aa814f07d6bc64de303edb586f941e5d21a` |
| 2 | 2 | `authored-local, runtime` | `sha256:580081dd6c0896d72307d1f1b90d2248a64b9353bc546fdbf05ddeb3848e8c39` |
| 2 | 4 | `authored-local, backup, runtime` | `sha256:5ce223d8b1070b82c42298538f1b8d376f788eb9e7a42a987e8c094070d73f0e` |
| 2 | 4 | `authored-local, backup, runtime` | `sha256:67717a8afb51f29380e849cad62036628bd5a3c8193bacf3b00b5cd7a30fc1cb` |
| 2 | 2 | `authored-local, runtime` | `sha256:71bc91120548c46092abb98dba5ef2d91b12224d1106d6abfdc57862a6e26683` |
| 2 | 2 | `authored-local, runtime` | `sha256:9ca574af14580dc7a2a3dc37a1796d17f93cb8850be66501f0799ef8603e9dc0` |
| 2 | 5 | `authored-local, installed-marketplace, runtime` | `sha256:bf68f4cac5a56c673a928c2e6d619586c5b93ea364026ab37547772cb45a663a` |
| 2 | 4 | `authored-local, backup, runtime` | `sha256:d07d21b93fcf3d4dc8d9a3399c05fc226a49a333a96d3e1c68b451b8dd9eade6` |
| 2 | 2 | `authored-local, runtime` | `sha256:f00f09ecb7c682f9bfb09bbbc93de6242b2d10c7e4d97f059d91de82c14246c8` |
| 2 | 2 | `authored-local, runtime` | `sha256:f412bf1efc6fd541dcdd2db0b3347d6f6735899d35d1831a27ef439036c39a47` |
| 2 | 5 | `authored-local, installed-marketplace, runtime` | `sha256:fe1fc9787c495d864c5d6eada47396478572325fde1b33a96d78bf4b849b7a3e` |
| 2 | 2 | `authored-local, runtime` | `sha256:fecaf35d692bd3d33d1a065648258d12e393afa9055d78adf6e57b42f4142f6d` |

## Digest Review Decisions

Hand-authored trim decisions cover generated duplicate-content groups. These decisions do not authorize deletion; cleanup still requires `garden/manifests/cleanup-ledger.json`.

| Decision | Count |
|---|---:|
| `RETAIN_EXTERNAL` | 15 |

| Status | Count |
|---|---:|
| `DECIDED` | 15 |

| Priority | Digest | Decision | Status | Candidates | Coverage |
|---:|---|---|---|---|---|
| 2 | `sha256:03f8f25b25be6267c15f42a87c15caf54fe0863d40ebf90104c4a44e93a1864c` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:openai` | - |
| 2 | `sha256:104b792090370aff47c4a70411cb84e737b7cfa9699f2f54c916d8946fc98f16` | `RETAIN_EXTERNAL` | `DECIDED` | `SKILL:skill-creator` | - |
| 2 | `sha256:3555f7bdb3a50a4ccb99be8d94d23528509b988f3f72645e03705da25594b66c` | `RETAIN_EXTERNAL` | `DECIDED` | `SKILL:imagegen` | - |
| 2 | `sha256:57134da0c1a4eea33fbd74a1c9c44aa814f07d6bc64de303edb586f941e5d21a` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:grader` | - |
| 2 | `sha256:580081dd6c0896d72307d1f1b90d2248a64b9353bc546fdbf05ddeb3848e8c39` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:openai` | - |
| 2 | `sha256:5ce223d8b1070b82c42298538f1b8d376f788eb9e7a42a987e8c094070d73f0e` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:openai` | - |
| 2 | `sha256:67717a8afb51f29380e849cad62036628bd5a3c8193bacf3b00b5cd7a30fc1cb` | `RETAIN_EXTERNAL` | `DECIDED` | `SKILL:skill-installer` | - |
| 2 | `sha256:71bc91120548c46092abb98dba5ef2d91b12224d1106d6abfdc57862a6e26683` | `RETAIN_EXTERNAL` | `DECIDED` | `SKILL:plugin-creator` | - |
| 2 | `sha256:9ca574af14580dc7a2a3dc37a1796d17f93cb8850be66501f0799ef8603e9dc0` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:openai` | - |
| 2 | `sha256:bf68f4cac5a56c673a928c2e6d619586c5b93ea364026ab37547772cb45a663a` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:analyzer` | - |
| 2 | `sha256:d07d21b93fcf3d4dc8d9a3399c05fc226a49a333a96d3e1c68b451b8dd9eade6` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:openai` | - |
| 2 | `sha256:f00f09ecb7c682f9bfb09bbbc93de6242b2d10c7e4d97f059d91de82c14246c8` | `RETAIN_EXTERNAL` | `DECIDED` | `SKILL:openai-docs` | - |
| 2 | `sha256:f412bf1efc6fd541dcdd2db0b3347d6f6735899d35d1831a27ef439036c39a47` | `RETAIN_EXTERNAL` | `DECIDED` | `SKILL:jira-resolve-ticket` | - |
| 2 | `sha256:fe1fc9787c495d864c5d6eada47396478572325fde1b33a96d78bf4b849b7a3e` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:comparator` | - |
| 2 | `sha256:fecaf35d692bd3d33d1a065648258d12e393afa9055d78adf6e57b42f4142f6d` | `RETAIN_EXTERNAL` | `DECIDED` | `AGENT:openai` | - |

## First-Party Name Collisions

Canonical primitives should not reuse first-party OpenAI/Anthropic names unless an intentional replacement is recorded.

No canonical primitive currently shares a type/name with a first-party installed or system source.

## First-Party Raw Digest Matches

Canonical primitives should not have identical content digests to first-party OpenAI/Anthropic sources.

No canonical primitive currently has the same digest as a first-party installed or system source.

## Cleanup Ledger

Ledger entries are the cleanup authority. `PROPOSED` entries are review records only; they do not authorize deletion or symlink writes.

| Decision | Count |
|---|---:|
| `REMOVE_BROKEN_SYMLINK` | 13 |
| `REPLACE_WITH_SYMLINK` | 33 |

| Status | Count |
|---|---:|
| `EXECUTED` | 33 |
| `PROPOSED` | 13 |

| Status | Decision | Source | Path | Canonical | Evidence |
|---|---|---|---|---|---|
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `agent-profile-authoring` | `skills/agent-profile-authoring` | `sha256:1082a610e1b83155187bb9d1538a7b12631e93329fc95d95094f74f579bd72ad` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `github-ci-operations` | `skills/github-ci-operations` | `sha256:88fc2c00d20207399b75db33be2b67e7ae8e4ec717511c008ac0825909a33603` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `hook-primitive-authoring` | `skills/hook-primitive-authoring` | `sha256:467af02e05c8421bbdc9761ff1731aaff72d5e489389e5b866fafd025cf84100` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `local-repository-navigation` | `skills/local-repository-navigation` | `sha256:ac83ea1916cdcab2c49e2ee63a8bca81c0fbafcf56db22c5bc0202d8f0b276d8` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `plugin-composition-authoring` | `skills/plugin-composition-authoring` | `sha256:6a8090018485921007cdbd34d892a1b7d2c842e58c2f911ed3ba299c24f8c7c3` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `primitive-quality-audit` | `skills/primitive-quality-audit` | `sha256:7047d5b9cc667230d64172f73819df89c44037ea298302a17f52ed47d6e082f4` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `primitive-routing-evaluation` | `skills/primitive-routing-evaluation` | `sha256:c7a9697567dfe4442abe481162aae4f5724b108db671b41586bbc38d30f62e7a` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `repository-signature-indexing` | `skills/repository-signature-indexing` | `sha256:8494dd6b59ead010e7f071cb22d5c85d818842801dcc7494f7e443157db83a73` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `runtime-linking` | `skills/runtime-linking` | `sha256:3717e49963fb909cd5c4a62a2c21dbfe57939baa26e0ad51a0c89a4c614d1861` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `shell-script-safety` | `skills/shell-script-safety` | `sha256:fee78b7f191c58520722e4ecc99cdd31ed9b668f5c4c4dba68a1993d8c361a1f` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `site-docs-authoring` | `skills/site-docs-authoring` | `sha256:3d4683e1792a3ec3b33809425e80f0af61c1be1a49ce789423612b0aeeeffb00` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `skill-primitive-authoring` | `skills/skill-primitive-authoring` | `sha256:e24ce9f217d02e7c5daf989e097d2c4188cea686fdbee2639bb8afc11c29e905` |
| `PROPOSED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `source-graph-consolidation` | `skills/source-graph-consolidation` | `sha256:8c274af0389f6267860d1a5450834181babd28701897ae8b400290f2f29a7cbf` |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `claude-agents` | `code-documentation-generator.md` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `claude-agents` | `codebase-uplift-agent.md` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `claude-agents` | `kotlin-idiom-forge.agent.md` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `claude-agents` | `prompt-enhancer.md` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-agent-skills` | `design-an-interface` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-agent-skills` | `karpathy-guidelines` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-agent-skills` | `kast` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-codex-backups` | `find-skills` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-codex-skills-backup` | `arch-review` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-codex-skills-backup` | `documentation` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-codex-skills-backup` | `segment-codebase-agents-md` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-codex-skills-backup` | `tdd` | - | - |
| `EXECUTED` | `REMOVE_BROKEN_SYMLINK` | `global-codex-skills-backup` | `yeet` | - | - |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `define-goal` | `skills/define-goal` | `sha256:e988680dcf9a18be5e5b0891b959a76d3e3889f0c1b56620f50e4c00306dc169` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `doc-coauthoring` | `skills/reference-doc-workflow` | `sha256:fd8325cb199c45d53792e9fbc144d72832900c77aa31bc3b781099d5cbe81266` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `kotlin-gradle-loop` | `skills/kotlin-gradle-validation` | `sha256:c16057f6fd84c37cc1474c821e28717dcf263d288e6272941b80833f8086dd03` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `kotlin-standards` | `skills/kotlin-standards` | `sha256:a93c1a3a9379a8d154c28f625eb8eb66c59bfb31e7407b9f6c34bb5fa4a6f3d8` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `segment-codebase-agents-md` | `skills/repo-instruction-topology` | `sha256:c0c6aea845e03bead9593879d44db6628ed9da7f9b77aba706e2ad0f2459e475` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `apollo-skills` | `yeet` | `skills/git-change-flow` | `sha256:5892fe0117b49ea1832157035bf4cd6615208d0929b1f2fa28a8e2256e868fcf` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-agents` | `kotlin-boundary-contract-reviewer.agent.md` | `agents/kotlin-review/kotlin-boundary-contract-reviewer.agent.md` | `sha256:47428c1826d5ed716bb2bda9457f9eee78fb22183ddbf2626a53c8478e347a60` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-agents` | `kotlin-package-cohesion-reviewer.agent.md` | `agents/kotlin-review/kotlin-package-cohesion-reviewer.agent.md` | `sha256:a9383c5b37ccbb5f8d560c71b7e6fd6d906f3ff347d12e58c759cd6dd4879f07` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-agents` | `kotlin-review-captain.agent.md` | `agents/kotlin-review/kotlin-review-captain.agent.md` | `sha256:b1092a1c89e766b106b27ebff911376c9b3610e6ebbaa7a00725bb51b19fd712` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-agents` | `kotlin-type-safety-reviewer.agent.md` | `agents/kotlin-review/kotlin-type-safety-reviewer.agent.md` | `sha256:d902b3c5b6c94bd89c1aa3e9ce4ad48041336dfe33b3c3362be1f0c62c95d948` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-agents` | `schema-type-enforcer.agent.md` | `agents/schema-type-enforcer.agent.md` | `sha256:7d9b375234bfb426feabd6bfce62ae3cd0ed60de7985198cc3e45c9ba78efea6` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-hooks` | `copilot/kotlin-horizontalization-check.py` | `hooks/kotlin-horizontalization-check.py` | `sha256:7d40494fc877f233f7a45e92550ba34a28fe6fd7684fcd935c22a4a5f4e45836` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-instructions` | `schema-driven-design.md` | `concepts/schema-driven-design/core.md` | `sha256:6e8049024c7fd7bc24d8a04d19ab45a8a7a41f63045529074e1b1f9cf7aca975` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-instructions` | `type-safety.md` | `concepts/type-safety/core.md` | `sha256:e691dc8fc80c8e5b92b44988ca72a5a4a45d79633be080a9aa0ca4b95b13d906` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-skills` | `kotlin-standards` | `skills/kotlin-standards` | `sha256:0628e172d411987f024ec68c05bdc94343fef16a712074e1203ab2b6fee7b318` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-skills` | `manage-json-schemas` | `skills/manage-json-schemas` | `sha256:ca24fb7ba2d80085b7c2f9dc099cad26539841dddd50525578419fd3e52bdc8c` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `examplar-skills` | `tdd` | `skills/tdd` | `sha256:761f8af110477436cd7fad9552170529d9f90dfc6633bb5416fa7f027b11b06f` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `global-agent-skills` | `kotlin-standards` | `skills/kotlin-standards` | `sha256:fed19b2d040c61b3276a543943faf350ba62d79b7ab717e14670a36789ab69af` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `global-agent-skills` | `manage-json-schemas` | `skills/manage-json-schemas` | `sha256:014412957414e464f9f80f50d19ab0905999ed002f0908fac64270bd4b749514` |
| `EXECUTED` | `REPLACE_WITH_SYMLINK` | `global-agent-skills` | `tdd` | `skills/tdd` | `sha256:d5826cecfb40ba4616d23eceb721b5c97a55ab36777c5cc5288094488f3bbbc6` |

## Broken Symlinks

Broken symlinks are cleanup candidates, but they still need ledger entries before removal.

No broken symlinks currently found.
