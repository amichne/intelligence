---
name: local-repository-navigation
description: Create or refresh git-excluded local repository navigation summaries, such as AGENTS.local.md, OUTDATED.local.md, directory purpose maps, and staleness-driven summary refresh workflows. Use when bootstrapping AI-readable repo maps, designing local navigation generators, or maintaining lightweight generated summaries without turning them into durable documentation.
---

# Local Repository Navigation

Use this skill to create local, generated navigation maps that help agents choose
where to read next. These maps are routing aids, not source-of-truth docs.

## Operating Contract

- Keep durable repo instructions in `AGENTS.md`; keep local navigation summaries
  in git-excluded files such as `AGENTS.local.md`.
- Do not commit local summaries unless the repository explicitly makes them a
  checked-in generated artifact with a drift check.
- Make generated summaries deterministic enough to skip unchanged directories.
- Read just enough source to orient the summary. Do not turn navigation refresh
  into full documentation authoring.
- Keep any staleness marker append-only for hooks and truncate-only for the
  refresh workflow.
- Never mutate source files as part of navigation refresh.

## Workflow

1. Choose the local artifact.
   Name the summary file, where it lives, and how it is excluded from version
   control. Use [summary-contract.md](references/summary-contract.md) for the
   default `AGENTS.local.md` contract.

2. Define exclusions.
   Exclude VCS metadata, dependency caches, build outputs, virtual
   environments, generated local summaries, and staleness marker files.

3. Generate a summary.
   List the directory, read at most a few representative files, infer purpose,
   record key files and subdirectories, and write a compact map.

4. Detect staleness cheaply.
   Use a deterministic directory fingerprint from file names, sizes, or mtimes.
   Prefer a stable hash over re-reading every file.

5. Refresh only what changed.
   When a marker such as `OUTDATED.local.md` exists, process each listed path,
   refresh the matching local summaries, and clear the marker only after the
   refresh succeeds. See [staleness-workflow.md](references/staleness-workflow.md).

## Completion Criteria

- Local navigation files are ignored or explicitly generated.
- The summary contract states required sections, exclusions, and hash behavior.
- Refresh work is bounded to changed or requested directories.
- Hooks or scripts never edit source files while maintaining navigation.
- The result helps choose source reads; it is not a substitute for reading code
  when implementation details matter.
