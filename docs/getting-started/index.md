# Getting Started

Use `intelligence` to browse, import, validate, materialize, and publish portable
marketplaces. For reusable personal skills and plugin families, use the
`slopsentral` marketplace.

## Common Tasks

| Task | Command |
|---|---|
| Browse marketplace offerings | `intelligence marketplace browse amichne/slopsentral` |
| Browse script-readable offerings | `intelligence marketplace browse amichne/slopsentral --format json` |
| Import Kotlin workflow | `intelligence marketplace import amichne/slopsentral/kotlin-engineering` |
| Install all exposed plugins | `intelligence marketplace install amichne/slopsentral` |
| Validate this CLI repo | `intelligence validate --portable` |
| Inspect CLI import state | `intelligence marketplace browse . --provider source` |
| Build docs | `zensical build --clean` |

## Next Pages

Install the docs toolchain only if `zensical` is not already available.

```sh
python3 -m venv .venv-docs
. .venv-docs/bin/activate
python -m pip install -r requirements-docs.txt
```

## First Validation

Run the repo gate before trusting local state.

```sh
./gradlew :cli:test installDevelopmentCli
.local/intelligence/bin/intelligence validate --portable
```
