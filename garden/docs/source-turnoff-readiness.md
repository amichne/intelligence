# Source Turnoff Readiness

This generated report joins plugin coverage, review decisions, cleanup ledger entries, and runtime-link plans.

Readiness status: `NOT_READY`
Can mutate runtime without approval: `false`
Next action: Resolve blocked coverage, audit-completeness, or review-decision gates before planning runtime changes.

## Summary

| Measure | Value |
|---|---:|
| All canonical routed | `false` |
| Review completeness open | 1 |
| Source review open | 0 |
| Digest review open | 0 |
| Proposed replacements | 13 |
| Approved replacements | 0 |
| Executed cleanup entries | 33 |
| Retained external groups | 28 |
| Runtime links requiring approval | 0 |

## Gates

| Gate | Status | Evidence | Next Action |
|---|---|---|---|
| `canonical-plugin-coverage` | `BLOCKED` | garden/manifests/plugin-coverage.json has STANDALONE_ONLY canonical primitives. | Keep plugin coverage generated and checked before source replacement. |
| `review-completeness` | `BLOCKED` | 1 canonical primitives still need audit decisions. | Close review-completeness gaps before treating the source graph as fully reviewed. |
| `source-review-decisions` | `PASS` | 0 source-review entries remain open. | Keep every generated name-review group synchronized with source-review decisions. |
| `digest-review-decisions` | `PASS` | 0 digest-review entries remain open. | Keep every generated duplicate-content group synchronized with digest-review decisions. |
| `cleanup-ledger-approval` | `REVIEW_REQUIRED` | 13 proposed replacements and 0 approved replacements are recorded. | Treat PROPOSED entries as review records only; execute nothing until explicit approval changes status. |
| `external-retention` | `PASS` | 28 generated review groups are intentionally retained in external owners. | Do not replace RETAIN_EXTERNAL groups from this repository unless a new canonical promotion is reviewed. |
| `runtime-activation-approval` | `PASS` | 0 inactive runtime link or marketplace import plans still require explicit approval. | Use runtime-links.json as activation planning evidence, not as permission to write runtime paths. |

## Proposed Replacements

These entries are review records only. They do not authorize deletion or symlink writes.

| Source | Path | Canonical | Evidence |
|---|---|---|---|
| `apollo-skills` | `agent-profile-authoring` | `skills/agent-profile-authoring` | `sha256:1082a610e1b83155187bb9d1538a7b12631e93329fc95d95094f74f579bd72ad` |
| `apollo-skills` | `github-ci-operations` | `skills/github-ci-operations` | `sha256:88fc2c00d20207399b75db33be2b67e7ae8e4ec717511c008ac0825909a33603` |
| `apollo-skills` | `hook-primitive-authoring` | `skills/hook-primitive-authoring` | `sha256:467af02e05c8421bbdc9761ff1731aaff72d5e489389e5b866fafd025cf84100` |
| `apollo-skills` | `local-repository-navigation` | `skills/local-repository-navigation` | `sha256:ac83ea1916cdcab2c49e2ee63a8bca81c0fbafcf56db22c5bc0202d8f0b276d8` |
| `apollo-skills` | `plugin-composition-authoring` | `skills/plugin-composition-authoring` | `sha256:6a8090018485921007cdbd34d892a1b7d2c842e58c2f911ed3ba299c24f8c7c3` |
| `apollo-skills` | `primitive-quality-audit` | `skills/primitive-quality-audit` | `sha256:7047d5b9cc667230d64172f73819df89c44037ea298302a17f52ed47d6e082f4` |
| `apollo-skills` | `primitive-routing-evaluation` | `skills/primitive-routing-evaluation` | `sha256:c7a9697567dfe4442abe481162aae4f5724b108db671b41586bbc38d30f62e7a` |
| `apollo-skills` | `repository-signature-indexing` | `skills/repository-signature-indexing` | `sha256:8494dd6b59ead010e7f071cb22d5c85d818842801dcc7494f7e443157db83a73` |
| `apollo-skills` | `runtime-linking` | `skills/runtime-linking` | `sha256:3717e49963fb909cd5c4a62a2c21dbfe57939baa26e0ad51a0c89a4c614d1861` |
| `apollo-skills` | `shell-script-safety` | `skills/shell-script-safety` | `sha256:fee78b7f191c58520722e4ecc99cdd31ed9b668f5c4c4dba68a1993d8c361a1f` |
| `apollo-skills` | `site-docs-authoring` | `skills/site-docs-authoring` | `sha256:3d4683e1792a3ec3b33809425e80f0af61c1be1a49ce789423612b0aeeeffb00` |
| `apollo-skills` | `skill-primitive-authoring` | `skills/skill-primitive-authoring` | `sha256:e24ce9f217d02e7c5daf989e097d2c4188cea686fdbee2639bb8afc11c29e905` |
| `apollo-skills` | `source-graph-consolidation` | `skills/source-graph-consolidation` | `sha256:8c274af0389f6267860d1a5450834181babd28701897ae8b400290f2f29a7cbf` |
