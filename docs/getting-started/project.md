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

## GitHub Action

Use the repository action when projection should be a workflow step. The action
sets up Java, downloads the selected stable release and `SHA256SUMS`, verifies
the archive, and invokes the same `project` boundary.

```yaml
- name: Check out source
  uses: actions/checkout@v5

- name: Project source
  id: projection
  uses: amichne/intelligence@main
  with:
    source: .
    harness: github-copilot
    version: v0.2.7

- name: Upload generated payload
  uses: actions/upload-artifact@v4
  with:
    name: github-copilot-projection
    path: ${{ steps.projection.outputs.projection-path }}
```

`source` defaults to the workflow workspace. When `output` is omitted, the
action creates a fresh directory under `RUNNER_TEMP`. `version` accepts
`latest` or an exact stable `vX.Y.Z` tag. The action exposes
`projection-path`, `files`, and `version` as step outputs.

Pin an immutable action ref and exact projector version for reproducible
workflows. Uploading, committing, installing, or registering the projection is
outside the action boundary.
