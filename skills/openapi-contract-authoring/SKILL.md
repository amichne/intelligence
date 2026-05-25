---
name: openapi-contract-authoring
description: Author or revise OpenAPI specifications with schema-driven operation contracts, invariant-filled component schemas, typed errors, examples, and validation/generation evidence.
---

# OpenAPI Contract Authoring

Use this skill when creating or revising an OpenAPI contract that should be a
first-class boundary assertion, not a loose description of handler behavior.

Use these local concept references when they are available:

- `concepts/schema-driven-design/core.md`
- `concepts/type-safety/core.md`

## Operating Contract

- Treat the OpenAPI document, its generator, or its typed source model as the
  API boundary authority.
- Locate the existing spec owner before editing: source annotations, generated
  code, checked-in YAML, tests, linter config, or docs pipeline.
- Prefer OpenAPI 3.1 or newer when the local toolchain supports JSON Schema
  semantics. If the repository is pinned to OpenAPI 3.0, state that profile and
  use the closest supported representation intentionally.
- Move domain facts into schemas: required fields, variants, formats, numeric
  bounds, collection cardinality, nullability, examples, deprecation, and error
  shapes.
- Keep reusable domain concepts in `components/schemas`; do not duplicate the
  same primitive constraints across operations.
- Require explicit request, response, error, security, and status-code models
  for every public operation.
- Validate the spec and any generated docs or clients before calling the change
  complete.

## Workflow

1. Find the authority.
   Identify whether the spec is hand-authored or generated, where schemas live,
   and which command validates or regenerates the contract.

2. Set the OpenAPI profile.
   Record the OpenAPI version, supported schema dialect, validator, generator,
   and any local rules that constrain keywords.

3. Model domain shapes first.
   Name the domain concepts, finite variants, constrained primitives, state
   transitions, nullable states, and extension points before writing paths.

4. Author operations from typed boundaries.
   For each operation, define parameters, request body, response bodies,
   headers, errors, security requirements, examples, and operation metadata.

5. Tighten the schemas.
   Replace raw strings, loose objects, and prose-only invariants with reusable
   component schemas, discriminated variants, closed objects, and constraints.

6. Prove the contract.
   Run the local validator, generator, docs build, client generation, tests, or
   lint command that proves the OpenAPI artifact is accepted by the repo.

## Reference Routing

- Load [operation-contracts.md](references/operation-contracts.md) when shaping
  paths, parameters, request bodies, responses, status codes, errors, and
  security.
- Load [component-ownership.md](references/component-ownership.md) when deciding
  what belongs in `components/schemas`, reusable parameters, responses, headers,
  examples, or generated models.
- Use `skills/openapi-schema-modeling` when schema subtype, union, constraint,
  or example modeling is the central work.
- Use `skills/openapi-contract-rating` when the task is to score or review an
  existing specification.

## Completion Criteria

- The spec has a clear owning source and validation path.
- Every operation has typed request, response, error, and security behavior.
- Reusable domain constraints live in components or a generated typed source.
- Finite families use explicit variants rather than raw tag strings.
- Meaningful schemas include examples that demonstrate their constraints.
- Local validation, generation, or linting has run, or the missing command is
  named as residual risk.
