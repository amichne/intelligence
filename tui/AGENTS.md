# TUI Agent Instructions

## Scope

This file applies to the Rust Ratatui sidecar in `tui/`.

## Design Rules

- Keep the TUI as a presentation and interaction layer over the shared
  `intelligence rpc` contract. Do not duplicate marketplace parsing,
  normalization, lockfile, validation, or install semantics in Rust.
- Preserve `render(frame, app)` as the public rendering entrypoint unless the
  caller contract is intentionally changed.
- Model visible UI states with Rust types instead of string prefixes, raw JSON
  display parsing, or boolean combinations that can drift.
- Keep pane layout stable unless the task explicitly asks to redesign the full
  interface. Prefer focused changes to the command surface, focus model, or
  interaction state under test.
- Confirmation text shown in the main interface should describe the user action
  and target. Raw RPC methods and params belong only in explicit debug or batch
  confirmation surfaces.

## Verification

- Run `cargo test --manifest-path tui/Cargo.toml` after Rust TUI changes.
- Add behavior tests for interaction state and buffer render tests for visible
  Ratatui output when changing layout, prompts, shortcut text, truncation, or
  confirmation behavior.
