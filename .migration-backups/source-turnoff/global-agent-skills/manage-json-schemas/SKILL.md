---
name: manage-json-schemas
description: Create, extract, persist, validate, update, and lifecycle JSON Schema contracts with granular examples and validator-backed checks. Use when Codex needs one authoritative workflow for schema assets, incoming JSON payload shape assertions, schema evolution, custom formats, discriminator conventions, schema-tree policy, hook enforcement, or type-safety-driven JSON contract design. Enforces `type` as the discriminator field, discriminator schemas as `enum`, discriminator values as CAPS_CASE, hierarchical discriminator nesting, and schema file-tree alignment.
---

# Manage JSON Schemas

Use this skill as the authority for JSON Schema contracts. JSON Schema is the portable contract; Ajv is the default validation engine; repo policy checks catch the modeling rules that generic validators cannot express.

## Load Only What You Need

- For baseline schema design rules, read [references/schema-policy.md](references/schema-policy.md).
- For the bundled schema profile and fixed tree policy, read [references/schema-profile.md](references/schema-profile.md).
- For hierarchical discriminator layout, nested discriminators, and schema-tree layout, read [references/hierarchical-schema-layout.md](references/hierarchical-schema-layout.md).
- For hooks, CI, and executable policy gates, read [references/enforcement.md](references/enforcement.md).
- For concrete accepted schemas and rejected payload shapes, read [references/schema-examples.md](references/schema-examples.md) and the checked-in schemas under [references/schemas/](references/schemas/).
- For the bundled starter pack, read [references/knowledge-pipeline-schema-pack.md](references/knowledge-pipeline-schema-pack.md).

## Required First Steps

1. Read `type-safety.md` before changing code or schema contracts when the repo has it.
2. Read `schema-driven-design.md` before creating or changing boundary assertions, serialized data contracts, API payloads, messages, or persisted record shapes when the repo has it.
3. Locate existing schema assets before creating new ones.
4. Confirm local schema tooling is installed with `npm install` when the repo provides a `package.json`.
5. Keep schema assets in the owning module's durable schema path, usually `schemas/`, `schema/`, `contracts/`, or `api/schemas/`.

## Core Rules

- Model finite JSON shapes as discriminated unions, not loose objects plus prose.
- Use `type` as the discriminator field on every discriminated object.
- Require every object schema to define and require `properties.type`.
- Put `discriminator: { "propertyName": "type" }` on every `oneOf` or `anyOf` union schema.
- Define discriminator values with `enum`, not `const`.
- Write discriminator values in CAPS_CASE.
- Require `type` on every discriminated variant.
- Close object shapes with `additionalProperties: false` unless extension is intentional and documented.
- Add granular `examples` on meaningful schema nodes.
- Treat raw JSON as untrusted input; validate before constructing domain types.

## Hierarchical Schema Rules

- Treat underscores in discriminator values as a modeling signal.
- If a prefix names shared semantics, model the prefix as an outer node: `type: "UPLOAD"`.
- Put the nested discriminated object under the camelCase translation of the outer discriminator: `upload` for `UPLOAD`, `knowledgeArtifact` for `KNOWLEDGE_ARTIFACT`.
- Keep nested discriminators named `type`; do not use `subtype`, `kind`, `nodeType`, or similar aliases.
- Put shared fields on the outer object and variant-specific fields in the nested key object.

## File-Tree Rules

Mirror the semantic hierarchy in the schema file tree:

```text
schemas/
  <node>/
    <node>.schema.json
    <variant>.schema.json
```

For `type: "UPLOAD"` with `upload.type: "MANIFEST"`:

```text
schemas/
  upload/
    upload.schema.json
    manifest.schema.json
```

Do not repeat the parent prefix in child filenames. Prefer `schemas/upload/manifest.schema.json` over `schemas/upload/upload-manifest.schema.json`.

## CLI Workflow

Use the bundled helper from this skill directory:

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js help
```

Validate a schema against the bundled syntax profile:

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js profile \
  --schema schemas/upload/upload.schema.json
```

Create a schema asset:

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js init \
  --name UPLOAD \
  --out schemas/upload/upload.schema.json
```

Extract a starter schema from sample JSON:

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js extract \
  --sample samples/upload-manifest.json \
  --name MANIFEST \
  --out schemas/upload/manifest.schema.json
```

Check one schema:

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js policy \
  --schema schemas/upload/upload.schema.json
```

Check the schema tree:

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js policy-tree \
  --root schemas
```

Validate data:

```bash
node .agents/skills/manage-json-schemas/scripts/schema-contracts.js validate \
  --schema schemas/upload/upload.schema.json \
  --data samples/upload-manifest.json
```

## Enforcement Default

Every repo with schema contracts should expose one command that hooks and CI can run:

```json
{
  "scripts": {
    "schema:policy": "node scripts/schema-contracts.js policy-tree --root schemas"
  }
}
```

Use the same command from pre-commit hooks and CI. Hooks are a convenience boundary; CI is the durable rejection boundary.

## Starter Pack

This skill includes a reusable knowledge-pipeline starter pack:

```text
assets/knowledge-pipeline-schema-pack/
assets/knowledge-pipeline-schema-pack.zip
```

Use it when the task is about upload/inbox/content/registry/publication contracts, or when the user wants a concrete model to adapt.
