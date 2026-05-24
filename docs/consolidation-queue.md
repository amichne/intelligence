# Consolidation Queue

Inventory source: `manifests/discovered-primitives.json`
Inventory generated at: `2026-05-24T05:01:18.761002+00:00`

## Counts

| Bucket | Count |
|---|---:|
| `authored-local` | 76 |
| `backup` | 33 |
| `canonical` | 8 |
| `installed-marketplace` | 608 |
| `runtime` | 49 |

| Type | Count |
|---|---:|
| `AGENT` | 296 |
| `HOOK` | 35 |
| `INSTRUCTION` | 8 |
| `PLUGIN` | 134 |
| `SKILL` | 301 |

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
| `AGENT` | `kotlin-package-cohesion-reviewer` | `authored-local` | `examplar-agents` | `kotlin-package-cohesion-reviewer.agent.md` | `REVIEW_PROMOTE` |
| `AGENT` | `kotlin-review-captain` | `authored-local` | `examplar-agents` | `kotlin-review-captain.agent.md` | `REVIEW_PROMOTE` |
| `AGENT` | `kotlin-type-safety-reviewer` | `authored-local` | `examplar-agents` | `kotlin-type-safety-reviewer.agent.md` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/imagegen/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/openai-docs/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/plugin-creator/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/skill-creator/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `.system/skill-installer/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-agents` | `codebase-navigator/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `define-goal/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `jira-resolve-ticket/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `kast-agent-skills` | `llm-wiki/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `examplar-skills` | `manage-json-schemas/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `segment-codebase-agents-md/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `openai` | `authored-local` | `apollo-skills` | `yeet/agents/openai.yaml` | `REVIEW_PROMOTE` |
| `AGENT` | `page-patterns` | `authored-local` | `kast-agent-skills` | `llm-wiki/references/page-patterns.md` | `REVIEW_PROMOTE` |
| `AGENT` | `process-outdated-prompt` | `authored-local` | `apollo-agents` | `codebase-navigator/copilot/prompts/process-outdated.prompt.md` | `REVIEW_PROMOTE` |
| `AGENT` | `quickstart` | `authored-local` | `kast-agent-skills` | `kast/references/quickstart.md` | `REVIEW_PROMOTE` |
| `AGENT` | `routing-improvement` | `authored-local` | `kast-agent-skills` | `kast/fixtures/maintenance/references/routing-improvement.md` | `REVIEW_PROMOTE` |
| `AGENT` | `routing-improvement` | `authored-local` | `kast-agent-skills` | `kast/references/routing-improvement.md` | `REVIEW_PROMOTE` |
| `AGENT` | `schema-type-enforcer` | `authored-local` | `examplar-agents` | `schema-type-enforcer.agent.md` | `REVIEW_PROMOTE` |
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
| `HOOK` | `record-paths` | `authored-local` | `kast-github-hooks` | `record-paths.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `require-skills` | `authored-local` | `kast-github-hooks` | `require-skills.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `resolve-kast-path` | `authored-local` | `kast-github-hooks` | `resolve-kast-path.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `session-end` | `authored-local` | `kast-github-hooks` | `session-end.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `session-start` | `authored-local` | `kast-github-hooks` | `session-start.sh` | `REVIEW_PROMOTE` |
| `HOOK` | `skill-shadowing` | `authored-local` | `kast-github-hooks` | `skill-shadowing.json` | `REVIEW_PROMOTE` |
| `INSTRUCTION` | `concepts-agents-instructions` | `canonical` | `intelligence` | `plugouts/concepts/AGENTS.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `instructions-agents-instructions` | `authored-local` | `examplar-instructions` | `AGENTS.md` | `REVIEW_PROMOTE` |
| `INSTRUCTION` | `intelligence-agents-instructions` | `canonical` | `intelligence` | `AGENTS.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `schema-driven-design` | `authored-local` | `examplar-instructions` | `schema-driven-design.md` | `REVIEW_PROMOTE` |
| `INSTRUCTION` | `schema-driven-design` | `canonical` | `intelligence` | `plugouts/concepts/schema-driven-design/core.md` | `KEEP_CANONICAL` |
| `INSTRUCTION` | `type-safety` | `authored-local` | `examplar-instructions` | `type-safety.md` | `REVIEW_PROMOTE` |
| `INSTRUCTION` | `type-safety` | `canonical` | `intelligence` | `plugouts/concepts/type-safety/core.md` | `KEEP_CANONICAL` |
| `PLUGIN` | `agentic-coding` | `authored-local` | `examplar-plugins` | `agentic-coding` | `REVIEW_PROMOTE` |
| `PLUGIN` | `intelligence-core` | `canonical` | `intelligence` | `plugins/intelligence-core` | `KEEP_CANONICAL` |
| `PLUGIN` | `plugin-eval` | `authored-local` | `examplar-plugins` | `plugin-eval` | `REVIEW_PROMOTE` |
| `SKILL` | `agent-development` | `authored-local` | `kast-agent-skills` | `agent-development` | `REVIEW_PROMOTE` |
| `SKILL` | `define-goal` | `authored-local` | `apollo-skills` | `define-goal` | `REVIEW_PROMOTE` |
| `SKILL` | `doc-coauthoring` | `authored-local` | `apollo-skills` | `doc-coauthoring` | `REVIEW_PROMOTE` |
| `SKILL` | `imagegen` | `authored-local` | `apollo-skills` | `.system/imagegen` | `REVIEW_PROMOTE` |
| `SKILL` | `jira-resolve-ticket` | `authored-local` | `apollo-skills` | `jira-resolve-ticket` | `REVIEW_PROMOTE` |
| `SKILL` | `kast` | `authored-local` | `kast-agent-skills` | `kast` | `REVIEW_PROMOTE` |
| `SKILL` | `kotlin-gradle-loop` | `authored-local` | `apollo-skills` | `kotlin-gradle-loop` | `REVIEW_PROMOTE` |
| `SKILL` | `kotlin-standards` | `authored-local` | `apollo-skills` | `kotlin-standards` | `REVIEW_PROMOTE` |
| `SKILL` | `kotlin-standards` | `authored-local` | `examplar-skills` | `kotlin-standards` | `REVIEW_PROMOTE` |
| `SKILL` | `llm-wiki` | `authored-local` | `kast-agent-skills` | `llm-wiki` | `REVIEW_PROMOTE` |
| `SKILL` | `manage-json-schemas` | `authored-local` | `examplar-skills` | `manage-json-schemas` | `REVIEW_PROMOTE` |
| `SKILL` | `openai-docs` | `authored-local` | `apollo-skills` | `.system/openai-docs` | `REVIEW_PROMOTE` |
| `SKILL` | `plugin-creator` | `authored-local` | `apollo-skills` | `.system/plugin-creator` | `REVIEW_PROMOTE` |
| `SKILL` | `refresh-affected-agents` | `authored-local` | `kast-agent-skills` | `refresh-affected-agents` | `REVIEW_PROMOTE` |
| `SKILL` | `segment-codebase-agents-md` | `authored-local` | `apollo-skills` | `segment-codebase-agents-md` | `REVIEW_PROMOTE` |
| `SKILL` | `skill-creator` | `authored-local` | `apollo-skills` | `.system/skill-creator` | `REVIEW_PROMOTE` |
| | | | | plus 4 more in the JSON report | |

## Name Review Queue

Name collisions need semantic review unless every entry has the same digest.

| Priority | Type | Name | Action | Buckets | Entries |
|---:|---|---|---|---|---:|
| 1 | `AGENT` | `routing-improvement` | `SYNTHESIZE` | `authored-local` | 2 |
| 1 | `HOOK` | `agents-md-turn-refresh` | `SYNTHESIZE` | `canonical` | 3 |
| 1 | `HOOK` | `hooks` | `SYNTHESIZE` | `authored-local, installed-marketplace` | 11 |
| 1 | `INSTRUCTION` | `schema-driven-design` | `SYNTHESIZE` | `authored-local, canonical` | 2 |
| 1 | `INSTRUCTION` | `type-safety` | `SYNTHESIZE` | `authored-local, canonical` | 2 |
| 1 | `PLUGIN` | `plugin-eval` | `SYNTHESIZE` | `authored-local, installed-marketplace` | 2 |
| 1 | `SKILL` | `agent-development` | `SYNTHESIZE` | `authored-local, installed-marketplace` | 2 |
| 1 | `SKILL` | `bash-defensive-patterns` | `SYNTHESIZE` | `backup, installed-marketplace` | 2 |
| 1 | `SKILL` | `gh-fix-ci` | `SYNTHESIZE` | `backup, runtime` | 3 |
| 1 | `SKILL` | `llm-native-signature-spec` | `SYNTHESIZE` | `backup` | 2 |
| 2 | `AGENT` | `analyzer` | `DEDUP_IDENTICAL_NAME` | `authored-local, installed-marketplace, runtime` | 5 |
| 2 | `AGENT` | `comparator` | `DEDUP_IDENTICAL_NAME` | `authored-local, installed-marketplace, runtime` | 5 |
| 2 | `AGENT` | `grader` | `DEDUP_IDENTICAL_NAME` | `authored-local, installed-marketplace, runtime` | 5 |
| 2 | `AGENT` | `openai` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, runtime` | 33 |
| 2 | `SKILL` | `define-goal` | `DEDUP_IDENTICAL_NAME` | `authored-local, runtime` | 2 |
| 2 | `SKILL` | `doc-coauthoring` | `DEDUP_IDENTICAL_NAME` | `authored-local, installed-marketplace, runtime` | 3 |
| 2 | `SKILL` | `imagegen` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, runtime` | 3 |
| 2 | `SKILL` | `jira-resolve-ticket` | `DEDUP_IDENTICAL_NAME` | `authored-local, runtime` | 2 |
| 2 | `SKILL` | `kotlin-architect` | `DEDUP_IDENTICAL_NAME` | `backup` | 2 |
| 2 | `SKILL` | `kotlin-gradle-loop` | `DEDUP_IDENTICAL_NAME` | `authored-local, runtime` | 2 |
| 2 | `SKILL` | `kotlin-standards` | `RECONCILE_RUNTIME_COPY` | `authored-local, runtime` | 4 |
| 2 | `SKILL` | `manage-json-schemas` | `RECONCILE_RUNTIME_COPY` | `authored-local, runtime` | 2 |
| 2 | `SKILL` | `openai-docs` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, runtime` | 4 |
| 2 | `SKILL` | `plugin-creator` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, runtime` | 3 |
| 2 | `SKILL` | `segment-codebase-agents-md` | `DEDUP_IDENTICAL_NAME` | `authored-local, runtime` | 2 |
| 2 | `SKILL` | `skill-creator` | `RECONCILE_RUNTIME_COPY` | `authored-local, backup, installed-marketplace, runtime` | 9 |
| 2 | `SKILL` | `skill-installer` | `DEDUP_IDENTICAL_NAME` | `authored-local, backup, runtime` | 4 |
| 2 | `SKILL` | `tdd` | `RECONCILE_RUNTIME_COPY` | `authored-local, runtime` | 2 |
| 2 | `SKILL` | `web-artifacts-builder` | `DEDUP_IDENTICAL_NAME` | `installed-marketplace, runtime` | 2 |
| 2 | `SKILL` | `yeet` | `DEDUP_IDENTICAL_NAME` | `authored-local, runtime` | 2 |

## Digest Review Queue

Digest duplicates are candidates for symlink/reference replacement after promotion is verified.

| Priority | Entries | Buckets | Digest |
|---:|---:|---|---|
| 2 | 2 | `authored-local, runtime` | `sha256:03f8f25b25be6267c15f42a87c15caf54fe0863d40ebf90104c4a44e93a1864c` |
| 2 | 4 | `authored-local, backup, runtime` | `sha256:104b792090370aff47c4a70411cb84e737b7cfa9699f2f54c916d8946fc98f16` |
| 2 | 2 | `authored-local, runtime` | `sha256:3555f7bdb3a50a4ccb99be8d94d23528509b988f3f72645e03705da25594b66c` |
| 2 | 5 | `authored-local, installed-marketplace, runtime` | `sha256:57134da0c1a4eea33fbd74a1c9c44aa814f07d6bc64de303edb586f941e5d21a` |
| 2 | 2 | `authored-local, runtime` | `sha256:580081dd6c0896d72307d1f1b90d2248a64b9353bc546fdbf05ddeb3848e8c39` |
| 2 | 2 | `authored-local, runtime` | `sha256:5892fe0117b49ea1832157035bf4cd6615208d0929b1f2fa28a8e2256e868fcf` |
| 2 | 4 | `authored-local, backup, runtime` | `sha256:5ce223d8b1070b82c42298538f1b8d376f788eb9e7a42a987e8c094070d73f0e` |
| 2 | 4 | `authored-local, backup, runtime` | `sha256:67717a8afb51f29380e849cad62036628bd5a3c8193bacf3b00b5cd7a30fc1cb` |
| 2 | 2 | `authored-local, runtime` | `sha256:6f0a463b53232932ad70aec529e25fad9d54312de0bd29d64a38bb14a4e748de` |
| 2 | 2 | `authored-local, runtime` | `sha256:71bc91120548c46092abb98dba5ef2d91b12224d1106d6abfdc57862a6e26683` |
| 2 | 2 | `authored-local, runtime` | `sha256:96e60e84b8210c74a26762f66073519f516da8d346bd0b535d1ca170c1a29f0d` |
| 2 | 2 | `authored-local, runtime` | `sha256:9ca574af14580dc7a2a3dc37a1796d17f93cb8850be66501f0799ef8603e9dc0` |
| 2 | 2 | `authored-local, runtime` | `sha256:9d870a5d594130261173d7a4bd3fd9221a71eff34c347a7c782da5046fd23cb9` |
| 2 | 2 | `authored-local, runtime` | `sha256:a93c1a3a9379a8d154c28f625eb8eb66c59bfb31e7407b9f6c34bb5fa4a6f3d8` |
| 2 | 5 | `authored-local, installed-marketplace, runtime` | `sha256:bf68f4cac5a56c673a928c2e6d619586c5b93ea364026ab37547772cb45a663a` |
| 2 | 2 | `authored-local, runtime` | `sha256:c0c6aea845e03bead9593879d44db6628ed9da7f9b77aba706e2ad0f2459e475` |
| 2 | 2 | `authored-local, runtime` | `sha256:c16057f6fd84c37cc1474c821e28717dcf263d288e6272941b80833f8086dd03` |
| 2 | 4 | `authored-local, backup, runtime` | `sha256:d07d21b93fcf3d4dc8d9a3399c05fc226a49a333a96d3e1c68b451b8dd9eade6` |
| 2 | 2 | `authored-local, runtime` | `sha256:e988680dcf9a18be5e5b0891b959a76d3e3889f0c1b56620f50e4c00306dc169` |
| 2 | 2 | `authored-local, runtime` | `sha256:f00f09ecb7c682f9bfb09bbbc93de6242b2d10c7e4d97f059d91de82c14246c8` |
| 2 | 2 | `authored-local, runtime` | `sha256:f412bf1efc6fd541dcdd2db0b3347d6f6735899d35d1831a27ef439036c39a47` |
| 2 | 3 | `authored-local, installed-marketplace, runtime` | `sha256:fd8325cb199c45d53792e9fbc144d72832900c77aa31bc3b781099d5cbe81266` |
| 2 | 5 | `authored-local, installed-marketplace, runtime` | `sha256:fe1fc9787c495d864c5d6eada47396478572325fde1b33a96d78bf4b849b7a3e` |
| 2 | 2 | `authored-local, runtime` | `sha256:fecaf35d692bd3d33d1a065648258d12e393afa9055d78adf6e57b42f4142f6d` |

## Broken Symlinks

Broken symlinks are cleanup candidates, but they still need ledger entries before removal.

| Source | Path | Target |
|---|---|---|
| `claude-agents` | `code-documentation-generator.md` | `/Users/amichne/code/examplar/common/agents/code-documentation-generator.md` |
| `claude-agents` | `codebase-uplift-agent.md` | `/Users/amichne/code/examplar/common/agents/codebase-uplift-agent.md` |
| `claude-agents` | `kotlin-idiom-forge.agent.md` | `/Users/amichne/code/examplar/common/agents/kotlin-idiom-forge.agent.md` |
| `claude-agents` | `prompt-enhancer.md` | `/Users/amichne/code/examplar/common/agents/prompt-enhancer.md` |
| `global-agent-skills` | `design-an-interface` | `/Users/amichne/code/examplar/common/skills/design-an-interface` |
| `global-agent-skills` | `karpathy-guidelines` | `/Users/amichne/code/examplar/common/skills/karpathy-guidelines` |
| `global-agent-skills` | `kast` | `/Users/amichne/.kast/lib/skills/kast` |
| `global-codex-backups` | `find-skills` | `../../.agents/skills/find-skills` |
| `global-codex-skills-backup` | `arch-review` | `/Users/amichne/code/apollo/skills/arch-review` |
| `global-codex-skills-backup` | `documentation` | `/Users/amichne/code/examplar/common/skills/documentation` |
| `global-codex-skills-backup` | `segment-codebase-agents-md` | `/Users/amichne/code/examplar/common/skills/segment-codebase-agents-md` |
| `global-codex-skills-backup` | `tdd` | `/Users/amichne/code/examplar/common/skills/tdd` |
| `global-codex-skills-backup` | `yeet` | `/Users/amichne/code/examplar/common/skills/yeet` |
