# Bundled Schema Profile

Use this reference when replacing prose schema rules with a portable, executable contract.

## Fixed Contract

The skill ships one schema profile:

```text
references/schema-profile/schema-profile.schema.json
```

Repos should not carry customized schema profiles or project-level schema policy documents. The profile and the helper's fixed tree policy are the standard. If the standard is wrong, change this skill and let every consuming repo converge on the same contract.

## What The Profile Owns

The bundled profile constrains schema-document syntax:

- top-level schemas use a `$schema` value accepted by the bundled profile
- top-level schemas are object schemas
- schema documents have `$id` and `title`
- object schemas close with `additionalProperties: false`
- object schemas define `properties.type`
- object schemas include `type` in `required`
- discriminator fields are named `type`
- `oneOf` and `anyOf` union schemas declare `discriminator.propertyName: "type"`
- discriminator schemas use `enum`, not `const`
- discriminator values match CAPS_CASE
- meaningful schema nodes include `examples`
- only supported keywords are used

Run it directly:

```bash
node scripts/schema-contracts.js profile \
  --schema schemas/upload/upload.schema.json
```

## Fixed Tree Policy

The `policy-tree` command applies the bundled profile plus fixed cross-file rules:

```bash
node scripts/schema-contracts.js policy-tree --root schemas
```

The tree policy is intentionally not configurable. It derives `upload` from `UPLOAD`, compares enum values to filenames, checks sibling schema references, and enforces the hierarchical file tree.

Unsupported:

- `schema-policy.json`
- `schema-profile/schema-policy.schema.json`
- project-specific `schemaProfilePath`
- `--policy`, `--policy-schema`, or custom `--profile` overrides

Keep schema-policy decisions in this skill, not in each repo.
