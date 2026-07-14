# Primitive Material

The projector handles the primitive kinds declared by the provider-neutral
schema.

| Primitive | Projected material |
|---|---|
| Skill | `SKILL.md` and owned resources |
| Agent | Agent Markdown plus harness metadata |
| Instruction | Markdown guidance referenced by the plugin |
| Hook | Hook metadata, target adapter JSON, and referenced commands |
| Other typed material | Files owned by the primitive's schema and source path |

Primitives may be exposed directly by a marketplace or composed into plugins.
Projection preserves that source ownership while rewriting paths for the target
harness.
