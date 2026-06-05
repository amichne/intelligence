# Hierarchical Schema Layout

Use this reference when discriminator names, schema filenames, or directory shape are part of the design.

## Hierarchy Rule

Treat underscores in discriminator values as evidence of a possible missing parent type. A prefixed compound discriminator should only stay flat when the compound term is atomic in the domain or externally fixed. When the prefix names shared semantics, model it as the outer node.

Do not flatten shared parent semantics:

```json
{
  "oneOf": [
    { "properties": { "type": { "enum": ["UPLOAD_COMMAND"] } } },
    { "properties": { "type": { "enum": ["UPLOAD_MANIFEST"] } } }
  ]
}
```

Model the parent and nested subtype:

```json
{
  "type": "UPLOAD",
  "id": "upload_20260523_153012_compiler_gated_retrieval",
  "createdAt": "2026-05-23T15:30:12-04:00",
  "upload": {
    "type": "MANIFEST",
    "storedPath": "inbox/raw/2026-05-23-compiler-gated-retrieval.md",
    "sha256": "sha256:3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7"
  }
}
```

Use this pattern when:

- The prefix names a real domain node with fields, invariants, lifecycle, or behavior shared by the variants.
- The suffix names a node-scoped variant rather than a standalone event or record.
- Consumers should reason about the parent before variant-specific details.

## Nested Key Rule

Name the nested discriminated object by translating the outer discriminator value to camelCase:

| Outer `type` | Nested key |
|---|---|
| `UPLOAD` | `upload` |
| `KNOWLEDGE_ARTIFACT` | `knowledgeArtifact` |
| `VALIDATION_REPORT` | `validationReport` |

Keep both discriminator fields named `type`. The outer object owns the parent discriminator, and the nested object owns the variant discriminator.

## Field Placement

- Put fields shared by all child nodes on the outer object.
- Put only variant-specific fields on the nested key object.
- If the outer object has no meaningful shared fields or invariants, keep a flat discriminated union and use clear variant names without inventing an envelope.

## File Tree Rule

Schema layout should mirror the semantic hierarchy. Directories are parent nodes; schema files are concrete type files owned by that node.

```text
schemas/
  <parent-node>/
    <parent-node>.schema.json
    <variant>.schema.json
    <variant>.schema.json
samples/
  <parent-node>/
    valid/
    invalid/
```

For `type: "UPLOAD"` with `upload.type: "COMMAND"` and `upload.type: "MANIFEST"`:

```text
schemas/
  upload/
    upload.schema.json
    command.schema.json
    manifest.schema.json
samples/
  upload/
    valid/
      command.json
      manifest.json
    invalid/
      missing-upload.json
      unknown-upload-type.json
      extra-manifest-property.json
```

For compound parent discriminators:

```text
type: "KNOWLEDGE_ARTIFACT"
nested key: knowledgeArtifact
directory: schemas/knowledge-artifact/
root schema: schemas/knowledge-artifact/knowledge-artifact.schema.json
variant schema: schemas/knowledge-artifact/reference.schema.json
```

Schema directories and filenames are kebab-case. Discriminator values are CAPS_CASE. Do not repeat the parent name in child schema files when the directory already owns that namespace. Prefer `schemas/upload/manifest.schema.json` over `schemas/upload/upload-manifest.schema.json`.
