---
name: manage-json-schemas
description: Create, extract, persist, validate, update, and lifecycle JSON Schema contracts with granular examples and validator-backed checks. Use when Codex needs one authoritative workflow for schema assets, incoming JSON payload shape assertions, schema evolution, custom formats, discriminator conventions, or type-safety-driven JSON contract design. Enforces `type` as the discriminator field, discriminator schemas as `enum`, and discriminator values as CAPS_CASE.
---

# Manage JSON Schemas

Use this skill as the JSON Schema contract workflow. JSON Schema is the portable
contract. Prefer the repository-owned validator for local checks, and use the
bundled helper only for schema policy checks that the repository validator does
not own.

## Required first steps

1. Read `instructions/type-safety.md` before changing code or schema contracts.
2. Read `instructions/schema-driven-design.md` before creating or changing boundary assertions,
   serialized data contracts, API payloads, messages, or persisted record shapes.
3. Locate existing schema assets before creating new ones.
4. Confirm the repository-owned schema validation command before relying on a
   generic validator.
5. Keep schema assets in a durable repo path owned by the module or API surface.

## CLI workflow

Use the bundled helper from this skill directory when scaffolding or checking
local policy:

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js help
```

In Examplar, call the Kotlin CLI when no helper behavior is needed:

```bash
./bin/examplar check
```

Use adjacent tools by role:

- Repository validator: default authority for checked-in schema fixtures and
  publish gates.
- Ajv: useful for JavaScript runtime validation and generated standalone
  validators when the owning project is Node-based.
- Spectral: preferred policy/lint layer for conventions such as required examples, descriptions, naming, and API style.
- json-schema-faker: generated accepted samples and fixture expansion from schemas.
- quicktype or a language-specific generator: typed models and serializers from stable schemas.

## Contract design rules

- Model finite JSON shapes as discriminated unions, not loose objects plus prose.
- Use `type` as the only discriminator field.
- Define discriminator values with `enum`, not `const`.
- Write discriminator values in CAPS_CASE: `ORDER_CREATED`, `PAYMENT_FAILED`.
- Require `type` on every discriminated variant.
- Close object shapes with `additionalProperties: false` unless extension is intentional and documented in the schema description.
- Put reusable domain concepts in `$defs` or separate schemas with stable `$id`
  values.
- Add granular `examples` by default. Put examples on the top-level schema,
  nested object definitions, properties, array schemas, and item schemas when
  the schema node represents a meaningful value.
- Example values must be valid, realistic, and chosen to explain the constraint;
  do not use placeholders like `"string"` unless the literal value is meaningful.
- Prefer named schema assets over inline anonymous schemas at boundaries.
- Treat the schema as the construction boundary for incoming JSON; downstream
  code should receive parsed domain types.

## Lifecycle

When creating a schema:

1. Name the boundary and expected domain type.
2. Create or extract the schema asset.
3. Add or review granular examples for every meaningful schema node.
4. Validate the schema with the repo validator or a module-owned helper.
5. Validate at least one accepted sample and one rejected sample.
6. Wire runtime code so incoming data is validated before domain construction.

When updating a schema:

1. Identify consumers and persisted data before changing required fields or discriminator values.
2. Add optional fields compatibly when possible.
3. Add examples for new fields or variants in the same patch.
4. Create a new variant or versioned schema when meaning changes.
5. Preserve old schemas while data or clients still rely on them.
6. Validate old and new fixtures before finishing.

When retiring a schema:

1. Confirm no runtime path, fixture, doc, or client references it.
2. Remove it with the owning module changes in the same patch.
3. Run a reference sweep and the narrow validation command.

## Incoming data assertions

At runtime, validate raw JSON at the boundary and then construct typed domain
values. Do not let unvalidated JSON objects flow into business logic. If the
runtime uses a library directly, still keep a repository-owned fixture smoke
test.

## References

Read [references/schema-policy.md](references/schema-policy.md) when designing
unions, persistence layout, migration rules, or runtime validation boundaries.
Read [references/schema-examples.md](references/schema-examples.md) for helper
commands and schema snippets.
