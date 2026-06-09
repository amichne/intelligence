# Rating Rubric

Use this rubric to rate an OpenAPI specification as a boundary contract.

## Score Levels

Use `0` through `4` for each axis:

- `0`: absent or actively misleading.
- `1`: present but permissive; important behavior is prose-only.
- `2`: usable contract, but meaningful invariants are missing.
- `3`: strong local contract with typed operations, constrained schemas, and
  examples.
- `4`: first-class contract with strong schemas, invalid-state coverage, local
  validation, generation, and evolution evidence.

Use `unknown` when the evidence needed to score the axis is unavailable. Do not
average unknown axes into an overall grade.

## Axes

### 1. Boundary Authority

Checks:

- spec owner or generator is clear;
- validation command exists and runs;
- generated docs, clients, or server stubs are synchronized;
- hand-authored and generated surfaces do not drift.

### 2. Operation Completeness

Checks:

- stable `operationId`, tags, descriptions, request bodies, response bodies,
  response headers, content types, status codes, and deprecation metadata;
- path, query, header, and cookie parameters have named constraints;
- idempotency, pagination, async behavior, and caching are modeled when
  contractual.

### 3. Schema Invariants

Checks:

- required facts are required;
- objects are closed unless extension is intentional;
- identifiers, statuses, modes, and codes use named constrained schemas;
- strings, numbers, arrays, maps, nullability, and defaults are constrained;
- examples demonstrate constraints.

### 4. Variant And Subtype Modeling

Checks:

- finite families use `oneOf` with a required discriminator;
- discriminator values are finite and documented by schema, not prose;
- `allOf`, `anyOf`, and `not` are used only for their real semantics;
- nested variants are used when a parent type owns shared state;
- impossible field combinations are rejected.

### 5. Error, Security, And Failure Contracts

Checks:

- errors use a stable typed envelope or variant family;
- expected failures have status codes, typed error codes, examples, and retry
  semantics when applicable;
- security schemes, scopes, roles, and operation overrides are modeled;
- correlation, rate-limit, idempotency, and version headers are modeled when
  clients can rely on them.

### 6. Self-Description And Examples

Checks:

- descriptions explain domain meaning, not implementation trivia;
- examples exist for top-level operations, meaningful schemas, variants, and
  errors;
- examples are valid under the schema and demonstrate constraints;
- deprecated fields and operations name replacements or removal expectations.

### 7. Evolution And Compatibility

Checks:

- breaking changes are identified;
- discriminator changes, required-field changes, removed fields, and tightened
  constraints are visible;
- compatibility commitments are represented by versioning, variants, or
  migration notes;
- old permissive shapes are not retained without a named durability boundary.

## Severity Mapping

- `P0`: no schema or validation authority exists for a public API boundary, or
  the spec admits unvalidated arbitrary payloads into core behavior.
- `P1`: a schema admits a domain-impossible state, hides a finite family behind
  raw strings, or omits a required request/response/error contract.
- `P2`: the contract is usable but weak enough that clients or servers must
  re-validate facts from convention.
- `P3`: examples, naming, metadata, or compatibility notes should be tightened
  but the current schema does not admit a known invalid state.
