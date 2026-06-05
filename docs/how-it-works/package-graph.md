# APM Package Graph

The repository now has one publishing model: APM packages and an APM
marketplace manifest.

```mermaid
flowchart TD
  Root[root apm.yml]
  Package[packages/*/apm.yml]
  Primitive[packages/*/.apm primitives]
  HookScripts[packages/*/hooks scripts]
  Pack[apm pack]
  Claude[.claude-plugin/marketplace.json]
  Codex[.agents/plugins/marketplace.json]

  Primitive --> Package
  HookScripts --> Package
  Package --> Root
  Root --> Pack
  Pack --> Claude
  Pack --> Codex
```

## Source Of Truth

| Path | Role |
|---|---|
| `apm.yml` | Marketplace source of truth. |
| `packages/*/apm.yml` | Package source of truth. |
| `packages/*/.apm/` | Primitive source of truth. |
| `.claude-plugin/marketplace.json` | Generated marketplace output. |
| `.agents/plugins/marketplace.json` | Generated marketplace output. |

Generated marketplace JSON should be refreshed through APM, not hand-authored.
