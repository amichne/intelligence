---
name: kast
description: Kotlin semantic work and linked-worktree lifecycle in Gradle repositories prepared by the Kast IntelliJ plugin.
---

# Kast

This workspace was prepared by the Kast IntelliJ plugin from the Homebrew-distributed Kast package.

Use `kast agent verify --workspace-root "$PWD"` before Kotlin semantic work when state is uncertain.
Use typed commands such as `kast agent symbol`, `kast agent diagnostics`, `kast agent impact`, and `kast agent rename`.
Do not run `kast setup` or install Kast runtime/resources separately on macOS; reopen the workspace in IntelliJ IDEA or Android Studio with the Kast plugin enabled.

## Linked Worktrees

For every delegated worker using a linked Git worktree:

1. Before the worker starts, open the exact worktree root as its own IntelliJ IDEA or Android Studio project with the Kast plugin enabled.
2. Wait for `.kast/setup/workspace.json`, then run `kast agent verify --workspace-root "$PWD"` from that worktree.
3. Never reuse another worktree's Kast runtime, metadata, or semantic evidence.
4. Keep that IDE project open while the worker and worktree are active.
5. Before retiring or deleting the worktree, close that exact IDE project or window before removing the worktree.

The coordinating agent owns this setup and teardown for every delegated worktree.

Prepared plugin version: 0.13.0
CLI invocation: `/opt/homebrew/Cellar/kast/0.13.0/bin/kast`

