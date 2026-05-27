---
name: "primitive-quality-audit"
description: "Audit skills, agents, hooks, concepts, plugin manifests, and runtime-link plans before promotion, activation, or cleanup. Use when deciding whether a primitive is good enough to become canonical, whether older sources can be turned off, whether first-party material was safely renamed and rewritten, or whether a referential plugin is composed from independent primitives."
---

# Primitive Quality Audit

Use this skill as the local governance gate for AI tooling primitives. The goal
is not to score everything abstractly; it is to make defensible decisions about
promotion, runtime activation, and later cleanup from evidence.

## Operating Contract

- Audit a concrete primitive, family, plugin, or runtime-link plan.
- State the decision target up front: promote, synthesize, rewrite, defer,
  activate, prepare cleanup, or ignore.
- Compare against a baseline: existing canonical primitive, source-root
  candidate, runtime copy, installed marketplace copy, or no current equivalent.
- Keep primitives independent of plugins. Plugins may compose only primitives
  that already stand on their own.
- Treat every quality claim as evidence-bound. If evidence is thin, narrow the
  claim rather than expressing a broad positive or negative judgment.
- Do not raw-copy OpenAI, Anthropic, or other first-party content. Check names,
  content digests, and provenance before promotion.
- Treat persisted structured data as schema-driven. Any JSON, YAML, TOML, or
  generated report changed during the audit needs an owning schema, typed
  parser, generator, or validation command.
- Record durable decisions in the appropriate manifest before using them as a
  cleanup or runtime-activation basis.

## Workflow

1. Define the audit target.
   Identify primitive type, name, canonical path if it exists, source roots,
   and the decision being requested.

2. Refresh evidence.
   Run or inspect inventory and consolidation outputs before judging overlap.
   For structured manifests, confirm the validator covers every changed file.

3. Read the smallest useful source set.
   Compare the candidate, existing canonical primitive, plugin references, and
   relevant provenance. Avoid loading unrelated marketplace payloads.

4. Apply the rubric.
   Use capability boundary, trigger clarity, independence, progressive
   disclosure, schema coverage, provenance, executable evidence, and runtime
   safety as separate checks.

5. Choose the decision.
   Prefer `PROMOTE_READY` only when the primitive can stand alone, has stable
   references, records provenance, validates structured data, and can be
   composed by reference. Otherwise choose the smallest useful next action.

6. Update manifests when the decision is durable.
   Use `garden/manifests/primitive-audits.json` for quality decisions,
   `garden/manifests/promotions.json` for copied canonical primitives,
   `garden/manifests/runtime-links.json` for activation plans, and
   `garden/manifests/cleanup-ledger.json` only when cleanup is ready for explicit
   approval.

7. Validate.
   Regenerate inventory/report outputs when canonical primitives or plugins
   change, then run manifest validation and relevant syntax checks.

## Reference Routing

- Load [audit-rubric.md](references/audit-rubric.md) when deciding pass/fail
  criteria for a primitive or plugin family.
- Load [promotion-readiness.md](references/promotion-readiness.md) when choosing
  a promotion, activation, cleanup, or defer decision.
- Load [evidence-checks.md](references/evidence-checks.md) when selecting
  commands and artifacts to support the audit.

## Report Shape

Use this structure for human-facing audit reports:

```markdown
## Decision
[Decision and confidence]

## Findings
- [Finding, evidence, consequence]

## Manifest Updates
- [Files changed or entries needed]

## Validation
- [Commands run and result]

## Next Action
[One concrete next step]
```

## Completion Criteria

- The target, baseline, and decision are explicit.
- Findings are tied to file paths, manifests, command output, or source
  provenance.
- Structured data changes have schema coverage and validation evidence.
- First-party collision and raw-copy risks are checked when relevant.
- Plugin changes remain referential and do not hide primitive ownership.
- Cleanup or runtime activation is not recommended without a manifest entry and
  an approval boundary.
