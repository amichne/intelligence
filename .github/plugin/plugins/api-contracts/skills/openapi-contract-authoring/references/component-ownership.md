# Component Ownership

Use this reference when deciding what should be reusable OpenAPI structure.

## Schema Components

Put a schema under `components/schemas` when it is any of these:

- a domain object, command, event, state, identifier, or error;
- a constrained primitive used in more than one place;
- a request or response envelope with domain invariants;
- a finite variant family;
- a shape that generated clients or docs should name.

Inline schemas are acceptable for one-off wrappers with no independent domain
meaning. If an inline schema accumulates examples, descriptions, constraints,
or reuse, promote it to a component.

## Named Primitive Components

Raw primitive schemas should be wrapped when they carry domain meaning:

```yaml
TenantId:
  type: string
  pattern: "^tenant_[a-z0-9][a-z0-9-]{1,62}$"
  minLength: 9
  maxLength: 70
  description: Stable tenant identifier.
  examples: ["tenant_acme-ops"]
```

Use named primitives for identifiers, codes, cursors, emails, slugs, versions,
money amounts, timestamps with business meaning, and externally visible modes.

## Shared Responses And Errors

- Use `components/responses` for response shapes that repeat with the same
  status semantics.
- Use `components/headers` for pagination, rate-limit, correlation, and
  versioning headers.
- Use `components/examples` only when the same example teaches a shared
  contract. Keep schema-owned examples near the schema when possible.
- Define one error envelope unless the API has genuinely different error
  families. Model error codes as enums or discriminated variants, not strings
  plus prose.

## Generated Sources

When OpenAPI is generated from code, the component owner is the typed source
model and generator. Update that source and regenerate the spec instead of
hand-editing the generated file.

When code is generated from OpenAPI, the component schema is the source model.
Tighten the schema before adding downstream checks to generated clients or
servers.
