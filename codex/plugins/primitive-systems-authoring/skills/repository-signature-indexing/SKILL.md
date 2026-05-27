---
name: "repository-signature-indexing"
description: "Create or maintain compact repository signature indexes that help agents route source reads without opening every file. Use when a codebase needs generated type or symbol summaries, JVM fully qualified names, deterministic source maps, or drift checks for signature-level documentation."
---

# Repository Signature Indexing

Use this skill to create dense, generated source maps that help agents decide
which files to read. Signature indexes are navigation artifacts, not
hand-authored documentation. They should be deterministic, compact, and
regenerated from source.

## Operating Contract

- Treat signature indexes as generated artifacts.
- Define the output contract before generating or committing signature files.
- Keep generated entries minimal: package, imports, types, fields, methods, and
  stable source references are usually enough.
- Prefer fully qualified names for JVM types when package metadata exists.
- Do not hand-edit generated signature files.
- Regenerate after package moves, refactors, generated-source changes, or public
  API changes.
- If signatures are stored as structured data, validate them with a schema,
  parser, or deterministic generator check.

## Workflow

1. Choose the scope.
   Identify source roots, generated-source roots to include or exclude,
   languages, package conventions, and the output directory.

2. Define the contract.
   Specify file naming, path mirroring, index format, required fields, ordering,
   and how unknown syntax is represented. Keep this contract beside the
   generator or in repo documentation.

3. Generate deterministically.
   Sort files and symbols, normalize whitespace, avoid timestamps, and make the
   same source tree produce byte-stable output.

4. Inspect representative output.
   Check the root index, a file with multiple types, a file with no top-level
   type, and a package or import edge case.

5. Wire drift detection.
   Add a check command that regenerates to a temporary directory or verifies the
   checked-in index matches source.

6. Use the index as a routing aid.
   Read signatures first for relevance filtering, then open source files only
   when the signature indicates likely relevance or the task needs body-level
   details.

## Recommended Text Contract

For a line-oriented `.sig` format, keep entries stable and machine-readable:

```text
file=<relative source path>
package=<package or default>
imports=<comma-separated imports>
type=<fqcn>|kind=<class|interface|object|enum|...>|decl=<normalized declaration>
fields:
- <normalized field signature>
methods:
- <normalized method signature>
```

Use an `INDEX.sig` file containing one sorted relative path per generated
signature file. If a repo chooses JSON instead, model the same facts with a
closed schema before writing the files.

## Completion Criteria

- The signature contract is explicit and located near the generator or repo
  docs.
- Generated output is deterministic across repeated runs.
- Source paths mirror the source tree or have a documented mapping.
- Drift detection is available before relying on the index.
- Agents use the index to choose reads, not as a substitute for source when
  implementation details matter.
