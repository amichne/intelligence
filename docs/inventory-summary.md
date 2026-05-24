# Initial Primitive Inventory

Generated with:

```sh
python3 scripts/inventory-primitives.py
```

The current pass found 789 primitive-shaped artifacts:

| Type | Count |
|---|---:|
| Agent | 301 |
| Hook | 38 |
| Instruction | 11 |
| Plugin | 136 |
| Skill | 303 |

Most entries currently come from installed Claude plugin marketplace material:

| Source Root | Count |
|---|---:|
| `claude-plugins` | 608 |
| `global-agent-skills` | 28 |
| `global-codex-skills-backup` | 28 |
| `kast-agent-skills` | 23 |
| `apollo-skills` | 21 |
| `global-codex-skills` | 21 |
| `kast-github-hooks` | 9 |
| `intelligence` | 23 |
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
- `skills/kotlin-standards/SKILL.md`
- `skills/manage-json-schemas/SKILL.md`
- `concepts/type-safety/core.md`
- `concepts/schema-driven-design/core.md`
- `hooks/AGENTS.md`
- `hooks/agents-md-turn-refresh.hook.json`
- `hooks/kotlin-horizontalization-check.hook.json`
- `hooks/kotlin-horizontalization-check.py`
- `hooks/codex/kotlin-horizontalization-check.hooks.json`
- `plugins/intelligence-core/plugin.json`
- `plugins/kotlin-review/plugin.json`
- `plugins/schema-governance/plugin.json`
- `marketplace.json`

Next consolidation should separate authored/local primitives from installed
marketplace cache material, then promote one family at a time into canonical
skills, agents, hooks, instructions, and referential plugin sets.
