# Getting Started

Start with the APM manifest. The root `apm.yml` exposes local packages through
the `amichne-apm` marketplace.

## Common Tasks

| Task | Command |
|---|---|
| Preview marketplace outputs | `apm pack --marketplace=all --dry-run --check-versions --json` |
| Audit package content | `apm audit --ci --no-policy` |
| Build docs | `zensical build --clean` |

## Next Pages

Install validation dependencies before running repository gates.

```sh
npm ci
```

Install the docs toolchain only if `zensical` is not already available.

```sh
python3 -m venv .venv-docs
. .venv-docs/bin/activate
python -m pip install -r requirements-docs.txt
```

## Choose A Path

Each path has a dry-run or validation command before it mutates anything.

| Path | Command | Use When |
|---|---|---|
| Validate this repository | `.local/intelligence/bin/intelligence validate` | You changed manifests, hooks, schemas, profiles, or marketplace files. |
| Build docs | `zensical build --clean` | You changed this documentation site or navigation. |

## First Validation

Run the repo gate before trusting local state.

```sh
./gradlew installDevelopmentCli
.local/intelligence/bin/intelligence validate
```

This wraps the manifest validation path. Use the expanded commands in
[Validation](../how-it-works/validation.md) when you need to isolate a failing
schema check.
