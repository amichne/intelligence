---
name: shell-script-safety
description: Write, review, or harden Bash and shell automation used by hooks, CI, release scripts, installers, and local developer tooling. Use when shell scripts need predictable failure handling, quoting, dry-run boundaries, temporary-file cleanup, argument parsing, or safer destructive-operation guardrails.
---

# Shell Script Safety

Use this skill when shell code needs to become durable automation. The goal is
not to make every command elaborate; it is to make scripts fail clearly,
preserve data, and remain maintainable when run by agents, hooks, CI, or humans.

## Operating Contract

- Confirm the target shell before choosing Bash-only features.
- Prefer a short script with explicit checks over a dense pipeline that hides
  failure behavior.
- Treat destructive operations as opt-in: require dry-run support,
  confirmation, or an explicit force flag before mutation.
- Quote variables, use arrays for argument lists, and avoid string-built
  commands.
- Keep temporary files under a managed directory and clean them up with traps.
- Emit errors to stderr and reserve stdout for structured or user-requested
  output.
- If the script emits persisted structured data, define or reuse the schema,
  parser, or validator before writing it.

## Workflow

1. Identify the execution boundary.
   Record shell, OS assumptions, working directory, environment variables,
   required tools, and whether the script runs locally, in CI, or as a hook.

2. Choose strictness deliberately.
   For Bash scripts, start from `set -Eeuo pipefail` unless a command sequence
   requires narrower handling. For POSIX `sh`, avoid Bash arrays and `[[ ]]`.

3. Make inputs explicit.
   Validate required args, env vars, files, directories, and commands before
   doing work. Prefer one `usage` function and clear exit codes.

4. Protect file and process handling.
   Quote expansions, use `--` before path arguments when supported, use
   null-delimited file lists for arbitrary paths, and avoid parsing display
   output when a structured command option exists.

5. Add cleanup and rollback boundaries.
   Use `mktemp -d` for scratch space, `trap` for cleanup, and write mutation
   plans before applying broad changes. Prefer moving or linking only after
   validation passes.

6. Test the important paths.
   Run syntax checks, a dry-run path, a success path, and at least one expected
   failure path. For hooks, run a representative local invocation with explicit
   input.

## Review Checklist

- Does the script fail on unset required input?
- Are all path and argument expansions quoted?
- Are command arguments stored in arrays instead of concatenated strings?
- Are destructive commands guarded by dry-run, confirmation, or force?
- Are temporary files cleaned up on success and failure?
- Does error output go to stderr?
- Does CI or hook usage have a deterministic validation command?
- If JSON, YAML, TOML, or another structured format is written, does a schema or
  parser validate it?

## Common Patterns

Use these patterns as starting points, then adapt to the target shell:

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  printf 'Usage: %s --input PATH [--dry-run]\n' "${0##*/}" >&2
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$tmp_dir"' EXIT
```

For file walks:

```bash
while IFS= read -r -d '' file; do
  printf 'checking %s\n' "$file"
done < <(find "$root" -type f -print0)
```

For command invocation:

```bash
args=(--check --format json)
[[ "${verbose:-false}" == "true" ]] && args+=(--verbose)
tool "${args[@]}" -- "$target"
```

## Completion Criteria

- Shell and runtime assumptions are explicit.
- Required inputs and tools are validated before mutation.
- Destructive behavior has a reviewable safety boundary.
- Syntax checks and representative success/failure paths have been run.
- Structured outputs have schema, parser, or validator coverage.
