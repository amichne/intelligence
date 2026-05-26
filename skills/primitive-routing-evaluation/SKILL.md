---
name: "primitive-routing-evaluation"
description: "Diagnose and improve when skills, agents, hooks, or plugin workflows load too rarely, load in the wrong situations, or get bypassed by generic tool use. Use when building routing eval cases from session exports, logs, user corrections, or missed triggers, and when deciding how to tighten primitive descriptions without overfitting."
---

# Primitive Routing Evaluation

Use this skill to turn routing misses into durable evaluation evidence. A
routing miss is any case where the right primitive was not loaded, the wrong
primitive was loaded, a loaded primitive was bypassed, or a provider adapter
hid the canonical local primitive.

Keep the workflow provider-neutral. Host-specific exports, logs, adapters, and
tool traces are evidence; the canonical output is a sanitized eval case tied to
the independent primitive that should own the work.

## Operating Contract

- Treat raw transcripts, session exports, tool traces, and process logs as
  immutable evidence.
- Promote only sanitized, durable examples into checked-in eval corpora.
- Keep local absolute paths, secrets, private code snippets, and full command
  output out of durable routing cases.
- Evaluate routing against the canonical primitive, not the plugin or provider
  adapter that happened to expose it.
- Preserve negative evidence. A loaded-but-bypassed primitive is a distinct
  failure from a trigger miss.
- For any persisted routing corpus, use a schema, typed parser, generated
  model, or equivalent boundary assertion before writing cases.

## Workflow

1. Identify the decision target.
   Name the primitive family, current trigger surface, expected primitive, and
   observed route. Decide whether the goal is to tighten a skill, agent, hook,
   plugin composition, or runtime adapter.

2. Collect the smallest useful evidence set.
   Prefer a user prompt plus the loaded primitives and tool sequence. Add host
   logs only when the prompt and tool trace do not explain the miss.

3. Classify the miss.
   Use one primary class:

   - `TRIGGER_MISS`: the expected primitive never loaded.
   - `WRONG_PRIMITIVE`: a related but incorrect primitive loaded.
   - `LOADED_BYPASSED`: the expected primitive loaded but generic tools or an
     unrelated workflow did the work.
   - `ADAPTER_DRIFT`: provider metadata routes away from the canonical local
     primitive.
   - `SCHEMA_FRICTION`: structured input or output could not pass the expected
     boundary contract.
   - `SETUP_FRICTION`: initialization or dependency state blocked the intended
     primitive before it could prove behavior.

4. Sanitize into an eval case.
   Keep the user intent realistic and concrete. Replace local paths, customer
   names, tokens, and large source snippets with neutral placeholders. Preserve
   enough shape for the route decision to remain testable.

5. Encode expected behavior.
   State the expected primitive, allowed tool families, forbidden generic
   fallbacks, required recovery behavior, and evidence that would prove the
   route improved.

6. Choose the narrowest fix surface.
   Update `SKILL.md`, agent frontmatter/body, hook metadata, plugin composition,
   or provider adapter only when that surface explains the miss. Avoid changing
   several surfaces from one eval case unless the evidence requires it.

7. Re-measure.
   Run the local routing eval command or replay harness when one exists. If no
   harness exists yet, record the eval case and the manual evidence needed to
   validate the next pass.

## Routing Case Shape

Persisted routing cases should have a schema-backed shape equivalent to:

- `type`: one of the classification values above.
- `source`: sanitized evidence source and capture date.
- `prompt`: the sanitized user request or surrounding context.
- `expectedPrimitive`: canonical primitive name and type.
- `observedRoute`: loaded primitive, adapter, or tool path that actually ran.
- `allowedActions`: tool families or primitive actions that satisfy the route.
- `forbiddenActions`: generic fallbacks or incorrect primitives to reject.
- `recoveryExpectation`: what should happen after setup or schema friction.
- `notes`: why this case should remain in the durable corpus.

Do not store this as untyped JSON. Add or reuse a schema before committing a
corpus file.

## Source Promotion Guidance

When consolidating from repo-specific routing playbooks:

- Keep repo names and command examples only as provenance in manifests.
- Generalize from the failure class, not from one repository's directory layout.
- Preserve the handoff process: raw evidence becomes sanitized cases; cases
  drive the narrowest prompt, metadata, or adapter change; fresh runs prove the
  change.
- Keep old repo-local maintenance fixtures in place until their owning repo has
  its own cleanup approval.

## Completion Criteria

- The routing miss has one primary classification.
- The expected primitive and observed route are explicit.
- Durable cases are sanitized and schema-backed.
- The proposed fix surface is the narrowest one supported by evidence.
- Re-measurement evidence or the missing validation command is recorded.
