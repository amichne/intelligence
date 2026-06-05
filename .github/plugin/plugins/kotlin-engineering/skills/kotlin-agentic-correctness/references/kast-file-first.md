# Kast File-First Fallback

Prefer the installed `kast` skill or host-native `kast_*` tools when they are
available. Use this fallback when the current host exposes only the `kast` CLI.

## Setup

Confirm the CLI first:

```bash
command -v kast
kast --help
```

If the packaged Kast skill is installed, validate hand-authored request files
with its `scripts/validate-rpc-request.py` before sending them. If that
validator is not present, at least parse the request as JSON before calling
`kast rpc`.

## File-Based RPC

Use `scripts/kast_rpc_file.sh` for request/response exchange:

```bash
bash scripts/kast_rpc_file.sh \
  --workspace-root . \
  --request-file .agent-turn/kotlin-agentic-correctness/session/kast/diagnostics.request.json \
  --response-file .agent-turn/kotlin-agentic-correctness/session/kast/diagnostics.response.json \
  --stderr-file .agent-turn/kotlin-agentic-correctness/session/kast/diagnostics.stderr.log
```

The wrapper validates JSON input, normalizes the request to a compact temporary
file for the CLI, writes the response and stderr to files, and leaves stdout
empty.

## Request Choices

- Workspace shape: `raw/workspace-files`
- Symbol discovery by name: `raw/workspace-symbol` or `symbol/discover`
- Exact identity: `raw/resolve` or `symbol/resolve`
- Usages: `raw/references` or `symbol/references`
- Call flow: `raw/call-hierarchy` or `symbol/callers`
- Type shape: `raw/type-hierarchy` or `raw/implementations`
- File outline: `raw/file-outline`
- Diagnostics: `raw/diagnostics`
- Safe rename: `symbol/rename`, then reviewed apply
- Validated write: `symbol/write-and-validate` when available
- External edits: `raw/workspace-refresh`, then diagnostics

For larger or variant-heavy requests, read the installed Kast skill's
`references/commands.json` and request examples instead of guessing fields.
