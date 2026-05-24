---
name: tdd
description: >
  Test-driven development with red-green-refactor loop. Use when user wants to build features
  or fix bugs using TDD, mentions "red-green-refactor", wants integration tests, asks for
  test-first development, or says "write tests first". Also use when the user asks to implement
  something and explicitly wants tests to drive the design, or mentions tracer bullets, vertical
  slices of test+impl, or behavior-driven testing.
---

# Test-Driven Development

Use this skill to drive work through one behavior at a time. Tests must verify
observable behavior through public interfaces, not private structure.

## Core Rules

- One test, one behavior, one implementation slice.
- Start with a tracer bullet that proves the path end to end.
- Keep tests public-interface based and refactor-resistant.
- Do not write all tests first and then all implementation.
- Do not refactor while red.
- Do not add speculative production code for future tests.

See [tests.md](references/tests.md) for examples and [mocking.md](references/mocking.md) for mocking guidelines.

## Workflow

1. Frame the public interface and the behavior that matters.
2. List behavior tests in priority order; do not pre-commit to every edge case.
3. Write one failing tracer-bullet test.
4. Implement the smallest vertical slice that passes.
5. Repeat RED -> GREEN one behavior at a time.
6. Refactor only after the suite is green.
7. Run the narrowest useful verification after every refactor step.

Ask for clarification only when the public interface, critical behaviors, or
risk tolerance cannot be inferred from the task and local code.

## Per-Cycle Checklist

[ ] Test describes behavior, not implementation
[ ] Test uses public interface only
[ ] Test would survive internal refactor
[ ] Code is minimal for this test
[ ] No speculative features added
[ ] Refactor runs only while green

## Runtime Execution

Use the repository's own test entrypoint and the narrowest command that can prove
the behavior under change. For JVM/Kotlin projects, prefer the checked-in Gradle
wrapper or documented repo command, and inspect structured test output when the
project emits it.

Run a pre-flight check when the environment is uncertain, then run the targeted
test after each RED -> GREEN cycle. On unexpected failure, read the failure
artifact or compiler output before changing production code.

For non-JVM projects, run whatever test command is appropriate for the stack;
the same one-test-at-a-time discipline applies.

## References

- [interface-design.md](references/interface-design.md): public test seams and deep modules
- [deep-modules.md](references/deep-modules.md): simple interface, deep implementation
- [refactoring.md](references/refactoring.md): green-state cleanup moves
- [tests.md](references/tests.md): behavior-test examples
- [mocking.md](references/mocking.md): when mocks are appropriate
