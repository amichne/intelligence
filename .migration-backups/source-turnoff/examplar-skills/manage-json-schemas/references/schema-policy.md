# JSON Schema Contract Policy

## Type-safety mapping

Map `instructions/type-safety.md` and `instructions/schema-driven-design.md` principles to schema work:

- Illegal states unrepresentable: use `oneOf` variants, required fields, enums, string formats, bounds, and closed objects.
- Validate at construction: validate raw JSON before constructing domain objects.
- Single source of truth: store each schema once and reference it by `$id` or relative path.
- Sealed shapes to behavior: represent variant families with `oneOf` and a required `type` enum.
- Metadata belongs to the type: keep titles, descriptions, examples, and default behavior with the schema or generated domain type, not a parallel table.

## Example policy

Examples are part of the schema contract. They are not a replacement for
fixtures, but they make each constraint inspectable to humans, agents,
generators, and documentation tools.

Default to granular examples:

- Top-level schemas get at least one complete valid payload.
- Object definitions get examples for the object shape they own.
- Every meaningful property schema gets an example value.
- Array schemas get an example array, and item schemas get an example item.
- Union schemas get examples covering the common variants.
- `$ref` wrappers do not need duplicate examples when the referenced definition
  owns them.

Use examples that teach the constraint. Prefer `"tenant-acme"` over `"string"`,
`"ORDER_CREATED"` over `"TYPE"`, and `42` only when the number itself is
domain-plausible.

## Persistence layout

Use the owning module's existing layout. If there is no local convention, use:

```text
schemas/
  <boundary>/
    <variant>.schema.json
    <family>.schema.json
samples/
  <boundary>/
    valid/
    invalid/
```

Schema filenames should be kebab-case. Discriminator values should be CAPS_CASE. Do not encode versions into discriminator values unless version is part of the domain event identity.

## Discriminator convention

For every discriminated object:

```json
{
  "type": "object",
  "properties": {
    "type": { "type": "string", "enum": ["SOME_VARIANT"] }
  },
  "required": ["type"]
}
```

Do not use `kind`, `eventType`, `status`, or ad hoc tag fields as discriminators. If source data arrives with another tag name, normalize it at the ingestion boundary and validate the normalized shape.

## Validation command baseline

Use the repository-owned validator unless an existing schema needs a narrower
command. In Examplar source, that baseline is:

```bash
./bin/examplar check
```

The repository validator owns checked-in fixture smoke tests and publish gates.

## Tooling roles

Use the smallest tool that owns the decision being made:

- JSON Schema: portable contract source of truth.
- Repository validators: best fit for checked-in fixtures and publish gates.
- Ajv: best fit for JavaScript runtime integration and standalone validators in
  Node-owned projects.
- Spectral: best fit for style and policy rules that are not schema semantics,
  such as required examples, naming, descriptions, and OpenAPI/API consistency.
- json-schema-faker: generate valid accepted samples and broaden fixture
  coverage from schema constraints.
- quicktype or language-native generators: generate typed models and serializers
  once schemas are stable.

## Extraction rules

Generated schemas from samples are starter contracts, not final truth. After extraction:

1. Replace incidental sample literals with real domain constraints.
2. Add `minLength`, `minimum`, `format`, `pattern`, and enum constraints where the domain knows them.
3. Decide whether each observed property is required.
4. Replace weak generated examples with domain-plausible examples where needed.
5. Add invalid samples for missing required fields, wrong discriminator values, extra properties, and malformed primitive wrappers.
6. Run policy and validation checks.

## Update rules

Compatible changes:

- Add optional fields.
- Widen numeric or string bounds only when consumers tolerate the new range.
- Add a new union variant with a new CAPS_CASE discriminator.

Breaking changes:

- Rename or remove fields.
- Change a discriminator value.
- Make an optional field required.
- Change the meaning of a field without changing the schema identity.

For breaking changes, create a migration plan and keep old schemas until all persisted data and clients are migrated.
