# Commands

## APM

| Command | Purpose |
|---|---|
| `apm pack --marketplace=all --dry-run --check-versions --json` | Preview Claude and Codex marketplace outputs. |
| `apm pack --marketplace=all --check-versions --json` | Generate marketplace outputs. |
| `apm audit --ci --no-policy` | Audit package content for publishing. |
| `apm marketplace add amichne/intelligence --name amichne-apm` | Register this marketplace as a consumer. |
| `apm install apm-authoring@amichne-apm` | Install a package from this marketplace. |

## Docs

```sh
zensical build --clean
```
