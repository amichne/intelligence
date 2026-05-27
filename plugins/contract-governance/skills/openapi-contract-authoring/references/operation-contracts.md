# Operation Contracts

Use this reference when paths and operations are the main OpenAPI work.

## Operation Identity

- Give every operation a stable `operationId` that names the domain action, not
  the current controller method.
- Use tags to group domain surfaces, not implementation packages.
- Write summaries and descriptions that state accepted inputs, observable
  effects, idempotency, and important preconditions.
- Record deprecation and replacement paths in the operation metadata when a
  public behavior is being retired.

## Parameters

- Path parameters are always `required: true` and should reference named
  constrained primitives such as `TenantId`, `OrderId`, or `CursorToken`.
- Query parameters should distinguish absence from an empty value when the
  domain cares. Avoid booleans that secretly select modes; use enums or separate
  operations when the behavior is materially different.
- Header and cookie parameters need the same schema discipline as body fields:
  formats, patterns, examples, and requiredness.
- Do not leave `schema: { type: string }` on domain identifiers, statuses, or
  modes when the accepted state space is narrower.

## Request Bodies

- Set `required: true` when the operation cannot run without a body.
- Define each supported media type explicitly. Do not imply JSON behavior from
  examples alone.
- Use a component schema for any body shape that names a domain concept, has
  invariants, or is reused.
- For polymorphic bodies, prefer `oneOf` with a required `type` discriminator
  and closed variant schemas.
- For file and multipart uploads, type every part and attach size, media-type,
  filename, checksum, or metadata constraints when the domain knows them.

## Responses

- Model every success status that clients can observe.
- `201` responses should describe the created resource or stable lookup
  metadata. Include `Location` when the API contract promises it.
- `202` responses should expose a typed operation, job, or task resource rather
  than a string message.
- `204` responses should not define a response body.
- Error responses should use a stable error family with typed codes, field
  paths, retryability, correlation identifiers, and examples.
- Include response headers when pagination, caching, rate limits, correlation,
  versioning, or idempotency metadata is contractual.

## Security

- Define security schemes in `components/securitySchemes`.
- Attach operation-level security when behavior differs from the global default.
- Do not hide authorization constraints in prose if scopes, roles, or audience
  values are finite and can be modeled.

## Evolution

- Adding a required request field, removing a response field, changing a
  discriminator, tightening a format, or changing the meaning of a stable schema
  is a breaking contract change.
- Adding an optional response field is usually compatible, but only when clients
  are contractually required to tolerate it.
- Adding a union variant is compatible only when the consumer contract includes
  exhaustive fallback behavior.
