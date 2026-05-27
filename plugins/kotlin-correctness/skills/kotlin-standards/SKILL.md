---
name: "kotlin-standards"
description: "Use when writing, reviewing, or refactoring Kotlin code that benefits from type-driven design, parse-don't-validate boundaries, scoped package/file layout, Kotlin-native expression style, immutable state, explicit errors, coroutine safety, API design, or correctness-focused tests. Trigger for Kotlin implementation work, domain modeling, API review, primitive obsession, null-heavy logic, boolean flags, validation-heavy code, or package cohesion concerns."
---

# Kotlin Standards

Write Kotlin whose shape communicates the domain. Make illegal states hard to
construct, keep core rules pure, and prove behavior with focused tests.

Use these repo-level concepts when the task crosses their scope:

- `concepts/type-safety/core.md`
- `concepts/schema-driven-design/core.md`

## Operating Rules

- Prefer types over comments, conventions, nullable flags, and repeated checks.
- Parse untrusted input at boundaries, then pass trusted domain models inward.
- Keep side effects at the edge; keep the important rules pure and state-free.
- Preserve local public behavior unless the task explicitly asks for a break.
- Follow the nearest established repository pattern before adding abstraction.
- Test observable correctness, not implementation shape.

## Layout Rules

- Packages and files should map to semantic units, not horizontal layers.
- Use package scope to avoid redundant prefixes.
- Default to one primary public type or sealed root per file.
- Keep tightly owned factories, variants, and extensions with the owning type.
- Split only when the new unit has a name, owner, lifecycle, dependency, or test
  surface.

For detailed layout heuristics, read
`references/layout-package-code-style.md`.

## Type And Boundary Rules

- Use value classes, enums, sealed hierarchies, and focused data classes for
  domain concepts.
- Make constructors private when invariants require parsing or normalization.
- Prefer typed outcomes for expected failures.
- Reserve exceptions for exceptional conditions or established API contracts.
- Keep public APIs small, coherent, and hard to misuse.

## Style Rules

- Prefer expression-oriented code: `map`, `flatMap`, `fold`, `associate`,
  `partition`, `takeIf`, and `runCatching` when they state the transformation
  directly.
- Avoid transient `var`s and mutable accumulators unless they improve clarity or
  performance.
- Prefer `val`, immutable collections at boundaries, and confined mutation in
  builders or adapters.
- Avoid boolean traps, nullable control flags, and package-redundant prefixes.
- Hide implementation details with `private` or `internal`.
- Add KDoc for public APIs and non-obvious invariants; do not narrate obvious
  assignments.

## Workflow

1. Frame the behavior: boundary inputs, trusted outputs, invariants, and stable
   public behavior.
2. Inspect the immediate package, tests, and existing abstractions for local
   naming, error, layout, and verification patterns.
3. Choose the narrowest semantic unit that owns the change.
4. Add one tracer-bullet test for the next observable behavior, then implement
   the smallest vertical slice that passes.
5. Refactor only while green: improve names, package boundaries, file ownership,
   type modeling, and expression style.
6. Run the narrowest useful verification command before broadening scope.
7. Re-score the change and fix any `Fail` before finishing.

## Finish Scorecard

Mark each dimension `Pass`, `Concern`, or `Fail` before finishing:

- Domain fidelity: important concepts are represented by types, not comments or
  caller discipline.
- Boundary parsing: untrusted data is parsed once with clear failures.
- Layout cohesion: packages and files map to semantic units and avoid redundant
  prefixes.
- Error design: expected failures are explicit and testable.
- State safety: core code is immutable or intentionally confined.
- Test value: tests verify correctness themes and boundary failures through
  public behavior.
- Kotlin idiom: code reads as Kotlin, not Java with Kotlin syntax.

## Reference Map

Load only the smallest reference that matches the task:

- Layout: `layout-package-code-style.md`, `horizontalization-heuristic.md`
- Types and boundaries: `type-safety-patterns.md`, `types-domain-modeling.md`,
  `parse-dont-validate-examples.md`, `types-errors-and-testing.md`
- API design: `api-dsl-choices.md`, `api-parameter-selection.md`,
  `api-builders-and-configuration.md`, `api-extensions-and-factories.md`,
  `api-surface-stability.md`, `api-review-guides.md`
- Idiom and smells: `idioms.md`, `kotlin-antipatterns.md`,
  `types-dsls-and-generics.md`

Prefer a narrow leaf reference over a router file. Load multiple references
only when the task crosses multiple subtopics.

## Completion Gate

Do not declare Kotlin work complete until:

- No scorecard dimension is a `Fail`.
- Behavior claims are backed by executed tests, compiler checks, or an explicit
  reason verification could not run.
- Public or reusable APIs document non-obvious invariants and expected failures.
- Remaining `Concern` ratings are named with rationale or follow-up.
