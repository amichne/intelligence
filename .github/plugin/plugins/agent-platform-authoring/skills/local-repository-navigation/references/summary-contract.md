# Local Summary Contract

Use this reference when designing or reviewing `AGENTS.local.md` or an
equivalent local navigation summary.

## Default Shape

```markdown
<!-- AGENTS.local.md: generated local navigation, do not commit -->
<!-- generated: <ISO date> | hash: <short hash> -->

# <directory name>

## Purpose
<one or two sentences>

## Key Files
- `file` - why an agent would open it

## Subdirectories
- [`dir/`](dir/AGENTS.local.md) - why an agent would enter it

## Entry Points
<public commands, APIs, manifests, or owning files when relevant>

## Navigation Hints
- For <task>, start with <path>.
```

## Required Properties

- Local-only: ignored through `.gitignore`, `.git/info/exclude`, or an explicit
  generated-artifact policy.
- Deterministic enough to compare against current directory state.
- Short enough to scan before opening source files.
- Focused on routing: purpose, key files, subdirectories, entry points, and
  next-read hints.

## Exclusions

Exclude `.git`, dependency directories, build outputs, virtual environments,
IDE metadata, local summary files, staleness markers, binary caches, and other
directories that are not useful routing inputs.

## Hash Guidance

Use a cheap fingerprint when the summary is a local cache:

- sort included child names;
- include file sizes or mtimes when useful;
- ignore the summary file itself;
- keep the hash short but stable enough to detect stale summaries.

Use content hashes only when the repository needs stronger drift detection and
the cost is acceptable.
