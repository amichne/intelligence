# Terminal UI

The terminal UI is the primary workflow for people using `intelligence` by
hand. It gives the same marketplace semantics as the command line without
making you memorize provider entrypoints, source paths, lock files, or import
commands.

## Open The Browser

Use bare `intelligence` in an interactive terminal. The explicit subcommand is
available when a shell alias, script, or wrapper should be clear about opening
the full-screen browser.

| Situation | Command |
|---|---|
| Browse from the current repository | `intelligence` |
| Open the browser explicitly | `intelligence marketplace ui` |
| Install into another repository | `intelligence marketplace ui --repo /path/to/repo` |
| Resolve direct imports from a branch, tag, or SHA | `intelligence marketplace ui --ref main` |

The browser talks to the local `intelligence rpc` process. Marketplace parsing,
install intent, lock evidence, validation, update, pin, and remote handling all
go through the same typed CLI boundary as the non-interactive commands.

## Read The Screen

The UI is arranged around the decisions a marketplace user makes while working
inside a repository.

| Area | Purpose |
|---|---|
| Source / Operations pane | Shows the repository being browsed, default Git host, install target, search scope, installed count, update count, and core interaction hints. |
| Offerings pane | Lists plugins and exposed standalone primitives from the loaded marketplace. |
| Details pane | Shows the selected offering description, repository, tags, installed version, remote version, lock state, and install target. |
| Staging / Install pane | Holds staged install, import, and update actions before confirmation. |
| Command pane | Shows status, scoped search input, command input, suggestions, and confirmation prompts. |

The selected offering is only previewed until you confirm an operation. Import,
install, update, pin, and unpin operations open a confirmation prompt before
they write repository state.

## Keyboard Model

The browser keeps the everyday movement keys small and reserves the command
palette for operations that need a name, version, or repository reference.

| Key | Action |
|---|---|
| `?` | Show the shortcut reference. |
| `/` | Search the loaded marketplace catalog and local installed plugins. |
| `Esc` | Leave search or command mode; cancel a pending confirmation. |
| `Tab` / `Shift-Tab` | Move focus between panes. In search mode, cycle `all`, `repository`, `user`, `plugin`, `primitive`, and `installed` search scopes. |
| `j` / `k` or arrow keys | Move through offerings. |
| `Enter` | Preview the selected offering, open the selected staged action, or confirm a pending operation. |
| `r` | Preview the repository in the search box using the configured default Git host. |
| `i` | Stage the selected install, import, or update action. |
| `a` | Stage installation of every exposed plugin from the loaded marketplace. |
| `v` | Validate the target repository. |
| `x` | Remove the selected staged action when the staging pane is focused. |
| `:` | Open the command palette. |
| `q` | Quit the browser. |

Search filters the loaded catalog and local installed plugins by offering name,
kind, description, tags, repository, user, installed state, version, and update
state. Repository search can preview `owner/repo`, local paths, or Git URLs.
The default shorthand host is GitHub; use `host <enterprise-host>` to make
`owner/repo` preview an enterprise Git host instead.

## Command Palette

Open the palette with `:`. Suggestions appear in the command pane as you type.

| Command | Result |
|---|---|
| `browse amichne/slopsentral` | Preview another marketplace repository. |
| `preview amichne/slopsentral` | Preview a repository using the same host-aware rules as repository search. |
| `browse /path/to/slopsentral` | Preview a local marketplace checkout. |
| `host github.enterprise.example` | Set the default host used for `owner/repo` repository previews. |
| `target /path/to/repo` | Select the repository that receives install intent and validation. |
| `scope plugin` | Change the active search scope. |
| `search installed kotlin` | Set search scope and query from the palette. |
| `stage` | Stage the selected install, import, or update action. |
| `stage all` | Stage installation of every exposed plugin from the loaded marketplace. |
| `run staged` | Open the selected staged action confirmation. |
| `clear staged` | Remove all staged actions. |
| `import` or `install` | Import the selected plugin or exposed primitive into the target repository. |
| `install all` | Install every exposed plugin from the loaded marketplace. |
| `update` | Update the selected installed plugin. |
| `update all` | Update every imported plugin in the target repository. |
| `pin 1.2.3` | Pin the selected installed plugin to a version. |
| `unpin` | Remove the selected installed plugin pin. |
| `remote list` | Show configured external marketplaces for the target repository. |
| `validate` | Run portable validation for the target repository. |
| `quit` | Exit the browser. |

Repository references use the same rules as `marketplace browse`: an existing
local path, a GitHub URL, or `owner/repo` shorthand. Direct imports default to
`main` unless the UI was opened with `--ref`.

## Typical Flow

Most interactive use follows a browse, select, confirm, validate loop.

1. Open `intelligence` from the repository that should receive marketplace
   intent.
2. Type `:browse amichne/slopsentral` to preview the canonical marketplace.
3. Press `/` and search for the workflow, plugin, or primitive you want.
4. Move with `j` / `k` or the arrow keys until the offering is selected.
5. Type `:import` for one offering, or `:install all` for the whole marketplace.
6. Press `Enter` on the confirmation prompt.
7. Type `:validate` before trusting or committing the changed repository state.

The TUI writes the same install-only intent files as the CLI:
`.intelligence/adaptable.marketplace.json` for consumer repositories and
`.intelligence/marketplace-lock.json` for resolved content evidence.

## When To Use Commands Instead

Use the command line directly when the work is scripted, running in CI, or needs
machine-readable output.

| Job | Command |
|---|---|
| Script-readable catalog | `intelligence marketplace browse amichne/slopsentral --format json` |
| Non-interactive import | `intelligence marketplace import amichne/slopsentral/kotlin-engineering` |
| Non-interactive full install | `intelligence marketplace install amichne/slopsentral` |
| Materialize provider output | `intelligence marketplace materialize --repo /path/to/slopsentral --provider codex --out /tmp/slopsentral-codex` |
| Build the docs site | `zensical build --clean` |
