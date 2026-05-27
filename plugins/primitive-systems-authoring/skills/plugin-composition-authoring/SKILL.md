---
name: "plugin-composition-authoring"
description: "Create, revise, or validate referential plugin manifests that compose existing skills, agents, hooks, and concepts without owning those primitives. Use when adding a plugin family, updating marketplace entries, checking plugin references, or converting payload-style plugin bundles into direct primitive references."
---

# Plugin Composition Authoring

Use this skill to create plugin manifests that assemble primitives already
owned elsewhere in the repo. The plugin is the installable composition surface;
the primitive remains usable without the plugin.

## Operating Contract

- Do not copy primitive payloads into a plugin folder.
- Reference direct primitive roots such as `skills/*`, `agents/*`, `hooks/*`,
  and `concepts/*` inside the repository `source/` graph.
- Keep plugin manifests small and declarative.
- Treat plugin manifests and marketplace catalogs as structured data governed by
  repo-local schemas. Shape changes require schema changes or validation
  evidence, not prose-only agreement.
- Add marketplace entries only after the plugin manifest exists.
- Keep source provenance public-safe; the plugin itself should not be the only
  place provenance lives.
- Validate against the repository schema and local reference checks.

## Workflow

1. Define the plugin family.
   Name the composed capability set, the primitives it should assemble, and
   what should remain outside the plugin.

2. Confirm primitive independence.
   Verify every skill, agent, hook, and concept exists under the canonical root
   and makes sense without the plugin.

3. Write `source/plugins/<name>/plugin.json`.
   Use local references to existing primitives. Keep metadata limited to
   composition, version, and description.

4. Update `source/adaptable.marketplace.json`.
   Add or update one plugin entry and any newly promoted primitive entries.
   Preserve existing ordering unless a new order is part of the request.

5. Validate.
   Run manifest validation.

## Reference Routing

- Load [referential-plugin-shape.md](references/referential-plugin-shape.md)
  when creating or reviewing a plugin manifest in this repository.
- Load [marketplace-catalog.md](references/marketplace-catalog.md) when editing
  `source/adaptable.marketplace.json` or plugin catalog metadata.

## Completion Criteria

- The plugin references only existing independent primitives.
- No plugin-payload copy is introduced.
- `source/adaptable.marketplace.json` and plugin manifest references validate.
- First-party source handling remains clean.
