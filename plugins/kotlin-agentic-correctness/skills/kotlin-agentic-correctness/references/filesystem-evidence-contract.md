# Filesystem Evidence Contract

Use this contract whenever Kotlin work needs structured exchange between an
agent, Kast, Gradle, hooks, scripts, or reviewers.

## State Root

Default root:

```text
.agent-turn/kotlin-agentic-correctness/
```

One session directory should contain:

```text
<timestamp>-<slug>/
  session.json
  intent.json
  kast/
    *.request.json
    *.response.json
    *.stderr.log
  logs/
    *.log
  evidence.jsonl
  scorecard.json
```

`.agent-turn/` is turn-local and untracked. Keep durable design decisions in the
repository's normal docs only after they have lasting value.

## Exchange Rules

- Write JSON request files before calling a tool with non-trivial structured
  input.
- Capture structured stdout to response files. Capture human progress and
  failure details to stderr log files.
- Store command evidence as JSON lines with command arguments, exit code, log
  paths, and notes.
- Use atomic writes for complete JSON documents.
- Reserve stdout for structured output that a caller explicitly asked for.
- Treat any JSON read from disk as untrusted until parsed successfully.

## Completion Evidence

A finished Kotlin turn should be able to point to:

- the invariant or boundary statement that guided the change;
- the Kast request and response files used for semantic discovery or
  diagnostics;
- the Gradle or test log paths for executed proof commands;
- a scorecard with no `Fail` dimensions;
- a short summary of residual `Concern` ratings, if any.
