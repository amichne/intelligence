---
name: openapi-contract-rating
description: Rate or review OpenAPI specifications against type-driven and schema-driven criteria, with evidence-backed findings, scorecards, validation notes, and concrete contract-strengthening fixes.
---

# OpenAPI Contract Rating

Use this skill to review or rate an OpenAPI specification, generated OpenAPI
artifact, API contract pull request, or documentation surface that claims to
describe an API boundary.

Use these local concept references when they are available:

- `concepts/schema-driven-design/core.md`
- `concepts/type-safety/core.md`

## Operating Contract

- Do not give a firm quality judgment unless the object, criteria, evidence,
  baseline, and confidence are explicit.
- Rate the OpenAPI artifact as a boundary contract, not as prose quality alone.
- Prefer executable evidence: validator output, generator tests, generated
  client/server compilation, docs build, lint rules, and invalid fixtures.
- If validation cannot run, label the review as inspection-backed.
- Findings must identify the invalid or ambiguous state currently admitted and
  the concrete schema or operation change that would reject it.

## Workflow

1. Identify the review object.
   Name the spec file, generated source, PR diff, docs page, or API surface
   under review.

2. Find the baseline.
   Locate local OpenAPI version, schema dialect, validator, generator, lint
   config, previous spec, published docs, or repository rules.

3. Inspect contract strength.
   Review operations, schemas, variants, primitive constraints, errors,
   security, examples, and evolution notes.

4. Run evidence commands.
   Execute local validation or generation commands when available. Capture
   failures and do not infer green status from inspection alone.

5. Score with evidence.
   Apply the rating rubric. Mark axes unknown when evidence is missing.

6. Return findings first.
   Lead with blocking contract issues, then scorecard, validation evidence, and
   residual uncertainty.

## Reference Routing

- Load [rating-rubric.md](references/rating-rubric.md) when assigning scores or
  deciding severity.
- Load [evidence-and-output.md](references/evidence-and-output.md) when shaping
  the final review response.
- Use `skills/openapi-schema-modeling` for concrete fixes to weak schema
  variants, primitive constraints, nullability, and examples.
- Use `skills/openapi-contract-authoring` when the review turns into an
  implementation pass.

## Completion Criteria

- The review object and baseline are named.
- Each finding includes criteria, evidence, confidence, and a concrete fix.
- The scorecard distinguishes strong, weak, absent, and unknown evidence.
- Validation commands were run, or the reason they were unavailable is stated.
- Residual risk is explicit.
