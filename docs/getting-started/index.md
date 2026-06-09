# Getting Started

Start with the marketplace catalog. `source/adaptable.marketplace.json` exposes
local plugin families and points each entry at `source/plugins/*/plugin.json`.

## Common Tasks

| Task | Command |
|---|---|
| Browse marketplace offerings | `intelligence marketplace browse amichne/intelligence` |
| Browse script-readable offerings | `intelligence marketplace browse amichne/intelligence --format json` |
| Validate source contracts | `intelligence validate` |
| Materialize Codex output | `intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace` |
| Materialize GitHub output | `intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace` |
| Build docs | `zensical build --clean` |

## Next Pages

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
| Browse marketplace offerings | `intelligence marketplace browse amichne/intelligence` | You want to see available plugins and standalone primitives. |
| Validate this repository | `intelligence validate` | You changed manifests, hooks, schemas, profiles, or marketplace files. |
| Preview generated output | `intelligence marketplace materialize --provider all --out /tmp/intelligence-marketplace` | You changed marketplace exposure or projection logic. |
| Build docs | `zensical build --clean` | You changed this documentation site or navigation. |

## First Validation

Run the repo gate before trusting local state.

```sh
intelligence validate
```

Use the expanded commands in [Validation](../how-it-works/validation.md) when
you need to isolate a failing source or hydrated-output check.
