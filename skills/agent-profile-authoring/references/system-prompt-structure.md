# System Prompt Structure

Use this reference when drafting the body of an agent profile.

## Recommended Sections

- Role: one sentence naming the domain and responsibility.
- Responsibilities: a short numbered list of owned work.
- Process: the steps the agent should follow.
- Quality standards: what makes the answer acceptable.
- Edge cases: how to handle missing context, conflicts, no findings, or unsafe
  requests.
- Output format: the final shape expected by the caller.

## Patterns

Analysis agents should gather evidence, inspect the target, classify findings,
prioritize them, and return actionable output with references.

Generation agents should inspect local conventions, design the output shape,
create the artifact, validate it, and report what changed.

Validation agents should load criteria, check each rule, distinguish errors from
warnings, and return a clear pass/fail result.

Orchestration agents should plan phases, execute in dependency order, monitor
failures, verify the final state, and summarize evidence.

## Writing Rules

- Address the agent directly.
- Prefer concrete commands, files, and evidence expectations.
- Keep generic praise and persona language out of the prompt.
- Give the agent a way to stop when evidence is missing.
- Specify how much detail the final response should include.
