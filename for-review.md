# TUI Command Surface Redesign

## Summary
Redesign only the bottom command surface for the Ratatui marketplace browser.
Keep the existing persistent multi-panel layout and focus model intact:
Source / Operations, Offerings, Details, and Staging stay where they are.

The command surface becomes a compact 3-line, borderless mode bar:
- Row 1: explicit mode label plus current prompt or status.
- Row 2: command suggestions, search detail, confirmation provenance, or
  active status context.
- Row 3: 3-5 contextual shortcuts for the current mode and focused pane.

Rationale: the current bordered `Command` paragraph draws two content lines
inside a `Length(3)` block with full borders, so only one inner row is visible
and the suggestions line is clipped today. Borderless rows recover that space.

## Key Changes
- Replace the bordered `Command` paragraph with a `ModeBar` render model
  derived from `App`.
- `ModeBarMode` is a pure projection of existing state — no second mode field
  on `App`:
  - `CONFIRM` derives from `pending.is_some()` (checked first, matching the
    existing `handle_key` precedence)
  - `HELP` derives from `show_help`
  - `NORMAL`, `SEARCH <scope>`, and `COMMAND` derive from `input_mode`
- Replace `status: String` and the `"Error: {error}"` prefix convention with a
  typed `StatusMessage { severity, text }`. `set_error` and `set_rpc_result`
  construct typed statuses. Status clears on the next keypress; no auto-fade
  timers (the event loop blocks on `event::read`, so there is no tick).
- Semantic ANSI-safe severity styling scoped to the mode bar: info, success,
  warning, error — each paired with a non-color text label so `NO_COLOR` and
  monochrome terminals remain usable.
- Confirmation provenance: row 2 renders human-readable source → target, e.g.
  `import skill tui-design from amichne/slopsentral@main → .`, never method
  name + raw params JSON. Raw method/params remain visible only in the batch
  confirmation modal.
- Inline confirmation for single selected actions with explicit keys: `y` or
  `Enter` confirms, `Esc` or `n` cancels, all other keys are inert; row 3
  shows exactly these keys. Modal confirmation is retained for batch or
  multi-action staged operations.
- Search detail row shows match count and scope (`SEARCH repository · 3/87`)
  plus the Enter consequence — Enter in repository scope loads a remote
  catalog and must say so (`enter previews amichne/foo`).
- Keep `Tab` mode-specific: pane focus in normal mode, search-scope cycling in
  search mode. Tab's current meaning appears in row 3 in both modes.
- Command suggestions are context-ranked, not hidden: commands matching the
  current selection, staged state, and installed/update state rank first;
  non-applicable commands render dimmed. The complete command list stays
  reachable via `?` and the docs.
- Honest mode bar during blocking RPC: `apply_effect` draws one
  `Running {title}…` frame (info severity) before each synchronous call so the
  bar never claims `NORMAL` during a multi-second install.
- Truncation priority at narrow widths, highest kept first: mode label >
  confirm keys > message (ellipsized) > suggestions (dropped right-first).
  Three rows fixed; truncate rather than adapt height.
- Update `docs/getting-started/tui.md` (command pane description, confirm
  keys, search feedback) and `docs/reference/commands.md` only where visible
  guidance or behavior changes.

## Interfaces And Types
- No CLI, RPC, schema, or command-line contract changes.
- Keep `render(frame, app)` as the public rendering entrypoint.
- Add internal display/state types in `tui/src/lib.rs`:
  - `ModeBarState` — the full derived render model for the three rows
  - `ModeBarMode` — exhaustive enum over `NORMAL`, `SEARCH`, `COMMAND`,
    `CONFIRM`, `HELP`; always derived, never stored
  - `StatusSeverity` and `StatusMessage` — typed status replaces unstructured
    display strings
  - contextual footer/suggestion helpers
- Severity, provenance, and confirmation behavior must flow through typed
  state — never string prefixes or display-string parsing.

## Test Plan
- Behavior tests:
  - mode labels in normal, search, command, confirm, and help states
  - `ModeBarMode` derivation precedence: pending > help > input mode
  - contextual footer changes by mode and focused pane, including Tab's
    meaning in both normal-mode and search-mode footers
  - `Tab` moving focus in normal mode and cycling search scope in search mode
  - inline confirmation: `y`/`Enter` confirm, `Esc`/`n` cancel, other keys
    inert
  - modal confirmation retained for batch actions
  - confirmation row renders source → target provenance for plugin install,
    primitive import, and update actions
  - search detail shows match count and the repository-scope Enter consequence
  - status clears on next keypress and carries severity as typed state
  - context-ranked (not hidden) command suggestions
- Ratatui buffer render tests for 80x24 layouts covering:
  - normal mode
  - search mode (with match count)
  - command mode
  - inline confirm mode
  - batch confirm modal
  - long-status truncation: mode label survives, message ellipsized,
    suggestions dropped
  - monochrome rendering: severity labels legible without color
- Run `cargo test --manifest-path tui/Cargo.toml`.

## Assumptions
- First pass is limited to the command surface; main pane layout, offering
  table, details pane, and staging pane behavior remain stable. The one
  exception is the single `Running {title}…` pre-call frame in
  `main.rs::apply_effect`, required for the mode bar to be truthful.
- The primary optimized workflow is browse, search, install/import/update,
  confirm, validate — with discovery (search feedback, provenance) treated as
  a first-class peer of creation.
- The design must be readable at 80x24 and uses truncation rather than
  adaptive height changes.
- Broader whole-TUI theme work is intentionally deferred.

## Deferred Follow-Ups
- Remove the hardcoded "Operations" hint list from the Source / Operations
  pane once the contextual footer lands — two surfaces teaching shortcuts will
  drift; the mode bar becomes the single source of truth. Update the docs pane
  table in the same change.
- Async RPC execution (spinner-capable, non-blocking) — required for true
  never-freeze behavior; the pre-call frame is the stopgap.
- `NO_COLOR` environment detection wired app-wide; first pass scopes non-color
  affordances to the mode bar.
