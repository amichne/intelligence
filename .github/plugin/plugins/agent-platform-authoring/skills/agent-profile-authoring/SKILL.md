---
name: "agent-profile-authoring"
description: "Design, revise, or consolidate agent profiles that are independent of plugins. Use when creating specialist agents, writing agent markdown/frontmatter, defining trigger conditions and examples, shaping system prompts, limiting tools, or validating that an agent profile is clear enough to delegate work safely."
---

# Agent Profile Authoring

Use this skill to create independent agent profiles. A plugin may refer to an
agent profile, but the agent itself must carry its role, trigger, process, tool
boundaries, and output contract.

## Operating Contract

- Make the agent profile useful without any plugin README or marketplace text.
- Name the role by the work it owns, not by a host or vendor.
- Write trigger conditions from realistic user requests and surrounding
  context.
- Keep the system prompt specific enough for delegated work, with process,
  quality bars, edge cases, and output shape.
- Limit tools when a narrower tool set reduces risk.
- Validate that the profile does not overlap confusingly with existing agents.
- Keep source provenance public-safe when material comes from another source.

## Workflow

1. Define the delegation boundary.
   State what the agent owns, what it must not own, when it should trigger, and
   what evidence it should return.

2. Check existing agents.
   Search `source/agents/` for overlapping names, triggers, or responsibilities.

3. Draft the profile.
   Use frontmatter for stable metadata and the body for the operating prompt.
   Keep provider-specific fields optional unless the target runtime requires
   them.

4. Write trigger examples.
   Cover explicit, implicit, and proactive cases when relevant. Include examples
   that show when not to delegate if the boundary is easy to over-trigger.

5. Shape the system prompt.
   Give the agent a concrete role, responsibilities, process, quality
   standards, edge cases, and output format.

6. Validate and promote.
   Check name format, trigger clarity, tool scope, and overlap. Add the agent to
   `source/adaptable.marketplace.json` or a referential plugin only after it
   exists as an independent primitive.

## Reference Routing

- Load [trigger-design.md](references/trigger-design.md) when writing or
  reviewing an agent description.
- Load [system-prompt-structure.md](references/system-prompt-structure.md)
  when drafting the agent body.
- Load [validation.md](references/validation.md) before promoting an agent or
  composing it into a plugin.

## Completion Criteria

- The agent has a distinct delegation boundary.
- Trigger examples cover realistic entry points without over-broad matching.
- The system prompt defines process, quality, edge cases, and output.
- Tool access is justified or intentionally unrestricted.
- The agent remains independent of any plugin that composes it.
