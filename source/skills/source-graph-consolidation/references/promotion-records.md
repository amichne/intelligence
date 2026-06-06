# Promotion Records

Use this reference when editing `garden/manifests/promotions.json`.

## Required Record Shape

Each promoted primitive needs:

- `type`: one of `SKILL`, `AGENT`, `HOOK`, `INSTRUCTION`, or `PLUGIN`.
- `name`: local canonical name.
- `canonicalPath`: path under this repository.
- `sourceRoot`: source-root name from inventory or a clear local source label.
- `sourcePath`: original source path.
- `sourceResolvedPath`: resolved absolute source path when available.
- `status`: `PROMOTED`.
- `notes`: short explanation of what was promoted or synthesized.

Use `supportingSources` when the canonical primitive synthesizes several inputs
or depends on schema, hook, or documentation evidence.

## First-Party Handling

When source material comes from OpenAI, Anthropic, or another first-party
distribution:

- use the source as provenance, not as canonical text;
- choose a local capability-oriented name that does not collide with the
  installed source;
- rewrite in this repository's voice and provider-neutral shape;
- add `firstPartyHandling` with provider, policy, source names, and canonical
  name.

The validator rejects canonical name and digest collisions with first-party
sources unless intentional handling is recorded.

## Structured Data Rule

`promotions.json` is governed by
`garden/schemas/intelligence/promotions.schema.json`. After editing it, run:

```sh
node scripts/validate-manifests.mjs
```
