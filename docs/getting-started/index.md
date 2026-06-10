# Getting Started

Use `intelligence` as a full-screen marketplace browser first. The command-line
forms are still available for scripts, CI, publication, and exact copyable
operations, but a human should normally start in the terminal UI.

## Common Tasks

| Task | Primary Path |
|---|---|
| Open the marketplace browser | `intelligence` |
| Preview the canonical marketplace | `:browse amichne/slopsentral` inside the TUI |
| Search offerings | `/` inside the TUI |
| Import the selected offering | `:import` inside the TUI |
| Install all exposed plugins | `:install all` inside the TUI |
| Validate install state | `:validate` inside the TUI |

Use direct commands when the result needs to be scripted or captured.

| Task | Command |
|---|---|
| Browse script-readable offerings | `intelligence marketplace browse amichne/slopsentral --format json` |
| Import Kotlin workflow | `intelligence marketplace import amichne/slopsentral/kotlin-engineering` |
| Install all exposed plugins | `intelligence marketplace install amichne/slopsentral` |
| Validate this CLI repo | `intelligence validate --portable` |
| Build docs | `zensical build --clean` |

## Next Pages

Start with the [Terminal UI](tui.md) page for the interactive workflow. Use the
[Marketplace](marketplace.md) page when you need exact non-interactive command
forms for imports, materialization, publication, or automation.

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
