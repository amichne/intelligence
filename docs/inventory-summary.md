# Initial Primitive Inventory

Generated with:

```sh
python3 scripts/inventory-primitives.py
```

The current pass found 806 primitive-shaped artifacts:

| Type | Count |
|---|---:|
| Agent | 301 |
| Hook | 38 |
| Instruction | 11 |
| Plugin | 142 |
| Skill | 314 |

Most entries currently come from installed Claude plugin marketplace material:

| Source Root | Count |
|---|---:|
| `claude-plugins` | 608 |
| `intelligence` | 40 |
| `global-agent-skills` | 28 |
| `global-codex-skills-backup` | 28 |
| `kast-agent-skills` | 23 |
| `apollo-skills` | 21 |
| `global-codex-skills` | 21 |
| `kast-github-hooks` | 9 |
| `apollo-agents` | 7 |
| `examplar-agents` | 5 |
| `global-codex-backups` | 5 |
| `examplar-skills` | 4 |
| `examplar-instructions` | 3 |
| `examplar-plugins` | 2 |
| `examplar-hooks` | 1 |
| `kast-github-agents` | 1 |

The generated manifest also records 111 duplicate name groups, 93 duplicate
digest groups, and 13 broken symlinks. Duplicate digests are strong candidates
for symlink replacement after review. Duplicate names without matching digests
require semantic review before promotion or cleanup. Broken symlinks need repair
or removal entries in `manifests/cleanup-ledger.json` before any filesystem
cleanup.

Immediate canonical entries are intentionally small:

- `agents/kotlin-review/kotlin-review-captain.agent.md`
- `agents/kotlin-review/kotlin-type-safety-reviewer.agent.md`
- `agents/kotlin-review/kotlin-boundary-contract-reviewer.agent.md`
- `agents/kotlin-review/kotlin-package-cohesion-reviewer.agent.md`
- `agents/schema-type-enforcer.agent.md`
- `skills/AGENTS.md`
- `skills/agent-profile-authoring/SKILL.md`
- `skills/define-goal/SKILL.md`
- `skills/git-change-flow/SKILL.md`
- `skills/hook-primitive-authoring/SKILL.md`
- `skills/github-ci-operations/SKILL.md`
- `skills/kotlin-standards/SKILL.md`
- `skills/manage-json-schemas/SKILL.md`
- `skills/plugin-composition-authoring/SKILL.md`
- `skills/reference-doc-workflow/SKILL.md`
- `skills/repo-instruction-topology/SKILL.md`
- `skills/skill-primitive-authoring/SKILL.md`
- `skills/site-docs-authoring/SKILL.md`
- `skills/tdd/SKILL.md`
- `concepts/type-safety/core.md`
- `concepts/schema-driven-design/core.md`
- `hooks/AGENTS.md`
- `hooks/agents-md-turn-refresh.hook.json`
- `hooks/kotlin-horizontalization-check.hook.json`
- `hooks/kotlin-horizontalization-check.py`
- `hooks/codex/kotlin-horizontalization-check.hooks.json`
- `plugins/documentation-workflow/plugin.json`
- `plugins/intelligence-core/plugin.json`
- `plugins/kotlin-review/plugin.json`
- `plugins/planning-and-docs/plugin.json`
- `plugins/primitive-authoring/plugin.json`
- `plugins/repository-orientation/plugin.json`
- `plugins/schema-governance/plugin.json`
- `plugins/tdd-workflow/plugin.json`
- `plugins/version-control/plugin.json`
- `marketplace.json`

Next consolidation should separate authored/local primitives from installed
marketplace cache material, then promote one family at a time into canonical
skills, agents, hooks, instructions, and referential plugin sets.
