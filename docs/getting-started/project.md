# Project Agent Tooling

Use `project` when provider-neutral marketplace material must be rendered for
one supported harness.

## Inputs

| Option | Contract |
|---|---|
| `--source DIRECTORY` | Repository containing `source/adaptable.marketplace.json` and referenced source material. |
| `--harness HARNESS` | Exactly `codex` or `github-copilot`. |
| `--out DIRECTORY` | Generated harness payload root outside the source tree. |

## Command

```sh
intelligence project \
  --source /path/to/marketplace \
  --harness codex \
  --out /tmp/projected-marketplace
```

The command validates the source, converts its marketplace and composed
primitives, validates the generated target, and then returns a file count and
normalized paths.

## Failure Boundary

Expected failures are structured on stdout with an actionable command shape.

```text
error:
  code: SOURCE_INVALID
  message: "source/adaptable.marketplace.json: ..."
  help: "intelligence project --source <directory> --harness <codex|github-copilot> --out <directory>"
```

No failure path installs or registers generated material.
