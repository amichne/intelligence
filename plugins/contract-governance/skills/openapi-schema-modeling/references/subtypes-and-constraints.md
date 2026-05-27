# Subtypes And Constraints

Use this reference to choose the OpenAPI schema construct that makes a domain
invariant visible to tools.

## Primitive Types

### String

Use strings for text, identifiers, timestamps, slugs, tokens, codes, and enums
only after naming the domain concept.

Add the strongest known constraints:

- `enum` for finite values;
- `format` for standardized shapes such as `date-time`, `email`, `uuid`, or
  `uri` when the local validator supports the format;
- `pattern` for domain syntax such as tenant IDs, slugs, semantic versions, or
  opaque cursor prefixes;
- `minLength` and `maxLength` for storage, display, protocol, or domain limits.

Reject blank strings with `minLength` or a pattern when blank is not meaningful.

### Integer And Number

Use numeric bounds and divisibility instead of prose:

- `minimum`, `exclusiveMinimum`, `maximum`, `exclusiveMaximum`;
- `multipleOf` for cents, increments, page sizes, or rate units;
- `format` only when the local OpenAPI profile and generator interpret it
  consistently.

Money should usually be a named object or decimal string with currency and
precision rules, not a bare floating-point number.

### Boolean

Use booleans only for real two-state facts. If a boolean selects behavior,
authorization, lifecycle, retryability, or compatibility mode, prefer an enum,
variant, or separate operation.

### Null

Model null only when null is a domain value. Distinguish:

- omitted field: value is not supplied;
- `null`: value is explicitly unknown, cleared, or not applicable;
- empty string or empty array: value is supplied and empty;
- invalid value: boundary should reject it.

OpenAPI 3.1 and newer can use JSON Schema null modeling. OpenAPI 3.0 projects
often require `nullable: true`; honor the repository's active profile.

## Compound Types

### Object

- Require every domain-required property.
- Use `additionalProperties: false` unless the object is a typed extension
  point.
- Put variant-specific required fields on the variant schema, not the shared
  parent.
- Avoid catch-all `object` schemas for request bodies and responses.

### Array

- Define `items` with a named schema when item shape has domain meaning.
- Use `minItems`, `maxItems`, and `uniqueItems` when the domain knows the
  cardinality.
- If ordering matters, state it in the description and model any sortable key.
- If the list is paged, model the page envelope and cursor constraints.

### Map

- Use maps only when keys are open by contract.
- Type the values with `additionalProperties: { $ref: ... }` or an equivalent
  constrained schema.
- Constrain keys with `propertyNames` when the active OpenAPI profile supports
  it.
- Prefer an array of named entries when keys have lifecycle, metadata, ordering,
  or validation beyond simple syntax.

## Variant And Composition Constructs

### `oneOf`

Use `oneOf` for mutually exclusive legal variants. Add a discriminator for
finite families, require the discriminator, and keep each variant closed.

### `allOf`

Use `allOf` for true intersection: every branch must be valid at once. Do not
use it as casual inheritance if it makes required fields, closure, examples, or
generator output unclear.

### `anyOf`

Use `anyOf` only when multiple branches may be valid simultaneously and clients
are expected to handle that overlap. If exactly one branch should match, use
`oneOf`.

### `not`

Use `not` sparingly for simple, validator-backed exclusions. Prefer positive
variants and required fields when the illegal state can be modeled directly.

## Metadata With Contract Force

- `readOnly` and `writeOnly` are boundary direction constraints. Keep request
  and response schemas separate when a generator or validator handles them
  ambiguously.
- `default` is not a hidden required value. Define whether the server applies
  the default, the client may omit the field, and where the default owner lives.
- `deprecated` should include replacement guidance in the schema or operation
  description.
- `examples` should be valid and should teach a constraint.
