# Source-Of-Truth Map

Use this reference when instructions must explain where authoritative edits
belong.

## Map These Surfaces

- Marketplace root: `apm.yml`.
- Package roots: `packages/*/apm.yml`.
- Primitive roots: `packages/*/.apm/skills/`, `.apm/agents/`,
  `.apm/instructions/`, `.apm/hooks/`, and `.apm/prompts/`.
- Hook scripts: `packages/*/hooks/`.
- Generated outputs: `.claude-plugin/marketplace.json`,
  `.agents/plugins/marketplace.json`, bundles, reports, and docs output.
- Runtime or installed copies: target-runtime folders, package caches, and
  local APM modules.
- Validation commands: APM marketplace checks, APM pack/audit, docs builds,
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

## APM Package Repos

For repos that curate AI tooling primitives:

- primitives live in package-owned `.apm/` trees;
- the marketplace is authored in root `apm.yml`;
- generated marketplace JSON is output from APM, not authoring source;
- runtime caches and installed bundles are provenance, not canonical source.
