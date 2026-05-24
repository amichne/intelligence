# Runtime Activation Plan

This generated plan is dry-run evidence. It lists future activation operations but does not authorize runtime mutation.

## Summary

| Measure | Value |
|---|---:|
| Mode | `DRY_RUN_ONLY` |
| Total operations | 18 |
| Waiting approval | 17 |
| Waiting review | 0 |
| Ready for manual import | 1 |
| Runtime mutation allowed | `false` |

## Operations

| Status | Type | Name | Source | Target |
|---|---|---|---|---|
| `WAITING_APPROVAL` | `ACTIVATE_RUNTIME_LINK` | `agent-skill-children` | `/Users/amichne/code/intelligence/skills` | `/Users/amichne/.agents/skills` |
| `WAITING_APPROVAL` | `ACTIVATE_RUNTIME_LINK` | `claude-agent-children` | `/Users/amichne/code/intelligence/agents` | `/Users/amichne/.claude/agents` |
| `WAITING_APPROVAL` | `ACTIVATE_RUNTIME_LINK` | `codex-hook-adapters` | `/Users/amichne/code/intelligence/hooks/codex` | `/Users/amichne/.codex/hooks` |
| `WAITING_APPROVAL` | `ACTIVATE_RUNTIME_LINK` | `codex-skill-children` | `/Users/amichne/code/intelligence/skills` | `/Users/amichne/.codex/skills` |
| `READY_FOR_MANUAL_IMPORT` | `IMPORT_MARKETPLACE` | `codex-app-marketplace` | `/Users/amichne/code/intelligence/marketplace.json` | `codex://plugins?marketplacePath=/Users/amichne/code/intelligence/marketplace.json` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-agent-profile-authoring` | `/Users/amichne/code/intelligence/skills/agent-profile-authoring` | `/Users/amichne/code/apollo/skills/agent-profile-authoring` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-github-ci-operations` | `/Users/amichne/code/intelligence/skills/github-ci-operations` | `/Users/amichne/code/apollo/skills/github-ci-operations` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-hook-primitive-authoring` | `/Users/amichne/code/intelligence/skills/hook-primitive-authoring` | `/Users/amichne/code/apollo/skills/hook-primitive-authoring` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-local-repository-navigation` | `/Users/amichne/code/intelligence/skills/local-repository-navigation` | `/Users/amichne/code/apollo/skills/local-repository-navigation` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-plugin-composition-authoring` | `/Users/amichne/code/intelligence/skills/plugin-composition-authoring` | `/Users/amichne/code/apollo/skills/plugin-composition-authoring` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-primitive-quality-audit` | `/Users/amichne/code/intelligence/skills/primitive-quality-audit` | `/Users/amichne/code/apollo/skills/primitive-quality-audit` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-primitive-routing-evaluation` | `/Users/amichne/code/intelligence/skills/primitive-routing-evaluation` | `/Users/amichne/code/apollo/skills/primitive-routing-evaluation` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-repository-signature-indexing` | `/Users/amichne/code/intelligence/skills/repository-signature-indexing` | `/Users/amichne/code/apollo/skills/repository-signature-indexing` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-runtime-linking` | `/Users/amichne/code/intelligence/skills/runtime-linking` | `/Users/amichne/code/apollo/skills/runtime-linking` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-shell-script-safety` | `/Users/amichne/code/intelligence/skills/shell-script-safety` | `/Users/amichne/code/apollo/skills/shell-script-safety` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-site-docs-authoring` | `/Users/amichne/code/intelligence/skills/site-docs-authoring` | `/Users/amichne/code/apollo/skills/site-docs-authoring` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-skill-primitive-authoring` | `/Users/amichne/code/intelligence/skills/skill-primitive-authoring` | `/Users/amichne/code/apollo/skills/skill-primitive-authoring` |
| `WAITING_APPROVAL` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-source-graph-consolidation` | `/Users/amichne/code/intelligence/skills/source-graph-consolidation` | `/Users/amichne/code/apollo/skills/source-graph-consolidation` |

## Step Preview

### `agent-skill-children`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/runtime-links.json`

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/skills`
1. Review target directory before linking children: `ls -la /Users/amichne/.agents/skills 2>/dev/null || true`
1. Prepare target directory after approval if needed: `mkdir -p /Users/amichne/.agents/skills`
1. Link eligible primitive children only after approval: `find /Users/amichne/code/intelligence/skills -mindepth 1 -maxdepth 1 -type d -exec sh -c 'test -f "$1/SKILL.md" && echo ln -s "$1" "$2/$(basename "$1")"' sh '{}' /Users/amichne/.agents/skills ';'`

Primitive types: `SKILL`

### `claude-agent-children`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/runtime-links.json`

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/agents`
1. Review target directory before linking children: `ls -la /Users/amichne/.claude/agents 2>/dev/null || true`
1. Prepare target directory after approval if needed: `mkdir -p /Users/amichne/.claude/agents`
1. Link eligible primitive children only after approval: `find /Users/amichne/code/intelligence/agents -mindepth 1 -maxdepth 1 \( -type d -o -name '*.agent.md' \) ! -name AGENTS.md -exec sh -c 'echo ln -s "$1" "$2/$(basename "$1")"' sh '{}' /Users/amichne/.claude/agents ';'`

Primitive types: `AGENT`

### `codex-hook-adapters`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/runtime-links.json`

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/hooks/codex`
1. Review target directory before linking children: `ls -la /Users/amichne/.codex/hooks 2>/dev/null || true`
1. Prepare target directory after approval if needed: `mkdir -p /Users/amichne/.codex/hooks`
1. Link eligible primitive children only after approval: `find /Users/amichne/code/intelligence/hooks/codex -mindepth 1 -maxdepth 1 -type f -name '*.hooks.json' -exec sh -c 'echo ln -s "$1" "$2/$(basename "$1")"' sh '{}' /Users/amichne/.codex/hooks ';'`

Primitive types: `HOOK`

### `codex-skill-children`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/runtime-links.json`

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/skills`
1. Review target directory before linking children: `ls -la /Users/amichne/.codex/skills 2>/dev/null || true`
1. Prepare target directory after approval if needed: `mkdir -p /Users/amichne/.codex/skills`
1. Link eligible primitive children only after approval: `find /Users/amichne/code/intelligence/skills -mindepth 1 -maxdepth 1 -type d -exec sh -c 'test -f "$1/SKILL.md" && echo ln -s "$1" "$2/$(basename "$1")"' sh '{}' /Users/amichne/.codex/skills ';'`

Primitive types: `SKILL`

### `codex-app-marketplace`

- Status: `READY_FOR_MANUAL_IMPORT`
- Evidence: `manifests/runtime-links.json`

1. Verify marketplace catalog exists: `test -e /Users/amichne/code/intelligence/marketplace.json`
1. Open marketplace import URL manually after approval: `open 'codex://plugins?marketplacePath=/Users/amichne/code/intelligence/marketplace.json'`

Primitive types: `PLUGIN, SKILL, AGENT, HOOK, INSTRUCTION`

### `replace-apollo-skills-agent-profile-authoring`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/agent-profile-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/agent-profile-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/agent-profile-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/agent-profile-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/agent-profile-authoring /Users/amichne/code/apollo/skills/agent-profile-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/agent-profile-authoring`

### `replace-apollo-skills-github-ci-operations`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/github-ci-operations`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/github-ci-operations`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/github-ci-operations /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/github-ci-operations`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/github-ci-operations /Users/amichne/code/apollo/skills/github-ci-operations`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/github-ci-operations`

### `replace-apollo-skills-hook-primitive-authoring`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/hook-primitive-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/hook-primitive-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/hook-primitive-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/hook-primitive-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/hook-primitive-authoring /Users/amichne/code/apollo/skills/hook-primitive-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/hook-primitive-authoring`

### `replace-apollo-skills-local-repository-navigation`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/local-repository-navigation`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/local-repository-navigation`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/local-repository-navigation /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/local-repository-navigation`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/local-repository-navigation /Users/amichne/code/apollo/skills/local-repository-navigation`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/local-repository-navigation`

### `replace-apollo-skills-plugin-composition-authoring`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/plugin-composition-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/plugin-composition-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/plugin-composition-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/plugin-composition-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/plugin-composition-authoring /Users/amichne/code/apollo/skills/plugin-composition-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/plugin-composition-authoring`

### `replace-apollo-skills-primitive-quality-audit`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/primitive-quality-audit`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/primitive-quality-audit`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/primitive-quality-audit /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/primitive-quality-audit`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/primitive-quality-audit /Users/amichne/code/apollo/skills/primitive-quality-audit`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/primitive-quality-audit`

### `replace-apollo-skills-primitive-routing-evaluation`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/primitive-routing-evaluation`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/primitive-routing-evaluation`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/primitive-routing-evaluation /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/primitive-routing-evaluation`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/primitive-routing-evaluation /Users/amichne/code/apollo/skills/primitive-routing-evaluation`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/primitive-routing-evaluation`

### `replace-apollo-skills-repository-signature-indexing`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/repository-signature-indexing`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/repository-signature-indexing`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/repository-signature-indexing /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/repository-signature-indexing`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/repository-signature-indexing /Users/amichne/code/apollo/skills/repository-signature-indexing`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/repository-signature-indexing`

### `replace-apollo-skills-runtime-linking`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/runtime-linking`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/runtime-linking`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/runtime-linking /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/runtime-linking`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/runtime-linking /Users/amichne/code/apollo/skills/runtime-linking`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/runtime-linking`

### `replace-apollo-skills-shell-script-safety`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/shell-script-safety`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/shell-script-safety`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/shell-script-safety /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/shell-script-safety`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/shell-script-safety /Users/amichne/code/apollo/skills/shell-script-safety`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/shell-script-safety`

### `replace-apollo-skills-site-docs-authoring`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/site-docs-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/site-docs-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/site-docs-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/site-docs-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/site-docs-authoring /Users/amichne/code/apollo/skills/site-docs-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/site-docs-authoring`

### `replace-apollo-skills-skill-primitive-authoring`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/skill-primitive-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/skill-primitive-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/skill-primitive-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/skill-primitive-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/skill-primitive-authoring /Users/amichne/code/apollo/skills/skill-primitive-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/skill-primitive-authoring`

### `replace-apollo-skills-source-graph-consolidation`

- Status: `WAITING_APPROVAL`
- Evidence: `manifests/cleanup-ledger.json`

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/source-graph-consolidation`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/source-graph-consolidation`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/source-graph-consolidation /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/source-graph-consolidation`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/source-graph-consolidation /Users/amichne/code/apollo/skills/source-graph-consolidation`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/source-graph-consolidation`
