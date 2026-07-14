# Command Reference

The public CLI has one operation: project one provider-neutral source to one
target harness.

## Synopsis

```text
intelligence project --source DIRECTORY --harness HARNESS --out DIRECTORY
```

## Options

| Option | Required | Values |
|---|---:|---|
| `--source DIRECTORY` | Yes | Existing provider-neutral marketplace repository. |
| `--harness HARNESS` | Yes | `codex` or `github-copilot`. |
| `--out DIRECTORY` | Yes | Harness-specific generated output root. |
| `--help` | No | Print concise command help. |

The root also supports `--version`.

## Success Output

Stdout uses a minimal TOON object:

```text
status: projected
harness: codex
files: 42
source: "/absolute/source"
output: "/absolute/output"
```

## Errors

Expected projector failures use the same channel and a stable shape:

```text
error:
  code: PROJECTION_REJECTED
  message: "reason"
  help: "intelligence project --source <directory> --harness <codex|github-copilot> --out <directory>"
```

Exit `0` means projection and target validation succeeded. Exit `1` means
the requested conversion failed. Clikt uses exit `2` for invalid command or
option syntax.
