# Getting Started

Use `intelligence` as a CLI-first marketplace operator. Start with discovery
commands and switch to JSON when composing with automation.

## Common Tasks

| Task | Primary Path |
|---|---|
| Check CLI and GitHub host state | `intelligence doctor` |
| Search GitHub repositories | `intelligence marketplace search kotlin` |
| Inspect the canonical marketplace | `intelligence marketplace inspect amichne/slopsentral` |
| Search offerings inside a marketplace | `intelligence marketplace search kotlin --repository amichne/slopsentral` |
| Import a selected offering | `intelligence marketplace import amichne/slopsentral/kotlin-engineering` |
| Validate install state | `intelligence validate --portable` |

Use JSON when the result needs to be scripted or captured.

| Task | Command |
|---|---|
| Inspect script-readable offerings | `intelligence marketplace inspect amichne/slopsentral --format json` |
| List installed marketplace state | `intelligence marketplace installed list --format json` |
| Import Kotlin workflow | `intelligence marketplace import amichne/slopsentral/kotlin-engineering` |
| Install all exposed plugins | `intelligence marketplace install amichne/slopsentral` |
| Validate this CLI repo | `intelligence validate --portable` |
| Build docs | `zensical build --clean` |

## Next Pages

Start with the [Marketplace](marketplace.md) page for discovery and exact
command forms.

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
