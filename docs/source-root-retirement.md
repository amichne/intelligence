# Source Root Retirement

This generated report summarizes which scanned roots can be retained, reviewed, or prepared for source turnoff. It does not authorize deletion or symlink writes.

## Summary

| Measure | Value |
|---|---:|
| Total roots | 18 |
| Total observed entries | 867 |
| Canonical owners | 1 |
| Partial replacement ready | 1 |
| Runtime dependency mapped | 0 |
| Runtime review required | 0 |
| Covered, no replacement plan | 3 |
| Mixed retain and covered | 7 |
| Retain external owners | 4 |
| Cleanup recorded | 1 |
| Empty source roots | 1 |
| No action recorded | 0 |

Next action: Review PARTIAL_REPLACEMENT_READY roots and their activation approval packets before any source turnoff execution.

## Roots

| State | Root | Role | Entries | Covered | Retained | Proposed | Ready | Review | Mapped | Next Action |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| `CANONICAL_OWNER` | `intelligence` | `canonical-candidate` | 54 | 29 | 0 | 0 | 0 | 0 | 0 | Keep this repository as the source of truth; expose primitives only through referential plugins and approved runtime links. |
| `CLEANUP_RECORDED` | `claude-agents` | `runtime-source` | 6 | 0 | 0 | 0 | 0 | 0 | 0 | Keep the cleanup ledger as the source of truth for completed cleanup and rerun inventory to detect drift. |
| `COVERED_NO_REPLACEMENT_PLAN` | `examplar-agents` | `local-repo-source` | 5 | 5 | 0 | 0 | 0 | 0 | 0 | Decide whether this covered source should stay as provenance or receive a cleanup-ledger replacement proposal. |
| `COVERED_NO_REPLACEMENT_PLAN` | `examplar-hooks` | `local-repo-source` | 1 | 1 | 0 | 0 | 0 | 0 | 0 | Decide whether this covered source should stay as provenance or receive a cleanup-ledger replacement proposal. |
| `COVERED_NO_REPLACEMENT_PLAN` | `examplar-skills` | `local-repo-source` | 3 | 3 | 0 | 0 | 0 | 0 | 0 | Decide whether this covered source should stay as provenance or receive a cleanup-ledger replacement proposal. |
| `EMPTY_SOURCE_ROOT` | `apollo-plugins` | `local-repo-source` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | Keep or remove the scan root only through a source-roots manifest review; no primitives are currently discovered. |
| `MIXED_RETAIN_AND_COVERED` | `apollo-agents` | `local-repo-source` | 7 | 1 | 1 | 0 | 0 | 0 | 0 | Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence. |
| `MIXED_RETAIN_AND_COVERED` | `claude-plugins` | `runtime-source` | 608 | 2 | 11 | 0 | 0 | 0 | 0 | Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence. |
| `MIXED_RETAIN_AND_COVERED` | `examplar-instructions` | `local-repo-source` | 3 | 2 | 1 | 0 | 0 | 0 | 0 | Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence. |
| `MIXED_RETAIN_AND_COVERED` | `global-agent-skills` | `runtime-source` | 46 | 1 | 16 | 0 | 0 | 0 | 0 | Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence. |
| `MIXED_RETAIN_AND_COVERED` | `global-codex-backups` | `backup-source` | 5 | 3 | 1 | 0 | 0 | 0 | 0 | Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence. |
| `MIXED_RETAIN_AND_COVERED` | `global-codex-skills-backup` | `backup-source` | 28 | 4 | 11 | 0 | 0 | 0 | 0 | Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence. |
| `MIXED_RETAIN_AND_COVERED` | `kast-agent-skills` | `local-repo-source` | 23 | 2 | 9 | 0 | 0 | 0 | 0 | Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence. |
| `PARTIAL_REPLACEMENT_READY` | `apollo-skills` | `local-repo-source` | 33 | 21 | 19 | 13 | 0 | 0 | 0 | Review approval packets by name before changing cleanup-ledger status or executing any symlink replacement. |
| `RETAIN_EXTERNAL_OWNER` | `examplar-plugins` | `local-repo-source` | 2 | 0 | 2 | 0 | 0 | 0 | 0 | Leave this source root under its current owner unless a future review promotes a canonical replacement. |
| `RETAIN_EXTERNAL_OWNER` | `global-codex-skills` | `runtime-source` | 33 | 0 | 19 | 0 | 0 | 0 | 0 | Leave this source root under its current owner unless a future review promotes a canonical replacement. |
| `RETAIN_EXTERNAL_OWNER` | `kast-github-agents` | `local-repo-source` | 1 | 0 | 1 | 0 | 0 | 0 | 0 | Leave this source root under its current owner unless a future review promotes a canonical replacement. |
| `RETAIN_EXTERNAL_OWNER` | `kast-github-hooks` | `local-repo-source` | 9 | 0 | 2 | 0 | 0 | 0 | 0 | Leave this source root under its current owner unless a future review promotes a canonical replacement. |

## Root Details

### `intelligence`

- Path: `.`
- Resolved path: `/Users/amichne/code/intelligence`
- State: `CANONICAL_OWNER`
- Next action: Keep this repository as the source of truth; expose primitives only through referential plugins and approved runtime links.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot intelligence.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=29 and retained=0 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `claude-agents`

- Path: `~/.claude/agents`
- Resolved path: `/Users/amichne/.claude/agents`
- State: `CLEANUP_RECORDED`
- Next action: Keep the cleanup ledger as the source of truth for completed cleanup and rerun inventory to detect drift.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot claude-agents.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=0 and retained=0 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=4, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `examplar-agents`

- Path: `../examplar/agents`
- Resolved path: `/Users/amichne/code/examplar/agents`
- State: `COVERED_NO_REPLACEMENT_PLAN`
- Next action: Decide whether this covered source should stay as provenance or receive a cleanup-ledger replacement proposal.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot examplar-agents.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=5 and retained=0 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=5, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `examplar-hooks`

- Path: `../examplar/hooks`
- Resolved path: `/Users/amichne/code/examplar/hooks`
- State: `COVERED_NO_REPLACEMENT_PLAN`
- Next action: Decide whether this covered source should stay as provenance or receive a cleanup-ledger replacement proposal.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot examplar-hooks.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=1 and retained=0 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=1, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `examplar-skills`

- Path: `../examplar/skills`
- Resolved path: `/Users/amichne/code/examplar/skills`
- State: `COVERED_NO_REPLACEMENT_PLAN`
- Next action: Decide whether this covered source should stay as provenance or receive a cleanup-ledger replacement proposal.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot examplar-skills.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=3 and retained=0 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=3, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `apollo-plugins`

- Path: `../apollo/plugins`
- Resolved path: `/Users/amichne/code/apollo/plugins`
- State: `EMPTY_SOURCE_ROOT`
- Next action: Keep or remove the scan root only through a source-roots manifest review; no primitives are currently discovered.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot apollo-plugins.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=0 and retained=0 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `apollo-agents`

- Path: `../apollo/agents`
- Resolved path: `/Users/amichne/code/apollo/agents`
- State: `MIXED_RETAIN_AND_COVERED`
- Next action: Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot apollo-agents.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=1 and retained=1 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `claude-plugins`

- Path: `~/.claude/plugins`
- Resolved path: `/Users/amichne/.claude/plugins`
- State: `MIXED_RETAIN_AND_COVERED`
- Next action: Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot claude-plugins.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=2 and retained=11 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `examplar-instructions`

- Path: `../examplar/instructions`
- Resolved path: `/Users/amichne/code/examplar/instructions`
- State: `MIXED_RETAIN_AND_COVERED`
- Next action: Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot examplar-instructions.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=2 and retained=1 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=2, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `global-agent-skills`

- Path: `~/.agents/skills`
- Resolved path: `/Users/amichne/.agents/skills`
- State: `MIXED_RETAIN_AND_COVERED`
- Next action: Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot global-agent-skills.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=1 and retained=16 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=6, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `global-codex-backups`

- Path: `~/.codex/backups`
- Resolved path: `/Users/amichne/.codex/backups`
- State: `MIXED_RETAIN_AND_COVERED`
- Next action: Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot global-codex-backups.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=3 and retained=1 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=1, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `global-codex-skills-backup`

- Path: `~/.codex/skills.backup`
- Resolved path: `/Users/amichne/.codex/skills.backup`
- State: `MIXED_RETAIN_AND_COVERED`
- Next action: Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot global-codex-skills-backup.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=4 and retained=11 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=5, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `kast-agent-skills`

- Path: `../kast/.agents/skills`
- Resolved path: `/Users/amichne/code/kast/.agents/skills`
- State: `MIXED_RETAIN_AND_COVERED`
- Next action: Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot kast-agent-skills.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=2 and retained=9 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `apollo-skills`

- Path: `../apollo/skills`
- Resolved path: `/Users/amichne/code/apollo/skills`
- State: `PARTIAL_REPLACEMENT_READY`
- Next action: Review approval packets by name before changing cleanup-ledger status or executing any symlink replacement.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot apollo-skills.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=21 and retained=19 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=13, executed=6, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `examplar-plugins`

- Path: `../examplar/plugins`
- Resolved path: `/Users/amichne/code/examplar/plugins`
- State: `RETAIN_EXTERNAL_OWNER`
- Next action: Leave this source root under its current owner unless a future review promotes a canonical replacement.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot examplar-plugins.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=0 and retained=2 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `global-codex-skills`

- Path: `~/.codex/skills`
- Resolved path: `/Users/amichne/.codex/skills`
- State: `RETAIN_EXTERNAL_OWNER`
- Next action: Leave this source root under its current owner unless a future review promotes a canonical replacement.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot global-codex-skills.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=0 and retained=19 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `kast-github-agents`

- Path: `../kast/.github/agents`
- Resolved path: `/Users/amichne/code/kast/.github/agents`
- State: `RETAIN_EXTERNAL_OWNER`
- Next action: Leave this source root under its current owner unless a future review promotes a canonical replacement.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot kast-github-agents.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=0 and retained=1 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.

### `kast-github-hooks`

- Path: `../kast/.github/hooks`
- Resolved path: `/Users/amichne/code/kast/.github/hooks`
- State: `RETAIN_EXTERNAL_OWNER`
- Next action: Leave this source root under its current owner unless a future review promotes a canonical replacement.

Evidence:

- manifests/discovered-primitives.json records entries for sourceRoot kast-github-hooks.
- manifests/source-review-decisions.json and manifests/digest-review-decisions.json record covered=0 and retained=2 review groups.
- manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json record proposed=0, executed=0, readyForApproval=0, reviewRequired=0, dependencyMapped=0.
