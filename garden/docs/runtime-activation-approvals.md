# Runtime Activation Approvals

This generated queue packages dry-run activation operations for review. It does not authorize runtime mutation.

## Summary

| Measure | Value |
|---|---:|
| Source turnoff status | `NOT_READY` |
| Total packets | 18 |
| Ready for approval | 2 |
| Ready for manual import | 1 |
| Review required | 0 |
| Blocked | 0 |
| Already active | 15 |
| Runtime mutation allowed | `false` |
| Approval required | `true` |

Next action: Approve READY_FOR_APPROVAL packets by name only when the backup and symlink previews are acceptable; REVIEW_REQUIRED packets still need inspection.

## Packets

| State | Risk | Type | Name | Source | Target |
|---|---|---|---|---|---|
| `ALREADY_ACTIVE` | `MEDIUM` | `ACTIVATE_RUNTIME_LINK` | `claude-agent-children` | `/Users/amichne/code/intelligence/agents` | `/Users/amichne/.claude/agents` |
| `ALREADY_ACTIVE` | `MEDIUM` | `ACTIVATE_RUNTIME_LINK` | `codex-hook-adapters` | `/Users/amichne/code/intelligence/hooks/codex` | `/Users/amichne/.codex/hooks` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-agent-profile-authoring` | `/Users/amichne/code/intelligence/skills/agent-profile-authoring` | `/Users/amichne/code/apollo/skills/agent-profile-authoring` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-github-ci-operations` | `/Users/amichne/code/intelligence/skills/github-ci-operations` | `/Users/amichne/code/apollo/skills/github-ci-operations` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-hook-primitive-authoring` | `/Users/amichne/code/intelligence/skills/hook-primitive-authoring` | `/Users/amichne/code/apollo/skills/hook-primitive-authoring` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-local-repository-navigation` | `/Users/amichne/code/intelligence/skills/local-repository-navigation` | `/Users/amichne/code/apollo/skills/local-repository-navigation` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-plugin-composition-authoring` | `/Users/amichne/code/intelligence/skills/plugin-composition-authoring` | `/Users/amichne/code/apollo/skills/plugin-composition-authoring` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-primitive-quality-audit` | `/Users/amichne/code/intelligence/skills/primitive-quality-audit` | `/Users/amichne/code/apollo/skills/primitive-quality-audit` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-primitive-routing-evaluation` | `/Users/amichne/code/intelligence/skills/primitive-routing-evaluation` | `/Users/amichne/code/apollo/skills/primitive-routing-evaluation` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-repository-signature-indexing` | `/Users/amichne/code/intelligence/skills/repository-signature-indexing` | `/Users/amichne/code/apollo/skills/repository-signature-indexing` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-runtime-linking` | `/Users/amichne/code/intelligence/skills/runtime-linking` | `/Users/amichne/code/apollo/skills/runtime-linking` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-shell-script-safety` | `/Users/amichne/code/intelligence/skills/shell-script-safety` | `/Users/amichne/code/apollo/skills/shell-script-safety` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-site-docs-authoring` | `/Users/amichne/code/intelligence/skills/site-docs-authoring` | `/Users/amichne/code/apollo/skills/site-docs-authoring` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-skill-primitive-authoring` | `/Users/amichne/code/intelligence/skills/skill-primitive-authoring` | `/Users/amichne/code/apollo/skills/skill-primitive-authoring` |
| `ALREADY_ACTIVE` | `HIGH` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-source-graph-consolidation` | `/Users/amichne/code/intelligence/skills/source-graph-consolidation` | `/Users/amichne/code/apollo/skills/source-graph-consolidation` |
| `READY_FOR_APPROVAL` | `MEDIUM` | `ACTIVATE_RUNTIME_LINK` | `agent-skill-children` | `/Users/amichne/code/intelligence/skills` | `/Users/amichne/.agents/skills` |
| `READY_FOR_APPROVAL` | `MEDIUM` | `ACTIVATE_RUNTIME_LINK` | `codex-skill-children` | `/Users/amichne/code/intelligence/skills` | `/Users/amichne/.codex/skills` |
| `READY_FOR_MANUAL_IMPORT` | `LOW` | `IMPORT_MARKETPLACE` | `codex-app-marketplace` | `/Users/amichne/code/intelligence/marketplace.json` | `codex://plugins?marketplacePath=/Users/amichne/code/intelligence/marketplace.json` |

## Packet Details

### `claude-agent-children`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `DIRECTORY`
- Collision policy: `BACKUP_THEN_LINK`
- Runtime link strategy: `SYMLINK_CHILDREN`
- Primitive types: `AGENT`
- Evidence: `garden/manifests/runtime-links.json`
- Approval boundary: Requires explicit user approval after reviewing target contents, collision policy, and preflight reasons.
- Rollback: Remove links created during activation and restore any backed-up runtime paths before rerunning preflight.

Preflight reasons:

- Runtime target already links every source child to the canonical source.

Step preview:

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/agents`
1. Review target directory before linking children: `ls -la /Users/amichne/.claude/agents 2>/dev/null || true`
1. Prepare target directory after approval if needed: `mkdir -p /Users/amichne/.claude/agents`
1. Link eligible primitive children only after approval: `find /Users/amichne/code/intelligence/agents -mindepth 1 -maxdepth 1 \( -type d -o -name '*.agent.md' \) ! -name AGENTS.md -exec sh -c 'echo ln -s "$1" "$2/$(basename "$1")"' sh '{}' /Users/amichne/.claude/agents ';'`

### `codex-hook-adapters`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `DIRECTORY`
- Collision policy: `FAIL_IF_EXISTS`
- Runtime link strategy: `SYMLINK_CHILDREN`
- Primitive types: `HOOK`
- Evidence: `garden/manifests/runtime-links.json`
- Approval boundary: Requires explicit user approval after reviewing target contents, collision policy, and preflight reasons.
- Rollback: Remove links created during activation and restore any backed-up runtime paths before rerunning preflight.

Preflight reasons:

- Runtime target already links every source child to the canonical source.

Step preview:

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/hooks/codex`
1. Review target directory before linking children: `ls -la /Users/amichne/.codex/hooks 2>/dev/null || true`
1. Prepare target directory after approval if needed: `mkdir -p /Users/amichne/.codex/hooks`
1. Link eligible primitive children only after approval: `find /Users/amichne/code/intelligence/hooks/codex -mindepth 1 -maxdepth 1 -type f -name '*.hooks.json' -exec sh -c 'echo ln -s "$1" "$2/$(basename "$1")"' sh '{}' /Users/amichne/.codex/hooks ';'`

### `replace-apollo-skills-agent-profile-authoring`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/agent-profile-authoring`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/agent-profile-authoring; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/agent-profile-authoring and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/agent-profile-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/agent-profile-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/agent-profile-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/agent-profile-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/agent-profile-authoring /Users/amichne/code/apollo/skills/agent-profile-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/agent-profile-authoring`

### `replace-apollo-skills-github-ci-operations`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/github-ci-operations`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/github-ci-operations; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/github-ci-operations and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/github-ci-operations`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/github-ci-operations`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/github-ci-operations /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/github-ci-operations`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/github-ci-operations /Users/amichne/code/apollo/skills/github-ci-operations`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/github-ci-operations`

### `replace-apollo-skills-hook-primitive-authoring`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/hook-primitive-authoring`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/hook-primitive-authoring; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/hook-primitive-authoring and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/hook-primitive-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/hook-primitive-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/hook-primitive-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/hook-primitive-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/hook-primitive-authoring /Users/amichne/code/apollo/skills/hook-primitive-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/hook-primitive-authoring`

### `replace-apollo-skills-local-repository-navigation`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/local-repository-navigation`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/local-repository-navigation; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/local-repository-navigation and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/local-repository-navigation`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/local-repository-navigation`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/local-repository-navigation /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/local-repository-navigation`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/local-repository-navigation /Users/amichne/code/apollo/skills/local-repository-navigation`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/local-repository-navigation`

### `replace-apollo-skills-plugin-composition-authoring`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/plugin-composition-authoring`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/plugin-composition-authoring; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/plugin-composition-authoring and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/plugin-composition-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/plugin-composition-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/plugin-composition-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/plugin-composition-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/plugin-composition-authoring /Users/amichne/code/apollo/skills/plugin-composition-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/plugin-composition-authoring`

### `replace-apollo-skills-primitive-quality-audit`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/primitive-quality-audit`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/primitive-quality-audit; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/primitive-quality-audit and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/primitive-quality-audit`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/primitive-quality-audit`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/primitive-quality-audit /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/primitive-quality-audit`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/primitive-quality-audit /Users/amichne/code/apollo/skills/primitive-quality-audit`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/primitive-quality-audit`

### `replace-apollo-skills-primitive-routing-evaluation`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/primitive-routing-evaluation`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/primitive-routing-evaluation; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/primitive-routing-evaluation and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/primitive-routing-evaluation`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/primitive-routing-evaluation`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/primitive-routing-evaluation /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/primitive-routing-evaluation`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/primitive-routing-evaluation /Users/amichne/code/apollo/skills/primitive-routing-evaluation`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/primitive-routing-evaluation`

### `replace-apollo-skills-repository-signature-indexing`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/repository-signature-indexing`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/repository-signature-indexing; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/repository-signature-indexing and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/repository-signature-indexing`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/repository-signature-indexing`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/repository-signature-indexing /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/repository-signature-indexing`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/repository-signature-indexing /Users/amichne/code/apollo/skills/repository-signature-indexing`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/repository-signature-indexing`

### `replace-apollo-skills-runtime-linking`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/runtime-linking`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/runtime-linking; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/runtime-linking and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/runtime-linking`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/runtime-linking`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/runtime-linking /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/runtime-linking`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/runtime-linking /Users/amichne/code/apollo/skills/runtime-linking`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/runtime-linking`

### `replace-apollo-skills-shell-script-safety`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/shell-script-safety`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/shell-script-safety; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/shell-script-safety and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/shell-script-safety`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/shell-script-safety`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/shell-script-safety /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/shell-script-safety`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/shell-script-safety /Users/amichne/code/apollo/skills/shell-script-safety`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/shell-script-safety`

### `replace-apollo-skills-site-docs-authoring`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/site-docs-authoring`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/site-docs-authoring; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/site-docs-authoring and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/site-docs-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/site-docs-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/site-docs-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/site-docs-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/site-docs-authoring /Users/amichne/code/apollo/skills/site-docs-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/site-docs-authoring`

### `replace-apollo-skills-skill-primitive-authoring`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/skill-primitive-authoring`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/skill-primitive-authoring; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/skill-primitive-authoring and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/skill-primitive-authoring`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/skill-primitive-authoring`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/skill-primitive-authoring /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/skill-primitive-authoring`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/skill-primitive-authoring /Users/amichne/code/apollo/skills/skill-primitive-authoring`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/skill-primitive-authoring`

### `replace-apollo-skills-source-graph-consolidation`

- State: `ALREADY_ACTIVE`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `ALREADY_ACTIVE`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Backup path: `/Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/source-graph-consolidation`
- Backup kind: `MISSING`
- Evidence: `garden/manifests/cleanup-ledger.json`
- Approval boundary: Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes.
- Rollback: No rollback is needed until execution. Before replacing this path, preserve the original at .migration-backups/source-turnoff/apollo-skills/source-graph-consolidation; after execution, rollback by removing the symlink at /Users/amichne/code/apollo/skills/source-graph-consolidation and moving that backup back to the original path.

Preflight reasons:

- Replacement target already resolves to the canonical source.

Step preview:

1. Verify canonical source exists: `test -e /Users/amichne/code/intelligence/skills/source-graph-consolidation`
1. Verify current source exists before backup: `test -e /Users/amichne/code/apollo/skills/source-graph-consolidation`
1. Prepare backup directory: `mkdir -p /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills`
1. Back up current source path: `mv /Users/amichne/code/apollo/skills/source-graph-consolidation /Users/amichne/code/intelligence/.migration-backups/source-turnoff/apollo-skills/source-graph-consolidation`
1. Link source path to canonical primitive: `ln -s /Users/amichne/code/intelligence/skills/source-graph-consolidation /Users/amichne/code/apollo/skills/source-graph-consolidation`
1. Verify symlink target: `readlink /Users/amichne/code/apollo/skills/source-graph-consolidation`

### `agent-skill-children`

- State: `READY_FOR_APPROVAL`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `READY_FOR_APPROVAL`
- Source kind: `DIRECTORY`
- Target kind: `DIRECTORY`
- Collision policy: `BACKUP_THEN_LINK`
- Runtime link strategy: `SYMLINK_CHILDREN`
- Primitive types: `SKILL`
- Evidence: `garden/manifests/runtime-links.json`
- Approval boundary: Requires explicit user approval after reviewing target contents, collision policy, and preflight reasons.
- Rollback: Remove links created during activation and restore any backed-up runtime paths before rerunning preflight.

Preflight reasons:

- Runtime source and target directory exist; approval is still required before linking children.

Step preview:

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/skills`
1. Review target directory before linking children: `ls -la /Users/amichne/.agents/skills 2>/dev/null || true`
1. Prepare target directory after approval if needed: `mkdir -p /Users/amichne/.agents/skills`
1. Link eligible primitive children only after approval: `find /Users/amichne/code/intelligence/skills -mindepth 1 -maxdepth 1 -type d -exec sh -c 'test -f "$1/SKILL.md" && echo ln -s "$1" "$2/$(basename "$1")"' sh '{}' /Users/amichne/.agents/skills ';'`

### `codex-skill-children`

- State: `READY_FOR_APPROVAL`
- Plan status: `WAITING_APPROVAL`
- Preflight status: `READY_FOR_APPROVAL`
- Source kind: `DIRECTORY`
- Target kind: `SYMLINK`
- Collision policy: `BACKUP_THEN_LINK`
- Runtime link strategy: `SYMLINK_CHILDREN`
- Primitive types: `SKILL`
- Evidence: `garden/manifests/runtime-links.json`
- Approval boundary: Requires explicit user approval after reviewing target contents, collision policy, and preflight reasons.
- Rollback: Remove links created during activation and restore any backed-up runtime paths before rerunning preflight.

Preflight reasons:

- Runtime source exists and target resolves through a symlinked directory; approval is still required before linking children into the resolved target.

Step preview:

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/skills`
1. Review target directory before linking children: `ls -la /Users/amichne/.codex/skills 2>/dev/null || true`
1. Prepare target directory after approval if needed: `mkdir -p /Users/amichne/.codex/skills`
1. Link eligible primitive children only after approval: `find /Users/amichne/code/intelligence/skills -mindepth 1 -maxdepth 1 -type d -exec sh -c 'test -f "$1/SKILL.md" && echo ln -s "$1" "$2/$(basename "$1")"' sh '{}' /Users/amichne/.codex/skills ';'`

### `codex-app-marketplace`

- State: `READY_FOR_MANUAL_IMPORT`
- Plan status: `READY_FOR_MANUAL_IMPORT`
- Preflight status: `READY_FOR_APPROVAL`
- Source kind: `FILE`
- Target kind: `VIRTUAL`
- Collision policy: `SKIP_EXISTING`
- Runtime link strategy: `MARKETPLACE_IMPORT`
- Primitive types: `PLUGIN, SKILL, AGENT, HOOK, INSTRUCTION`
- Evidence: `garden/manifests/runtime-links.json`
- Approval boundary: Requires explicit user action in the Codex app or an explicit instruction to open the import URL; this repo only records the import target.
- Rollback: Remove or disable the imported marketplace/plugin entry through the runtime UI if activation is no longer wanted.

Preflight reasons:

- Marketplace file exists; manual import still requires user action.

Step preview:

1. Verify marketplace catalog exists: `test -e /Users/amichne/code/intelligence/marketplace.json`
1. Open marketplace import URL manually after approval: `open 'codex://plugins?marketplacePath=/Users/amichne/code/intelligence/marketplace.json'`
