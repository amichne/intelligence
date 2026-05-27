# JSON Schema Contract Policy

Use this file for baseline schema design rules. For the bundled executable profile, read `schema-profile.md`. For hierarchical discriminator nesting and schema-tree layout, read `hierarchical-schema-layout.md`. For hooks and CI, read `enforcement.md`.

## Type-Safety Mapping

- Illegal states unrepresentable: use `oneOf` variants, required fields, enums, formats, patterns, bounds, and closed objects.
- Validate at construction: validate raw JSON before constructing domain objects.
- Single source of truth: store each schema once and reference it by `$id` or relative path.
- Sealed shapes to behavior: represent variant hierarchies with `oneOf` and a required `type` enum.
- Metadata belongs to the type: keep titles, descriptions, examples, and default behavior with the schema or generated domain type, not a parallel table.

## Discriminator Rules

For every discriminated object:

```json
{
  "type": "object",
  "properties": {
    "type": {
      "type": "string",
      "enum": ["SOME_VARIANT"]
    }
  },
  "required": ["type"],
  "additionalProperties": false
}
```

Rules:

- Use `type` as the discriminator field.
- Every object schema defines `properties.type`.
- Every object schema includes `type` in `required`.
- Every `oneOf` or `anyOf` union schema declares `discriminator: { "propertyName": "type" }`.
- Define discriminator values with `enum`, not `const`.
- Write discriminator values in CAPS_CASE.
- Require `type`.
- Do not use `kind`, `eventType`, `status`, `subtype`, `nodeType`, or ad hoc tag fields as discriminators.
- If source data arrives with another tag name, normalize it at the ingestion boundary and validate the normalized shape.

## Object Rules

- Close objects with `additionalProperties: false` unless extension is intentional and documented.
- Prefer named schemas over inline anonymous schemas at boundaries.
- Put reusable domain concepts in `$defs` or separate schemas with stable `$id` values.
- Use `format`, `pattern`, `minimum`, `maximum`, `minLength`, `maxLength`, `minItems`, and `uniqueItems` where the domain knows the constraint.

## Example Policy

Examples are part of the schema contract. They make constraints inspectable to humans, agents, generators, and documentation tools.

Default to granular examples:

- Top-level schemas get at least one complete valid payload.
- Object definitions get examples for the object shape they own.
- Every meaningful property schema gets an example value.
- Array schemas get an example array, and item schemas get an example item.
- Union schemas get examples covering common variants.
- `$ref` wrappers do not need duplicate examples when the referenced schema owns them.

Use examples that teach the constraint. Prefer `"tenant-acme"` over `"string"` and `"UPLOAD"` over `"TYPE"`.

## Extraction Rules

Generated schemas from samples are starter contracts, not final truth. After extraction:

1. Replace incidental sample literals with real domain constraints.
2. Add `minLength`, `minimum`, `format`, `pattern`, and enum constraints where the domain knows them.
3. Decide whether each observed property is required.
4. Replace weak generated examples with domain-plausible examples.
5. Add invalid samples for missing required fields, wrong discriminator values, extra properties, and malformed primitive wrappers.
6. Run policy and validation checks.

## Change Policy

Default to replacement during early iteration. A schema is allowed to reject older payloads when the current model is clearer, tighter, or easier to verify. Do not preserve stale shapes just because they existed yesterday.

Make contract changes fail fast:

- Update the schema, examples, samples, parsers, generated types, and validation checks in the same change.
- Let old payloads fail validation instead of accepting aliases, fallback fields, loose unions, or compatibility shims.
- Add or update invalid samples when an old shape is likely to be reused by mistake.
- Keep discriminator renames, required-field changes, removed fields, and tightened bounds visible in the review or commit summary.
- Run policy and validation checks before changing downstream application logic.

Avoid early-iteration migration overhead:

- Do not create versioned duplicate schemas for private or pre-release contracts.
- Do not keep old schemas, adapters, or migration code unless there is real persisted data or a real external consumer.
- Do not widen the schema to accept both old and new shapes when a hard failure would expose the issue sooner.
- Delete obsolete samples and generated artifacts in the same change that replaces the contract.

Use a migration plan only after the contract crosses a durability boundary: deployed clients, persisted production data, external consumers, audit requirements, or explicit compatibility commitments. When that boundary exists, name it, keep the old schema intentionally, and define the removal condition.
