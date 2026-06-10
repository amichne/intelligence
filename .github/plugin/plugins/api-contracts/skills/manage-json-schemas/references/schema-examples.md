# Schema Examples

Use these schemas as references when designing or reviewing schema contracts. They are checked in as `*.schema.json` files so examples match the generated output shape.

## Accepted Schemas

- [schemas/order/order.schema.json](schemas/order/order.schema.json)
- [schemas/order/created.schema.json](schemas/order/created.schema.json)
- [schemas/upload/upload.schema.json](schemas/upload/upload.schema.json)
- [schemas/upload/command.schema.json](schemas/upload/command.schema.json)
- [schemas/upload/manifest.schema.json](schemas/upload/manifest.schema.json)

## Rejected Shape

Reject this because `UPLOAD_MANIFEST` hides the hierarchy in the discriminator:

```json
{
  "type": "UPLOAD_MANIFEST",
  "storedPath": "inbox/raw/2026-05-23-compiler-gated-retrieval.md"
}
```

Prefer this:

```json
{
  "type": "UPLOAD",
  "upload": {
    "type": "MANIFEST",
    "storedPath": "inbox/raw/2026-05-23-compiler-gated-retrieval.md"
  }
}
```
