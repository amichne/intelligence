# Validation

Validation is APM-first. Run checks from the repository root.

## Marketplace

```sh
apm pack --marketplace=all --dry-run --check-versions --json
apm audit --ci --no-policy
```

## Hooks

Parse changed hook JSON and check executable scripts.

```sh
python3 -m json.tool packages/kotlin-engineering/.apm/hooks/gradle-check-green-codex-hooks.json
bash -n packages/kotlin-engineering/hooks/gradle-check-green.sh
python3 -m py_compile packages/kotlin-engineering/hooks/kotlin-horizontalization-check.py
```

## Documentation

```sh
zensical build --clean
```

For this local-package marketplace, `apm pack --marketplace=all` is the
publish gate because it resolves the local package entries and validates version
alignment. If a check fails, repair the package-owned source under `packages/`
or the root `apm.yml`. Do not patch generated marketplace JSON by hand.
