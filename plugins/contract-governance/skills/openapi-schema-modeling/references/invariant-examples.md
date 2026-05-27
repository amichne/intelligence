# Invariant Examples

These examples show schema shapes that keep OpenAPI contracts self-describing
and strongly constrained. Adapt names and constraints to the local domain.

## Constrained Primitive

```yaml
TenantId:
  type: string
  description: Stable tenant identifier.
  pattern: "^tenant_[a-z0-9][a-z0-9-]{1,62}$"
  minLength: 9
  maxLength: 70
  examples:
    - tenant_acme-ops
```

Use this instead of repeating `type: string` for tenant identifiers.

## Exclusive Variant Family

```yaml
PaymentMethod:
  oneOf:
    - $ref: "#/components/schemas/CardPaymentMethod"
    - $ref: "#/components/schemas/BankPaymentMethod"
  discriminator:
    propertyName: type
    mapping:
      CARD: "#/components/schemas/CardPaymentMethod"
      BANK: "#/components/schemas/BankPaymentMethod"

CardPaymentMethod:
  type: object
  additionalProperties: false
  required: [type, cardToken, last4]
  properties:
    type:
      type: string
      enum: [CARD]
    cardToken:
      $ref: "#/components/schemas/CardToken"
    last4:
      type: string
      pattern: "^[0-9]{4}$"
      examples: ["4242"]

BankPaymentMethod:
  type: object
  additionalProperties: false
  required: [type, accountToken, routingNumberLast4]
  properties:
    type:
      type: string
      enum: [BANK]
    accountToken:
      $ref: "#/components/schemas/BankAccountToken"
    routingNumberLast4:
      type: string
      pattern: "^[0-9]{4}$"
      examples: ["0210"]
```

Reject a payload that contains `type: CARD` plus `accountToken`. The variant
schema should make that state invalid.

## Nested Variant When A Parent Has Shared State

```yaml
JobEvent:
  type: object
  additionalProperties: false
  required: [type, id, occurredAt, job]
  properties:
    type:
      type: string
      enum: [JOB]
    id:
      $ref: "#/components/schemas/EventId"
    occurredAt:
      type: string
      format: date-time
    job:
      oneOf:
        - $ref: "#/components/schemas/JobStarted"
        - $ref: "#/components/schemas/JobFailed"
      discriminator:
        propertyName: type
        mapping:
          STARTED: "#/components/schemas/JobStarted"
          FAILED: "#/components/schemas/JobFailed"

JobStarted:
  type: object
  additionalProperties: false
  required: [type, workerId]
  properties:
    type:
      type: string
      enum: [STARTED]
    workerId:
      $ref: "#/components/schemas/WorkerId"

JobFailed:
  type: object
  additionalProperties: false
  required: [type, failure]
  properties:
    type:
      type: string
      enum: [FAILED]
    failure:
      $ref: "#/components/schemas/JobFailure"
```

Use this shape when the outer node has shared fields and the nested node owns
the variant-specific state.

## Typed Map Extension Point

```yaml
Metadata:
  type: object
  description: User-defined metadata with constrained keys and values.
  propertyNames:
    pattern: "^[a-z][a-z0-9_]{0,39}$"
  additionalProperties:
    type: string
    minLength: 1
    maxLength: 200
  examples:
    - owner: platform
      cost_center: cc_1234
```

Use a map only when open keys are part of the contract. Otherwise prefer a
closed object or an array of named entries.

## Typed Error Envelope

```yaml
ApiError:
  type: object
  additionalProperties: false
  required: [type, code, message, correlationId]
  properties:
    type:
      type: string
      enum: [API_ERROR]
    code:
      type: string
      enum: [VALIDATION_FAILED, NOT_FOUND, RATE_LIMITED, CONFLICT]
    message:
      type: string
      minLength: 1
    correlationId:
      $ref: "#/components/schemas/CorrelationId"
    retryAfterSeconds:
      type: integer
      minimum: 1
    fields:
      type: array
      items:
        $ref: "#/components/schemas/FieldError"
```

Use typed error codes and field errors instead of a freeform string response.

## Invalid-State Checklist

For each schema family, add or mentally test invalid examples for:

- missing required field;
- unknown property on a closed object;
- wrong discriminator value;
- discriminator and payload mismatch;
- malformed identifier, cursor, email, URI, date, or version;
- `null` where omission or a real value is required;
- blank string where text must be non-empty;
- array too short, too long, or with duplicates when uniqueness is required;
- map key outside the allowed key syntax;
- deprecated variant used without an intentional compatibility boundary.
