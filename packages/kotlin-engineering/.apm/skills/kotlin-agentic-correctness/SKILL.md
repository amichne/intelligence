---
name: kotlin-agentic-correctness
description: Use when Kotlin work needs typed design discipline, invariant-oriented Kotlin standards, filesystem-backed evidence, Kast semantic tooling, Gradle validation, TDD, or PR-ready proof.
---

# Kotlin Agentic Correctness

Use this skill as the workflow wrapper for Kotlin changes where correctness
depends on domain shape, semantic code understanding, and executable evidence.
It composes the Kotlin standards, typed design principles, evidence-driven
delivery loop, shell-script safety rules, and Kast semantic tooling.

## Operating Contract

- Make illegal states unrepresentable with value classes, sealed hierarchies,
  enums, private constructors, focused factories, and typed expected failures.
- Parse untrusted data at the edge. Core Kotlin code should accept trusted
  domain types, not raw strings, maps, nullable control flags, or DTOs.
- Treat JSON, CLI args, hook input, Gradle reports, Kast requests, Kast
  responses, and persisted workflow state as boundary data with a parser,
  schema, constructor, or validator.
- Use files for agent/tool exchange whenever payloads outlive one command or
  are bigger than a tiny literal. Keep requests, responses, logs, scorecards,
  and command evidence under `.agent-turn/kotlin-agentic-correctness/`.
- Prefer the packaged `kast` skill or native Kast tools for Kotlin identity,
  references, hierarchy, diagnostics, semantic insertion points, renames, and
  edit validation. Use text search only after Kast cannot answer the semantic
  question, and label that as absence verification.
- Keep shell scripts thin. Use Bash for command boundaries and Python for
  stateful JSON parsing, request construction, scoring, and evidence records.
- For mutations, prefer Kast's plan, review, apply flow or normal repository
  patch tools followed by Kast diagnostics and the narrowest Gradle proof.

## Workflow

1. Create a turn-local filesystem workspace:

   ```bash
   python3 scripts/kotlin_workflow_state.py init --repo .
   ```

   Record intent, invariants, affected files, Kast request/response files,
   Gradle logs, and review scorecards there instead of relying on terminal
   scrollback.

2. Frame the trusted shape before editing:

   - boundary inputs and their parser or schema;
   - domain values and invariants that should become types;
   - expected failures and their return shape;
   - package/file owner for the change;
   - proof target: focused test, Kast diagnostics, Gradle task, CI check, or PR
     status.

3. Orient semantically with Kast before touching Kotlin symbols. Prefer the
   installed `kast` skill when available. For CLI fallback, write a JSON-RPC
   request file and call:

   ```bash
   bash scripts/kast_rpc_file.sh \
     --workspace-root . \
     --request-file .agent-turn/kotlin-agentic-correctness/latest-request.json \
     --response-file .agent-turn/kotlin-agentic-correctness/latest-response.json
   ```

4. Use TDD for behavior changes. Add the smallest public-behavior test that
   proves the next invariant, parser failure, state transition, or API contract.

5. Implement the narrowest Kotlin slice. Keep side effects at the boundary,
   state immutable or intentionally confined, and package layout semantic
   rather than horizontally grouped.

6. Verify in widening rings:

   - Kast diagnostics for changed Kotlin files;
   - targeted Gradle compile or test task;
   - owning module check;
   - broader `check` or CI only when the touched surface requires it.

7. Review with the bundled Kotlin review agents when changes affect APIs,
   boundaries, package shape, nullable state, primitive identifiers, or expected
   failures. Deduplicate findings into one prioritized list.

8. Finish with the exact commands run, file-backed evidence paths, scorecard
   result, and any remaining `Concern`. Do not declare completion with a
   `Fail` scorecard dimension.

## Scorecard

Mark each dimension `Pass`, `Concern`, or `Fail` before finishing:

- Domain fidelity: important concepts are represented by types, not comments or
  caller discipline.
- Boundary parsing: untrusted data is parsed once with explicit failures.
- Layout cohesion: packages and files map to semantic units.
- Error design: expected failures are typed, stable, and testable.
- State safety: core state is immutable or mutation is intentionally confined.
- Test value: tests prove behavior and boundary failures through public
  surfaces.
- Kotlin idiom: code reads as Kotlin and keeps public APIs hard to misuse.
- Filesystem evidence: non-trivial tool exchange uses request, response, log,
  or scorecard files.
- Kast semantics: semantic Kotlin identity, references, diagnostics, or edits
  use Kast where available.

## Reference Map

- Filesystem evidence layout: `references/filesystem-evidence-contract.md`
- Kast file-first fallback: `references/kast-file-first.md`
- Helper scripts:
  - `scripts/kotlin_workflow_state.py`
  - `scripts/kast_rpc_file.sh`

Use the bundled `negative-capability-proof`, `kotlin-standards`,
`kotlin-gradle-validation`, `tdd`, `git-change-flow`,
`github-ci-operations`, `pull-request-lifecycle`, and `shell-script-safety`
skills for their narrower procedures when the turn enters their scope.
