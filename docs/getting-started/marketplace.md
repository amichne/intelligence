# Marketplace

The marketplace is authored in the root `apm.yml` `marketplace:` block.
Generated marketplace JSON is output from `apm pack`, not source.

## Consumer Setup

Register the marketplace and install a package.

```sh
apm marketplace add amichne/intelligence --name amichne-apm
apm install apm-authoring@amichne-apm
```

Use other package names from [Plugin families](../available/plugin-families.md)
as needed.

## Producer Preview

Run current APM checks from the repository root.

```sh
apm pack --marketplace=all --dry-run --check-versions --json
apm audit --ci --no-policy
```

## Published Shape

| Surface | Purpose |
|---|---|
| `apm.yml` | Marketplace source of truth. |
| `packages/*/apm.yml` | Package metadata. |
| `packages/*/.apm/` | Package primitives. |
| `.claude-plugin/marketplace.json` | Generated Claude marketplace output. |
| `.agents/plugins/marketplace.json` | Generated Codex marketplace output. |

When Codex output is enabled, every marketplace package entry includes a
`category` field.
