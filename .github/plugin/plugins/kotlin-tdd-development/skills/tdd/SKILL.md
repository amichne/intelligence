---
name: tdd
description: >
  Language-agnostic test-driven development with red-green-refactor and
  evidence-based handoff discipline. Use when the user wants to build features
  or fix bugs using TDD, mentions red-green-refactor, wants integration tests,
  asks for test-first development, says "write tests first", or wants a
  resumable implementation loop with clear proof artifacts.
---

# Test-Driven Development

Use this skill to drive software work through one observable behavior at a
time. It is independent of language, framework, and test runner: discover the
repository's own commands, then use the narrowest proof loop that exercises a
public interface.

Use these repo-level concepts when they are available and relevant:

- `concepts/type-safety/core.md`
- `concepts/schema-driven-design/core.md`

## Operating Contract

- Read the nearest repo instructions, existing tests, and build metadata before
  choosing a test command.
- State the behavior, public interface, edit scope, and proof target before the
  first test.
- Work one behavior at a time: one failing test, one implementation slice, one
  passing result.
- Keep tests public-interface based and refactor-resistant.
- Treat RED, GREEN, REFACTOR, VERIFY, and DONE as evidence phases.
- Do not write all tests first and then all implementation.
- Do not refactor while red.
- Do not add speculative production code for future tests.

See [tests.md](references/tests.md) for behavior-test examples and
[mocking.md](references/mocking.md) for mocking guidance.

## Workflow

1. Discover the repo's language, test runner, fixture style, and fastest
   targeted test command.
2. Frame the public interface and the behavior that matters.
3. List behavior tests in priority order; do not pre-commit to every edge case.
4. Write one failing tracer-bullet test and record the RED failure.
5. Implement the smallest vertical slice that passes and record the GREEN
   result.
6. Refactor only while green, then rerun the same targeted proof.
7. Repeat one behavior at a time.
8. Broaden to VERIFY only when the change crosses a boundary, public workflow,
   generated contract, or user-facing behavior.

Ask for clarification only when the public interface, critical behaviors, or
risk tolerance cannot be inferred from the task and local code.

## Evidence And Handoff

If the repository already has a workflow CLI, hook state, lease mechanism, or
handoff file convention, use that authoritative surface for status and
completion assertions. Do not parse or duplicate its state logic.

When no durable workflow tool exists, keep the same handoff shape in user
updates and the final response:

- goal and acceptance target;
- edit scope and changed files;
- current phase: `RED`, `GREEN`, `REFACTOR`, `VERIFY`, or `DONE`;
- evidence log: command, result, and artifact path when available;
- next behavior or blocker.

Load [handoff.md](references/handoff.md) for long-running work, interruptions,
handoffs to another agent, or tasks with multiple test cycles.

## Per-Cycle Checklist

[ ] Test describes behavior, not implementation
[ ] Test uses public interface only
[ ] Test would survive internal refactor
[ ] Code is minimal for this test
[ ] No speculative features added
[ ] Refactor runs only while green

## Runtime Execution

Use the repository's own test entrypoint and the narrowest command that can prove
the behavior under change. Prefer checked-in wrappers, documented scripts, or
package metadata over globally installed defaults.

Run a pre-flight check when the environment is uncertain, then run the targeted
test after each RED -> GREEN cycle. On unexpected failure, read the failure
artifact or compiler output before changing production code.

## References

- [interface-design.md](references/interface-design.md): public test seams and deep modules
- [deep-modules.md](references/deep-modules.md): simple interface, deep implementation
- [refactoring.md](references/refactoring.md): green-state cleanup moves
- [tests.md](references/tests.md): behavior-test examples
- [mocking.md](references/mocking.md): when mocks are appropriate
- [handoff.md](references/handoff.md): resumable phase and evidence summaries
