# Local Hook Layout

Use this reference for files under `hooks/`.

## Layout

```text
hooks/
├── <name>.hook.json          # provider-neutral hook primitive metadata
├── <name>.sh|.py|.js|...     # provider-neutral implementation
└── <provider>/
    └── <name>.hooks.json     # provider adapter projection
```

## Neutral Metadata

Neutral metadata is the canonical hook primitive. It should include:

- `type: "HOOK"`;
- local source reference;
- `path` pointing at the provider adapter or canonical hook asset;
- stable hook `name`;
- optional `dependsOn` references to skills, agents, hooks, or concepts.

The metadata shape is owned by `schemas/core/hook.schema.json` and
validated by `node scripts/validate-manifests.mjs`.

Keep dependency references local and canonical:

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

## Provider Adapters

Provider adapters belong under a provider directory such as `hooks/codex/`.
They should translate the provider's event schema into a command invocation and
avoid owning reusable hook logic.

## Implementation Files

Executable implementations belong at the hook root when they are provider
neutral. They may call repo scripts, inspect state, or enforce policy, but they
should not depend on an installed plugin bundle or runtime cache.
