# Local Hook Layout

Use this reference for package-owned APM hook files.

## Layout

```text
packages/<name>/
  .apm/
    hooks/
      <hook-name>-codex-hooks.json
  hooks/
    <hook-name>.sh
    <hook-name>.py
  hook-config/
    <hook-name>.requirements.json
```

## Hook JSON

APM discovers hook definitions from `.apm/hooks/*.json`. Use target suffixes
such as `-codex-hooks.json` when the hook is target-specific.

Hook commands should reference package files through `${PLUGIN_ROOT}` so APM can
copy and rewrite them during install:

```json
{
  "type": "command",
  "command": "python3 ${PLUGIN_ROOT}/hooks/example.py --config ${PLUGIN_ROOT}/hook-config/example.json"
}
```

## Sidecars

Keep JSON sidecars under `hook-config/`, not `hooks/`. APM also scans
`hooks/*.json` as hook definitions, so sidecars in that directory can be treated
as malformed hooks.

## Validation

- Parse hook JSON and sidecar JSON with `python3 -m json.tool`.
- Run `bash -n` for shell hook scripts.
- Run `python3 -m py_compile` for Python hook scripts.
- Run `apm pack --marketplace=all --dry-run --check-versions --json` from the
  marketplace root.
