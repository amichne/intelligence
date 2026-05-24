# Evidence Checks

Pick checks that match the primitive being audited. Do not run broad commands
only to create activity; run them because the decision depends on their result.

## Source Graph

```sh
python3 scripts/inventory-primitives.py
python3 scripts/inventory-primitives.py --check
python3 scripts/analyze-consolidation.py
python3 scripts/analyze-consolidation.py --check
```

Use these after adding, moving, renaming, or removing canonical primitive
references. The generated manifests and docs should agree with the changed
source graph.

## Structured Data

```sh
node --check scripts/validate-manifests.mjs
node scripts/validate-manifests.mjs
```

Use these after changing JSON schemas, manifests, plugin manifests, marketplace
catalogs, hook metadata, or hook adapters.

## Syntax And Scripts

```sh
bash -n hooks/*.sh
python3 -m py_compile scripts/inventory-primitives.py scripts/analyze-consolidation.py hooks/kotlin-horizontalization-check.py
node skills/manage-json-schemas/scripts/schema-contracts.js policy-tree --root skills/manage-json-schemas/references/schemas
git diff --check
```

Add domain-specific checks for the primitive under review. For example, run
language tests for a language-specific skill, hook script syntax for hooks, and
documentation site builds for docs plugins.

## Evidence Recording

Record durable audit decisions in `manifests/primitive-audits.json` when the
decision affects promotion, runtime activation, or cleanup. Store transient raw
outputs outside packaged primitive directories unless the output itself is a
maintained fixture.
