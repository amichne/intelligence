# Runtime Activation Preflight

This generated report inspects dry-run activation operations against the current filesystem. It does not authorize runtime mutation.

## Summary

| Measure | Value |
|---|---:|
| Total entries | 18 |
| Already active | 15 |
| Blocked | 0 |
| Ready for approval | 3 |
| Review required | 0 |
| Runtime mutation allowed | `false` |

## Entries

| Status | Type | Name | Source | Target | Reasons |
|---|---|---|---|---|---|
| `ALREADY_ACTIVE` | `ACTIVATE_RUNTIME_LINK` | `claude-agent-children` | `/Users/amichne/code/intelligence/agents` | `/Users/amichne/.claude/agents` | Runtime target already links every source child to the canonical source. |
| `ALREADY_ACTIVE` | `ACTIVATE_RUNTIME_LINK` | `codex-hook-adapters` | `/Users/amichne/code/intelligence/hooks/codex` | `/Users/amichne/.codex/hooks` | Runtime target already links every source child to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-agent-profile-authoring` | `/Users/amichne/code/intelligence/skills/agent-profile-authoring` | `/Users/amichne/code/apollo/skills/agent-profile-authoring` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-github-ci-operations` | `/Users/amichne/code/intelligence/skills/github-ci-operations` | `/Users/amichne/code/apollo/skills/github-ci-operations` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-hook-primitive-authoring` | `/Users/amichne/code/intelligence/skills/hook-primitive-authoring` | `/Users/amichne/code/apollo/skills/hook-primitive-authoring` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-local-repository-navigation` | `/Users/amichne/code/intelligence/skills/local-repository-navigation` | `/Users/amichne/code/apollo/skills/local-repository-navigation` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-plugin-composition-authoring` | `/Users/amichne/code/intelligence/skills/plugin-composition-authoring` | `/Users/amichne/code/apollo/skills/plugin-composition-authoring` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-primitive-quality-audit` | `/Users/amichne/code/intelligence/skills/primitive-quality-audit` | `/Users/amichne/code/apollo/skills/primitive-quality-audit` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-primitive-routing-evaluation` | `/Users/amichne/code/intelligence/skills/primitive-routing-evaluation` | `/Users/amichne/code/apollo/skills/primitive-routing-evaluation` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-repository-signature-indexing` | `/Users/amichne/code/intelligence/skills/repository-signature-indexing` | `/Users/amichne/code/apollo/skills/repository-signature-indexing` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-runtime-linking` | `/Users/amichne/code/intelligence/skills/runtime-linking` | `/Users/amichne/code/apollo/skills/runtime-linking` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-shell-script-safety` | `/Users/amichne/code/intelligence/skills/shell-script-safety` | `/Users/amichne/code/apollo/skills/shell-script-safety` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-site-docs-authoring` | `/Users/amichne/code/intelligence/skills/site-docs-authoring` | `/Users/amichne/code/apollo/skills/site-docs-authoring` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-skill-primitive-authoring` | `/Users/amichne/code/intelligence/skills/skill-primitive-authoring` | `/Users/amichne/code/apollo/skills/skill-primitive-authoring` | Replacement target already resolves to the canonical source. |
| `ALREADY_ACTIVE` | `REPLACE_SOURCE_WITH_SYMLINK` | `replace-apollo-skills-source-graph-consolidation` | `/Users/amichne/code/intelligence/skills/source-graph-consolidation` | `/Users/amichne/code/apollo/skills/source-graph-consolidation` | Replacement target already resolves to the canonical source. |
| `READY_FOR_APPROVAL` | `ACTIVATE_RUNTIME_LINK` | `agent-skill-children` | `/Users/amichne/code/intelligence/skills` | `/Users/amichne/.agents/skills` | Runtime source and target directory exist; approval is still required before linking children. |
| `READY_FOR_APPROVAL` | `ACTIVATE_RUNTIME_LINK` | `codex-skill-children` | `/Users/amichne/code/intelligence/skills` | `/Users/amichne/.codex/skills` | Runtime source exists and target resolves through a symlinked directory; approval is still required before linking children into the resolved target. |
| `READY_FOR_APPROVAL` | `IMPORT_MARKETPLACE` | `codex-app-marketplace` | `/Users/amichne/code/intelligence/marketplace.json` | `codex://plugins?marketplacePath=/Users/amichne/code/intelligence/marketplace.json` | Marketplace file exists; manual import still requires user action. |

## Path Details

| Name | Source Kind | Target Kind | Backup Kind |
|---|---|---|---|
| `claude-agent-children` | `DIRECTORY` | `DIRECTORY` | `-` |
| `codex-hook-adapters` | `DIRECTORY` | `DIRECTORY` | `-` |
| `replace-apollo-skills-agent-profile-authoring` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-github-ci-operations` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-hook-primitive-authoring` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-local-repository-navigation` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-plugin-composition-authoring` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-primitive-quality-audit` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-primitive-routing-evaluation` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-repository-signature-indexing` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-runtime-linking` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-shell-script-safety` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-site-docs-authoring` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-skill-primitive-authoring` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `replace-apollo-skills-source-graph-consolidation` | `DIRECTORY` | `SYMLINK` | `MISSING` |
| `agent-skill-children` | `DIRECTORY` | `DIRECTORY` | `-` |
| `codex-skill-children` | `DIRECTORY` | `SYMLINK` | `-` |
| `codex-app-marketplace` | `FILE` | `VIRTUAL` | `-` |
