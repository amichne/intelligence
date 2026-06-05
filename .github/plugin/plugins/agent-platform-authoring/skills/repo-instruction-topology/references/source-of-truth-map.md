# Source-Of-Truth Map

Use this reference when instructions must explain where authoritative edits
belong.

## Map These Surfaces

- Primitive roots: `source/skills/`, `source/agents/`, `source/hooks/`,
  `source/concepts/`, and equivalent local source directories.
- Composition roots: plugin manifests, marketplace catalogs, app manifests, and
  MCP configs.
- Generated outputs: API references, schema-derived types, generated docs,
  lockfiles, snapshots, bundles, and reports.
- Runtime or installed copies: symlinked skill directories, cached marketplace
  bundles, generated plugin materializations, or tool-specific runtime folders.
- Validation commands: inventory, schema validation, codegen, docs builds,
  tests, linters, and CI workflows.

Every persisted structured data surface needs an owning schema, parser,
generator, or equivalent boundary assertion. Do not document JSON/YAML/TOML
shape as prose-only policy.

## Instruction Rules

- Name the file or command that owns regeneration.
- State where hand edits belong.
- State where hand edits are forbidden or temporary.
- Link generated artifacts back to their source input.
- Keep cleanup intent in a manifest or ledger when originals should not be
  deleted yet.

## Plugin/Primitive Repos

For repos that curate AI tooling primitives:

- primitives live at direct source roots;
- plugins compose primitives by reference;
- runtime caches and first-party installed bundles are provenance, not
  canonical local source;
- cleanup waits for a reviewed promotion entry and rollback path.
