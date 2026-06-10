# Staleness Workflow

Use this reference when adding hooks or scripts that keep local navigation
summaries current.

## Marker File Pattern

Use a local marker such as `OUTDATED.local.md` when hooks need to flag changed
directories without doing expensive generation work.

Hook behavior:

- append one relative directory path per line;
- avoid duplicate writes when practical;
- never rewrite source files;
- do not fail developer workflows unless navigation freshness is a hard local
  policy.

Refresh behavior:

- read the marker file;
- normalize and de-duplicate paths;
- regenerate summaries for those directories only;
- leave failed paths in the report;
- truncate the marker only after successful processing.

## Safety Rules

- Treat hook output as a request for refresh, not as proof that a summary is
  stale.
- Keep all generated local files out of commits unless the repo has a checked-in
  generated-summary policy.
- Do not follow symlinks into external source trees unless the user explicitly
  chose that scope.
- If a directory no longer exists, report it and remove it from the marker after
  the user agrees or after the workflow's retention rule says stale paths can be
  dropped.

## Verification

Before relying on the workflow, verify:

- generated files are ignored;
- repeated refreshes are stable when source state does not change;
- marker processing handles blank lines, duplicates, missing directories, and
  paths with spaces;
- the workflow reports written, current, skipped, and failed directories.
