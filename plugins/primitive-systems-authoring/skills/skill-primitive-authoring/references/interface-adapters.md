# Interface Adapters

Use this reference when a skill needs runtime-specific metadata.

## Rule

Keep skill behavior portable. Runtime metadata can improve discoverability, but
it must not be required for the skill to work.

## Adapter Examples

- `agents/openai.yaml` for UI presentation in runtimes that read it.
- Plugin marketplace metadata for install and display surfaces.
- Host-specific hook files that call a provider-neutral script.

## Metadata Guidelines

- Generate metadata from the actual skill content.
- Keep frontmatter to `name` and `description` unless the target runtime
  requires more.
- Do not copy first-party display names or descriptions into local canonical
  skills.
- If metadata is omitted, the skill should still trigger and route correctly
  from `SKILL.md`.

## Review Questions

- Is this metadata an adapter, or is it changing the skill contract?
- Does the metadata duplicate content already owned by `SKILL.md`?
- Would this metadata collide with a first-party installed skill?
- Is the adapter path recorded in the plugin or marketplace that needs it?
