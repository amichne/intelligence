---
mode: agent
description: Process OUTDATED.local.md — regenerate AGENTS.local.md for all directories flagged as changed since last navigation update.
tools: ['codebase']
---

Process `OUTDATED.local.md` at the repo root.

1. Read `OUTDATED.local.md`. Each non-blank line is a directory path relative to the repo root.
   If the file does not exist or is empty, report "Nothing to update." and stop.

2. For each listed directory:
   - List its contents.
   - Read at most 5 files to infer purpose (priority: `README.md` > `SKILL.md` > `AGENT.md` > largest `.md` > primary source).
   - Compute a hash from sorted `name:size` pairs of non-excluded items.
   - Regenerate `AGENTS.local.md` unconditionally (these dirs are flagged as changed).

3. After all directories are processed, truncate `OUTDATED.local.md` to empty (zero bytes).

4. Report: N updated, N failed (directory no longer exists).

Do not modify any source files. Only write `AGENTS.local.md` files and truncate `OUTDATED.local.md`.
