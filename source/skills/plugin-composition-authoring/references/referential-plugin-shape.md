# Referential Plugin Shape

Use this reference for `source/plugins/<name>/plugin.json` in this repository.

The manifest shape is governed by
`source/schemas/core/plugin.schema.json`. Validate changes with
`node scripts/validate-manifests.mjs`.

## Manifest Pattern

```json
{
  "type": "PLUGIN",
  "schemaVersion": 1,
  "name": "example-family",
  "version": "0.1.0",
  "description": "Capability family composed from independent primitives.",
  "skills": [],
  "agents": [],
  "instructions": [],
  "hooks": [],
  "metadata": {
    "composition": "referential"
  }
}
```

## Primitive References

Each referenced primitive should point to the `source/` graph root as its
source and then to the primitive path:

```json
{
  "type": "SKILL",
  "source": {
    "type": "LOCAL_SOURCE",
    "path": "./"
  },
  "path": "skills/example-skill",
  "name": "example-skill"
}
```

Use the same shape for `AGENT`, `INSTRUCTION`, and `HOOK` references with the
appropriate path and name.

## Rules

- Do not place copied skill or agent directories inside the plugin.
- Do not reference runtime caches or installed marketplace bundles.
- Do not add unsupported schema fields to satisfy a UI wish.
- If a plugin needs non-primitive resources, keep them plugin-adjacent and do
  not confuse them with primitive ownership.
