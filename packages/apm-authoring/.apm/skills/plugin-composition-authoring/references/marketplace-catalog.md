# Marketplace Catalog

Use this reference when editing the root `apm.yml` `marketplace:` block.

## Catalog Duties

The marketplace exposes local APM packages. It should list installable packages
that are useful outside this repository.

## Package Entries

Each `marketplace.packages` entry should include:

- stable `name`;
- local `source: ./packages/<name>`;
- `version`;
- concise `description`;
- `category` when Codex output is enabled;
- tags that describe capability, not provenance.

## Review Checks

- The package directory exists.
- The package has `packages/<name>/apm.yml`.
- Package primitives live under `packages/<name>/.apm/`.
- Tags are consistent with neighboring entries.
- APM validates with
  `apm pack --marketplace=all --dry-run --check-versions --json`.
