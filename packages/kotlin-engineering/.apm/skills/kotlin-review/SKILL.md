---
name: kotlin-review
description: Review Kotlin code and Kotlin edits with the Kotlin review agent suite. Use when Codex needs to audit Kotlin diffs, PRs, or changed files for type safety, parse-don't-validate boundaries, package cohesion, horizontalization, duplicated logic, primitive/null/boolean traps, expected failure design, or correctness-focused review findings.
---

# Kotlin Review

Use this skill as the orchestration layer for Kotlin review. It routes a Kotlin
change through focused review passes, applies the local type and schema
instructions, and deduplicates the result into one actionable finding list.

## Building Blocks

Use these bundled primitives when they are available:

- `kotlin-review-captain`: coordinate broad or end-of-turn Kotlin review.
- `kotlin-type-safety-reviewer`: audit domain modeling, nullability, primitive
  identifiers, expected failures, and visibility.
- `kotlin-boundary-contract-reviewer`: audit public APIs, adapters, CLI,
  serialization, persistence, HTTP, messaging, SDK, and interop boundaries.
- `kotlin-package-cohesion-reviewer`: audit package topology, prefix-heavy
  directories, multi-member files, and horizontal layer buckets.
- `type-safety` and `schema-driven-design`: use as normative instructions for
  invalid-state prevention and boundary assertions.
- `kotlin-standards`: load for detailed Kotlin layout, API, idiom, and testing
  references when the review needs more than the focused agent profiles.

If multi-agent tools are available and the review is non-trivial, invoke only
the focused reviewers whose triggers match the changed code. If those tools are
not available, apply the corresponding agent profile manually from the bundled
agent files.

## Workflow

1. Scope the review.
   Read the nearest repository instructions, identify changed Kotlin files with
   `git diff` or the user-provided paths, and inspect only the smallest
   surrounding context needed to understand public behavior.

2. Route review passes.
   Use the captain for broad review or when multiple axes apply. Use focused
   reviewers directly for narrow requests: type safety, boundary contracts, or
   package cohesion.

3. Run a duplication and generalization pass.
   Look for repeated parser/validation logic, DTO-domain mapping, lifecycle
   rules, parallel metadata catalogs, repeated leading filename prefixes,
   duplicated tests, and multiple constructors or factories enforcing the same
   invariant. Prefer one named owner only when the shared concept, lifecycle,
   dependency direction, and verification surface are clear.

4. Deduplicate findings.
   If one package move, parser extraction, sealed type, value class, or boundary
   assertion fixes several symptoms, report it as one finding with the combined
   rationale. Do not split type, boundary, cohesion, and duplication findings
   when the same change is the meaningful fix.

5. Verify review evidence.
   Use file paths, line numbers, type signatures, package counts, call sites,
   tests, compiler output, or Gradle/Kast evidence. For pure review, do not run
   broad validation unless the user asks or the risk justifies it. If fixes are
   made, use `kotlin-gradle-validation` for the narrowest useful proof.

## Finding Standard

Lead with findings ordered by severity. Every finding must include:

- severity;
- file and line or package directory;
- object under review;
- criteria;
- evidence;
- baseline or nearest local pattern;
- confidence;
- proposed fix;
- proof that would confirm the fix.

If no issue is justified, say that directly and name residual uncertainty such
as missing call-site context, tests not run, generated code, or incomplete diff
coverage.

## Severity

- `P0`: invalid domain state can be constructed, routine boundary failures are
  invisible, or package shape hides an obvious subdomain behind 8 or more direct
  peer Kotlin files.
- `P1`: semantic nulls, primitive obsession, duplicated parser/invariant logic,
  untyped expected failures, DTO leakage into core code, or a package root over
  5 direct Kotlin files without a documented reason.
- `P2`: repeated prefixes, duplicated mappers/tests, multi-member files, weak
  visibility, or extraction candidates that create avoidable review risk but do
  not immediately break behavior.
- `P3`: local cleanup, naming, or layout polish that should not block the turn.

## Review Output

Use this shape:

```markdown
## Findings
- [Severity] [file:line] [finding with criteria, evidence, baseline, confidence, fix, and proof]

## Verification
- [Commands, diffs, files, or reason verification was not run]

## Residual Risk
- [Only what remains uncertain]
```
