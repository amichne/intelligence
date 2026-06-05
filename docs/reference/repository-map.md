# Repository Map

| Path | Purpose |
|---|---|
| `apm.yml` | Root APM marketplace manifest. |
| `packages/*/apm.yml` | APM package manifests. |
| `packages/*/.apm/skills/` | Skill primitives. |
| `packages/*/.apm/agents/` | Agent primitives. |
| `packages/*/.apm/instructions/` | Instruction primitives. |
| `packages/*/.apm/hooks/` | Hook JSON primitives. |
| `packages/*/hooks/` | Hook executable scripts. |
| `packages/*/hook-config/` | Hook sidecar JSON that should not be scanned as hook definitions. |
| `.claude-plugin/marketplace.json` | Generated Claude marketplace output. |
| `.agents/plugins/marketplace.json` | Generated Codex marketplace output. |
| `docs/` | Zensical documentation source. |
