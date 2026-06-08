# Agent Primitive Instructions

## Scope

This file applies to reusable agent profiles under `source/agents/`.

## Contract

- Keep each agent usable without installing any plugin.
- Store provider-neutral profile content in Markdown. Provider adapters may
  project these profiles later, but do not make provider format the source of
  truth.
- Reference local concepts with repository-relative paths such as
  `source/concepts/type-safety/core.md`.
- Do not point an agent at a runtime cache, installed plugin copy, or generated
  bundle as its authority.
- If an agent came from another source, keep public-safe provenance in the agent
  or plugin documentation.

## Output Discipline

- Review agents lead with findings, ordered by severity.
- Every finding must include the object under review, criteria, evidence,
  baseline, and confidence.
- If no finding is justified, say that directly and name residual uncertainty.

## Verify

- Run `.local/intelligence/bin/intelligence validate` after adding promoted
  agents to a plugin or marketplace manifest.
