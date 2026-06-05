# amichne-apm

`amichne-apm` is an APM-native marketplace for reusable AI tooling primitives.
The repository publishes package-owned skills, agents, hooks, and instructions
through the Microsoft APM marketplace flow.

APM is the point of arrival here. The root `apm.yml` is the marketplace source
of truth, packages live under `packages/`, and generated marketplace JSON is
created by `apm pack`.

## Repository Shape

- `apm.yml` owns the `amichne-apm` marketplace and its package entries.
- `packages/*/apm.yml` owns package metadata.
- `packages/*/.apm/` owns package primitives.
- `packages/*/hooks/` owns executable hook scripts referenced by hook JSON.
- `.claude-plugin/marketplace.json` is the generated Claude marketplace output.
- `.agents/plugins/marketplace.json` is the generated Codex marketplace output.
- `docs/` contains the Zensical documentation site.

## Validate

Use current APM for marketplace and package checks.

```sh
apm pack --marketplace=all --dry-run --check-versions --json
apm audit --ci --no-policy
```

Run hook syntax checks when hook scripts change.

```sh
bash -n packages/*/hooks/*.sh
python3 -m py_compile packages/*/hooks/*.py
```

Build docs after documentation or navigation changes.

```sh
zensical build --clean
```

## Publish

Publishing is APM-only. Use `apm pack --marketplace=all --check-versions --json`
to refresh marketplace outputs, then publish the resulting marketplace artifacts
through the configured release workflow.
