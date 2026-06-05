---
name: "negative-capability-proof"
description: "Use when Kotlin work must prove an architectural invariant by demonstrating a currently representable invalid state or operation, then moving enforcement into the Kotlin type system so that misuse is no longer reachable."
---

# Negative Capability Proof

Use this skill for a scoped Kotlin change where success is not "more types" but
fewer representable illegal states, operations, transitions, or combinations.
The output must prove that an architectural hole existed before the change and
that the reachable state space is smaller afterward.

## Operating Contract

- Operate only inside the caller-provided module, package, feature area, service
  boundary, or explicit file set.
- Select exactly one invariant unless the user explicitly asks for more.
- Preserve valid behavior while moving enforcement from runtime checks,
  conventions, documentation, configuration, code review, or tests into Kotlin's
  type system.
- Prefer sealed interfaces, sealed hierarchies, capability-specific interfaces,
  constrained construction, phantom markers, typed state transitions, and
  restricted visibility.
- Avoid reflection, code generation, annotation processing, runtime validation
  disguised as typing, and generic machinery that is bigger than the invariant.
- Keep executable proof artifacts in source control. Use `.agent-turn/` only for
  transient logs and scratch evidence.

## Candidate Discovery

Search only the requested scope. Look for:

- Boolean mode flags.
- Mutually exclusive configuration values.
- Runtime capability checks.
- Environment-dependent APIs.
- State machines with illegal transitions.
- Sealed hierarchies represented as primitive values.
- Feature toggles that encode type distinctions.
- Authorization or capability rules encoded as runtime guards.
- APIs exposing operations unavailable to some implementations.

For each candidate, write a short candidate note with:

- Architectural invariant.
- Current enforcement mechanism.
- Invalid state or operation currently representable.
- Expected type-level representation.
- Estimated blast radius.

Rank candidates by architectural importance, proof strength, locality of change,
and risk. Choose the highest-ranked candidate that can be completed and verified
inside the requested scope.

## Red Proof

Before refactoring, add an executable proof that demonstrates the architectural
hole. The proof must not rely only on compilation behavior.

Acceptable red proof forms:

- Runtime counterexample: an illegal operation is expressible and reaches the
  runtime guard.
- Invalid state construction: an illegal configuration or state can be
  instantiated.
- Reachable state-space proof: construct or enumerate legal and illegal members
  of the current model.
- API surface proof: a forbidden capability appears on an accessible API.
- Misuse fixture: a named fixture captures a real forbidden usage pattern.
- Existing test gap: the misuse survives the current suite and is prevented only
  by convention.

The red proof must identify:

- The invariant being violated.
- The invalid state or operation.
- The mechanism currently responsible for rejection.
- Why the compiler cannot currently prevent the misuse.

Run the narrowest test command that executes the red proof and record the
expected failing or counterexample-producing result.

## Refactor

Move the invariant to the type boundary with the smallest model change that
eliminates the misuse:

- Replace primitive tags or mode flags with a sealed hierarchy or enum-backed
  domain type when legal variants are finite.
- Split mixed-capability APIs into capability-specific interfaces when only some
  implementations should expose an operation.
- Make constructors private or internal when callers can currently assemble an
  invalid instance.
- Replace nullable or mutually exclusive fields with typed variants that carry
  only the data valid for that variant.
- Represent lifecycle transitions as functions whose receiver and return types
  make illegal transitions absent.

Do not add a runtime check and call it typing. Boundary parsers and factories may
reject untrusted input, but core Kotlin APIs should accept only the trusted
typed shape after parsing.

## Green Proof

After refactoring, change or add proof that the original misuse is no longer
reachable. Focus on eliminated representability.

Acceptable green proof forms:

- State-space reduction: the previously invalid state has no construction path.
- Capability elimination: the forbidden operation is absent from the accessible
  API.
- Transition elimination: the illegal transition is no longer modeled.
- Constructor elimination: invalid combinations cannot be constructed.
- Compile-time rejection: a misuse fixture fails to compile through compile
  testing or an equivalent checked fixture.

Compilation failure is strong supporting evidence, but the primary claim must be
about the missing construction path, missing operation, or smaller state space.

## Verification

Use the repository's own proof loop. Prefer the narrowest executable sequence:

1. Red proof command.
2. Targeted Kotlin test or compile task after the type change.
3. Owning module check when the public API or construction boundary changed.
4. Broader Gradle or CI verification only when the touched surface requires it.

If Kast is available in the workflow, use it for semantic Kotlin navigation,
reference checks, or diagnostics before making API-shape claims.

## Completion Report

Finish with:

- Architectural summary: invariant, original enforcement mechanism, and new
  enforcement mechanism.
- State-space analysis: invalid states or operations before and after.
- Evidence: red proof, green proof, test outputs, and compilation outputs.
- Change report: files modified, public APIs modified, and migration impact.

The work is complete only when a real invariant was identified, a previously
representable misuse was demonstrated, that misuse was removed from the
reachable model, valid behavior still works, the state-space reduction is
evidenced, and the proof survives automated execution.
