---
name: openapi-schema-modeling
description: Design OpenAPI component schemas with discriminated variants, constrained primitives, subtype/composition choices, nullability, examples, and invalid-state prevention.
---

# OpenAPI Schema Modeling

Use this skill when the hard part of an OpenAPI task is modeling the schema
state space: subtypes, discriminators, constraints, nullability, examples,
extension points, or reusable component ownership.

Use these local concept references when they are available:

- `concepts/schema-driven-design/core.md`
- `concepts/type-safety/core.md`

## Operating Contract

- Model the domain concept before choosing the transport shape.
- Prefer named component schemas over anonymous inline object graphs at API
  boundaries.
- Use `type` as the discriminator field for finite families unless an existing
  external contract forces another tag; if so, name the exception.
- Use `oneOf` for exclusive legal variants. Use `allOf` only for true
  intersection/composition. Use `anyOf` only when multiple branches may be true
  at the same time by contract.
- Close object schemas unless an extension point is intentional and typed.
- Encode every known primitive constraint: pattern, format, enum, bounds,
  length, cardinality, uniqueness, defaults, examples, and deprecation.
- Treat examples as part of the contract. Include examples that demonstrate the
  constraint, not placeholder values.

## Workflow

1. Name the concept and state space.
   Identify finite variants, required facts, impossible combinations, null
   semantics, collection rules, maps, and extension points.

2. Choose the schema construct.
   Select primitive constraints, object shape, array shape, map shape, `oneOf`,
   `allOf`, `anyOf`, or `not` based on the actual domain semantics.

3. Define reusable components.
   Move identifiers, codes, envelopes, state machines, and variant families into
   named components.

4. Attach constraints and examples.
   Add meaningful descriptions, examples, required fields, closed objects, and
   local compatibility notes.

5. Check invalid states.
   Ask which payloads should fail: missing discriminator, wrong variant payload,
   extra property, malformed primitive, illegal null, blank string, duplicate
   array item, or impossible field combination.

6. Validate with the local profile.
   Run the repository's OpenAPI validator, schema validator, generator, or
   linting command. If no command exists, state that the modeling is
   inspection-backed only.

## Reference Routing

- Load [subtypes-and-constraints.md](references/subtypes-and-constraints.md)
  when choosing primitive, object, array, map, union, nullability, or
  composition constructs.
- Load [invariant-examples.md](references/invariant-examples.md) when you need
  concrete OpenAPI schema examples for discriminated variants, nested variants,
  constrained primitives, typed maps, typed errors, and invalid-state checks.
- Use `skills/manage-json-schemas` when standalone JSON Schema policy,
  hierarchical discriminator layout, or schema-tree validation is needed.

## Completion Criteria

- Every meaningful domain concept has a named schema or a justified inline
  shape.
- The schema rejects the illegal states the domain can name.
- Finite variants are explicit and discriminator-driven.
- Nullable, optional, empty, blank, and invalid states are not conflated.
- Examples cover representative variants and constraints.
- Local validation has run, or the missing validation path is named.
