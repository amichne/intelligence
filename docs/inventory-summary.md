# Initial Primitive Inventory

Generated with:

```sh
python3 scripts/inventory-primitives.py
```

The first pass found 774 primitive-shaped artifacts:

| Type | Count |
|---|---:|
| Agent | 296 |
| Hook | 35 |
| Instruction | 8 |
| Plugin | 134 |
| Skill | 301 |

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
| `intelligence` | 8 |
| `apollo-agents` | 7 |
| `examplar-agents` | 5 |
| `global-codex-backups` | 5 |
| `examplar-skills` | 4 |
| `examplar-instructions` | 3 |
| `examplar-plugins` | 2 |
| `examplar-hooks` | 1 |
| `kast-github-agents` | 1 |

The generated manifest also records 105 duplicate name groups, 93 duplicate
digest groups, and 13 broken symlinks. Duplicate digests are strong candidates
for symlink replacement after review. Duplicate names without matching digests
require semantic review before promotion or cleanup. Broken symlinks need repair
or removal entries in `manifests/cleanup-ledger.json` before any filesystem
cleanup.

Immediate canonical entries are intentionally small:

- `plugouts/concepts/type-safety/core.md`
- `plugouts/concepts/schema-driven-design/core.md`
- `hooks/agents-md-turn-refresh.hook.json`
- `plugins/intelligence-core/plugin.json`
- `marketplace.json`

Next consolidation should separate authored/local primitives from installed
marketplace cache material, then promote one family at a time into canonical
skills, agents, hooks, instructions, and referential plugin sets.
