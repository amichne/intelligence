# Trigger Design

Use this reference when writing agent descriptions and examples.

## Trigger Contract

An agent description should answer:

- what work the agent owns;
- which user requests should trigger it;
- which surrounding context should trigger it proactively;
- which similar requests should stay with the main agent or another specialist;
- what the main agent should say before delegation.

## Example Types

- Explicit request: the user directly asks for the agent's work.
- Implicit request: the user's wording points to the work without naming it.
- Proactive follow-up: recent edits, tool output, or validation results make a
  specialist review useful.
- Non-trigger: a nearby request belongs elsewhere.

## Example Shape

Use examples with enough context to teach routing:

```markdown
<example>
Context: A pull request changes workflow YAML and package scripts.
user: "Can you figure out why CI is red?"
assistant: "I'll use the CI operations agent to inspect the failing checks."
<commentary>
The request is about GitHub check failure triage, which matches the agent's
boundary.
</commentary>
</example>
```

## Quality Checks

- Examples use realistic wording, not only canonical labels.
- Triggers are narrow enough to avoid stealing unrelated work.
- Similar agents have clear boundaries.
- The description includes both reactive and proactive routes when needed.
