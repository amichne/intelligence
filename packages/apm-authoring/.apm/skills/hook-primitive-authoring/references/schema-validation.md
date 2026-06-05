# Hook Validation

Use this reference for validating APM hook primitives in package-owned `.apm/`
trees.

## APM Boundary

APM discovers hook JSON files from `.apm/hooks/*.json`. Target-specific hooks
should use the suffix convention recognized by APM, such as
`<name>-codex-hooks.json`, so a Codex hook is not installed into unrelated
targets.

Executable scripts referenced by hook JSON should live under package `hooks/`.
Commands such as `python3 hooks/check.py` or `bash hooks/check.sh` give APM a
package-relative path it can rewrite during install.

## Validation Checklist

- Parse changed hook JSON with `python3 -m json.tool`.
- Run `bash -n hooks/*.sh` for shell hook implementations.
- Run `python3 -m py_compile hooks/*.py` for Python hook implementations.
- Run a representative local command for hooks that inspect repository state.
- Run `apm pack --marketplace=all --dry-run --check-versions --json` from the
  marketplace root to prove package discovery and marketplace output mapping.
- Run `apm audit --ci --no-policy` before publishing.

## Packaging Checks

- Hook files live under `.apm/hooks/`, not generated runtime output paths.
- Target-specific files include a target suffix such as `-codex-hooks.json`.
- Script paths in hook JSON resolve from the package root.
- JSON sidecar config lives under package `hook-config/`, not `hooks/`.
- Related skills, agents, or instructions are present in the same package or
  documented as external assumptions.
- Public-safe provenance is recorded when a hook is promoted from another
  source.
