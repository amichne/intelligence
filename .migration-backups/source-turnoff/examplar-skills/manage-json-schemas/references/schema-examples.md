# Schema Examples

## Helper Commands

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js init \
  --name ORDER_CREATED \
  --out schemas/events/order-created.schema.json

node .agents/skills/manage-json-schemas/scripts/schema-contracts.js extract \
  --sample samples/order-created.json \
  --name ORDER_CREATED \
  --out schemas/events/order-created.schema.json

node .agents/skills/manage-json-schemas/scripts/schema-contracts.js policy \
  --schema schemas/events/order-created.schema.json

node .agents/skills/manage-json-schemas/scripts/schema-contracts.js validate \
  --schema schemas/events/order-created.schema.json \
  --data samples/order-created.json
```

## Variant

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "schemas/events/order-created.schema.json",
  "title": "ORDER_CREATED",
  "type": "object",
  "properties": {
    "type": {
      "type": "string",
      "enum": ["ORDER_CREATED"],
      "examples": ["ORDER_CREATED"]
    },
    "orderId": {
      "type": "string",
      "minLength": 1,
      "examples": ["order-1001"]
    }
  },
  "required": ["type", "orderId"],
  "additionalProperties": false,
  "examples": [
    {
      "type": "ORDER_CREATED",
      "orderId": "order-1001"
    }
  ]
}
```

## Union

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "schemas/events/order-event.schema.json",
  "title": "OrderEvent",
  "oneOf": [
    { "$ref": "order-created.schema.json" },
    { "$ref": "order-cancelled.schema.json" }
  ],
  "examples": [
    {
      "type": "ORDER_CREATED",
      "orderId": "order-1001"
    },
    {
      "type": "ORDER_CANCELLED",
      "orderId": "order-1001",
      "reason": "customer-request"
    }
  ]
}
```

## Tooling Stack

Use JSON Schema as the portable source contract, then choose tools by role:

- Repository validators for checked-in fixtures and publish gates.
- Ajv for JavaScript runtime validation and standalone validators in Node-owned projects.
- Spectral for policy checks such as examples, naming, descriptions, and API style.
- json-schema-faker for generated valid samples.
- quicktype or language-specific generators for typed models and serializers.

## Runtime Smoke Test

Keep runtime validation owned by the runtime module. In Examplar source, the
checked-in fixture smoke test is the Kotlin CLI:

```bash
./bin/examplar check
```
