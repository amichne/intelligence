# Runtime Trim Execution

This receipt records an explicit packet-scoped trim or activation attempt.

## Summary

| Measure | Value |
|---|---:|
| Mode | `APPLY` |
| Requested packets | 2 |
| Selected packets | 2 |
| Dry-run entries | 0 |
| Applied entries | 2 |
| Skipped entries | 0 |
| Failed entries | 0 |
| Runtime mutation attempted | `true` |

## Entries

| State | Packet | Type | Source | Target | Reasons |
|---|---|---|---|---|---|
| `APPLIED` | `agent-skill-children` | `ACTIVATE_RUNTIME_LINK` | `/Users/amichne/code/intelligence/skills` | `/Users/amichne/.agents/skills` | Created missing child symlinks. |
| `APPLIED` | `codex-skill-children` | `ACTIVATE_RUNTIME_LINK` | `/Users/amichne/code/intelligence/skills` | `/Users/amichne/.codex/skills` | Created missing child symlinks. |

## Actions

### `agent-skill-children`

Primitive types: `SKILL`

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/skills`
1. Prepare target directory: `mkdir -p /Users/amichne/.agents/skills`
1. Link child agent-profile-authoring: `ln -s /Users/amichne/code/intelligence/skills/agent-profile-authoring /Users/amichne/.agents/skills/agent-profile-authoring`
1. Link child define-goal: `ln -s /Users/amichne/code/intelligence/skills/define-goal /Users/amichne/.agents/skills/define-goal`
1. Link child git-change-flow: `ln -s /Users/amichne/code/intelligence/skills/git-change-flow /Users/amichne/.agents/skills/git-change-flow`
1. Link child github-ci-operations: `ln -s /Users/amichne/code/intelligence/skills/github-ci-operations /Users/amichne/.agents/skills/github-ci-operations`
1. Link child hook-primitive-authoring: `ln -s /Users/amichne/code/intelligence/skills/hook-primitive-authoring /Users/amichne/.agents/skills/hook-primitive-authoring`
1. Link child kotlin-gradle-validation: `ln -s /Users/amichne/code/intelligence/skills/kotlin-gradle-validation /Users/amichne/.agents/skills/kotlin-gradle-validation`
1. Child kotlin-standards is already linked: `true`
1. Link child local-repository-navigation: `ln -s /Users/amichne/code/intelligence/skills/local-repository-navigation /Users/amichne/.agents/skills/local-repository-navigation`
1. Child manage-json-schemas is already linked: `true`
1. Link child plugin-composition-authoring: `ln -s /Users/amichne/code/intelligence/skills/plugin-composition-authoring /Users/amichne/.agents/skills/plugin-composition-authoring`
1. Link child primitive-quality-audit: `ln -s /Users/amichne/code/intelligence/skills/primitive-quality-audit /Users/amichne/.agents/skills/primitive-quality-audit`
1. Link child primitive-routing-evaluation: `ln -s /Users/amichne/code/intelligence/skills/primitive-routing-evaluation /Users/amichne/.agents/skills/primitive-routing-evaluation`
1. Link child reference-doc-workflow: `ln -s /Users/amichne/code/intelligence/skills/reference-doc-workflow /Users/amichne/.agents/skills/reference-doc-workflow`
1. Link child repo-instruction-topology: `ln -s /Users/amichne/code/intelligence/skills/repo-instruction-topology /Users/amichne/.agents/skills/repo-instruction-topology`
1. Link child repository-signature-indexing: `ln -s /Users/amichne/code/intelligence/skills/repository-signature-indexing /Users/amichne/.agents/skills/repository-signature-indexing`
1. Link child runtime-linking: `ln -s /Users/amichne/code/intelligence/skills/runtime-linking /Users/amichne/.agents/skills/runtime-linking`
1. Link child shell-script-safety: `ln -s /Users/amichne/code/intelligence/skills/shell-script-safety /Users/amichne/.agents/skills/shell-script-safety`
1. Link child site-docs-authoring: `ln -s /Users/amichne/code/intelligence/skills/site-docs-authoring /Users/amichne/.agents/skills/site-docs-authoring`
1. Link child skill-primitive-authoring: `ln -s /Users/amichne/code/intelligence/skills/skill-primitive-authoring /Users/amichne/.agents/skills/skill-primitive-authoring`
1. Link child source-graph-consolidation: `ln -s /Users/amichne/code/intelligence/skills/source-graph-consolidation /Users/amichne/.agents/skills/source-graph-consolidation`
1. Child tdd is already linked: `true`

### `codex-skill-children`

Primitive types: `SKILL`

1. Verify source directory exists: `test -d /Users/amichne/code/intelligence/skills`
1. Prepare target directory: `mkdir -p /Users/amichne/.codex/skills`
1. Link child agent-profile-authoring: `ln -s /Users/amichne/code/intelligence/skills/agent-profile-authoring /Users/amichne/.codex/skills/agent-profile-authoring`
1. Child define-goal is already linked: `true`
1. Link child git-change-flow: `ln -s /Users/amichne/code/intelligence/skills/git-change-flow /Users/amichne/.codex/skills/git-change-flow`
1. Link child github-ci-operations: `ln -s /Users/amichne/code/intelligence/skills/github-ci-operations /Users/amichne/.codex/skills/github-ci-operations`
1. Link child hook-primitive-authoring: `ln -s /Users/amichne/code/intelligence/skills/hook-primitive-authoring /Users/amichne/.codex/skills/hook-primitive-authoring`
1. Link child kotlin-gradle-validation: `ln -s /Users/amichne/code/intelligence/skills/kotlin-gradle-validation /Users/amichne/.codex/skills/kotlin-gradle-validation`
1. Child kotlin-standards is already linked: `true`
1. Link child local-repository-navigation: `ln -s /Users/amichne/code/intelligence/skills/local-repository-navigation /Users/amichne/.codex/skills/local-repository-navigation`
1. Link child manage-json-schemas: `ln -s /Users/amichne/code/intelligence/skills/manage-json-schemas /Users/amichne/.codex/skills/manage-json-schemas`
1. Link child plugin-composition-authoring: `ln -s /Users/amichne/code/intelligence/skills/plugin-composition-authoring /Users/amichne/.codex/skills/plugin-composition-authoring`
1. Link child primitive-quality-audit: `ln -s /Users/amichne/code/intelligence/skills/primitive-quality-audit /Users/amichne/.codex/skills/primitive-quality-audit`
1. Link child primitive-routing-evaluation: `ln -s /Users/amichne/code/intelligence/skills/primitive-routing-evaluation /Users/amichne/.codex/skills/primitive-routing-evaluation`
1. Link child reference-doc-workflow: `ln -s /Users/amichne/code/intelligence/skills/reference-doc-workflow /Users/amichne/.codex/skills/reference-doc-workflow`
1. Link child repo-instruction-topology: `ln -s /Users/amichne/code/intelligence/skills/repo-instruction-topology /Users/amichne/.codex/skills/repo-instruction-topology`
1. Link child repository-signature-indexing: `ln -s /Users/amichne/code/intelligence/skills/repository-signature-indexing /Users/amichne/.codex/skills/repository-signature-indexing`
1. Link child runtime-linking: `ln -s /Users/amichne/code/intelligence/skills/runtime-linking /Users/amichne/.codex/skills/runtime-linking`
1. Link child shell-script-safety: `ln -s /Users/amichne/code/intelligence/skills/shell-script-safety /Users/amichne/.codex/skills/shell-script-safety`
1. Link child site-docs-authoring: `ln -s /Users/amichne/code/intelligence/skills/site-docs-authoring /Users/amichne/.codex/skills/site-docs-authoring`
1. Link child skill-primitive-authoring: `ln -s /Users/amichne/code/intelligence/skills/skill-primitive-authoring /Users/amichne/.codex/skills/skill-primitive-authoring`
1. Link child source-graph-consolidation: `ln -s /Users/amichne/code/intelligence/skills/source-graph-consolidation /Users/amichne/.codex/skills/source-graph-consolidation`
1. Link child tdd: `ln -s /Users/amichne/code/intelligence/skills/tdd /Users/amichne/.codex/skills/tdd`
