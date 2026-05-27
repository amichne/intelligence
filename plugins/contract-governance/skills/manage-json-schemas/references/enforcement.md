# Policy Enforcement

Use this reference when turning schema guidance into hooks, local commands, or CI gates.

## Command

Repos should expose one policy command that is safe to run locally, from hooks, and from CI:

```bash
node scripts/schema-contracts.js policy-tree --root schemas
```

If the helper is not vendored into the repo, call the skill helper directly:

```bash
node <skill-dir>/scripts/schema-contracts.js policy-tree --root schemas
```

The helper enforces this skill's bundled profile and fixed tree policy. Do not add repo-local `schema-policy.json` files or custom profile overrides.

## Package Script

```json
{
  "scripts": {
    "schema:policy": "node scripts/schema-contracts.js policy-tree --root schemas"
  }
}
```

## Pre-Commit Hook

```sh
#!/bin/sh
set -eu

if git diff --cached --name-only | grep -E '^schemas/.+\.schema\.json$' >/dev/null; then
  npm run schema:policy
fi
```

## CI Gate

```yaml
- name: Check schema contracts
  run: npm run schema:policy
```

Hooks are a convenience boundary. CI is the durable rejection boundary.

## Rejected Violations

The `policy-tree` check rejects:

- schema files outside `schemas/<node>/<type>.schema.json`
- non-kebab-case schema directories or filenames
- missing root schemas for directories with variant schemas
- child schema filenames that repeat the parent prefix
- root schemas whose discriminator does not match the parent directory
- child schemas whose discriminator does not match the child filename
- child discriminator values that repeat the parent prefix
- root schemas missing the nested camelCase key for the outer discriminator
- root schemas where that nested key is not required
- root schemas whose nested union does not reference checked-in child variant schemas
- unsupported schema keywords
- schema documents that do not use a `$schema` value accepted by the bundled profile
- object schemas that do not close with `additionalProperties: false`
- object schemas that do not define and require `properties.type`
- `oneOf` or `anyOf` union schemas without `discriminator.propertyName: "type"`
- missing granular examples enforced by the node-level policy
- discriminator schemas that use `const` instead of `enum`
- discriminator values that are not CAPS_CASE
- project-specific `--policy`, `--policy-schema`, or custom `--profile` overrides

## Recommended Adoption

1. Vendor or copy `scripts/schema-contracts.js`, or call the skill helper directly.
2. Add `schema:policy` to `package.json`.
3. Add the pre-commit hook or equivalent `lefthook`, `husky`, or `pre-commit` config.
4. Add CI with the same command.
5. Add one valid sample and one invalid sample per schema hierarchy as fixtures.
6. Make schema changes fail before app code or generated types are touched.
