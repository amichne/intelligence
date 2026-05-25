# TDD Handoff

Use this reference when work may continue across sessions, agents, or long
implementation loops. The goal is to make the next agent trust the state from
evidence, not from narrative confidence.

## Phase Vocabulary

- `RED`: A behavior test exists and fails for the expected reason.
- `GREEN`: The targeted behavior test passes with minimal implementation.
- `REFACTOR`: Cleanup is happening while the targeted proof stays green.
- `VERIFY`: Broader checks are running because the change crosses a boundary.
- `DONE`: Acceptance criteria and relevant verification have passed.

## Handoff Shape

Use this shape in a final response, status update, or repository-native
handoff artifact when one already exists:

```text
Goal:
Acceptance:
Scope:
Phase:
Changed files:
Evidence:
- RED: <command> -> <result>; artifact: <path or none>
- GREEN: <command> -> <result>; artifact: <path or none>
- REFACTOR: <command> -> <result>; artifact: <path or none>
- VERIFY: <command> -> <result>; artifact: <path or none>
Next:
Blockers:
```

## Rules

- Prefer an existing workflow CLI, hook, issue template, or handoff file if the
  repository has one.
- Do not create a new persistent state directory unless the user or repo
  convention asks for one.
- Record exact commands and results; avoid vague proof such as "tests look good."
- If paused while red, include the expected failure and why it is the intended
  failure.
- If blocked, include the missing input or external state needed to continue.
