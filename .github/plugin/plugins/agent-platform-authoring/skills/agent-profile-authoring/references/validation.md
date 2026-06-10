# Agent Validation

Use this reference before promoting an agent profile.

## Metadata Checks

- Name is lower-case, hyphenated, and specific.
- Description states trigger conditions and useful examples.
- Optional model, color, or tool metadata matches the target runtime.
- Tool access is minimal for the work, or unrestricted access is justified by
  the role.

## Boundary Checks

- The agent has a reason to exist separately from the main agent.
- Its trigger does not collide with existing agents unless the overlap is
  intentional and documented.
- It does not depend on a plugin-local README or marketplace entry to explain
  its behavior.
- It knows when to decline or hand work back.

## Prompt Checks

- Responsibilities are concrete.
- Process steps are ordered and evidence-seeking.
- Output format is explicit.
- Edge cases include missing context and no-findings paths.
- The profile can be reviewed without conversation history.

## Promotion Checks

- Add the agent to `source/adaptable.marketplace.json` only after the file
  exists under `source/agents/`.
- Compose it into plugin manifests by reference.
- Record source provenance in public-safe form.
- Validate manifests.
