# Source Turnoff Readiness

This generated report joins plugin coverage, review decisions, cleanup ledger entries, and runtime-link plans.

Readiness status: `REVIEW_READY`
Can mutate runtime without approval: `false`
Next action: Review proposed cleanup-ledger replacements; do not mutate runtime paths until explicitly approved.

## Summary

| Measure | Value |
|---|---:|
| All canonical routed | `true` |
| Review completeness open | 0 |
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
| `canonical-plugin-coverage` | `PASS` | manifests/plugin-coverage.json reports all canonical primitives routed. | Keep plugin coverage generated and checked before source replacement. |
| `review-completeness` | `PASS` | 0 canonical primitives still need audit decisions. | Close review-completeness gaps before treating the source graph as fully reviewed. |
| `source-review-decisions` | `PASS` | 0 source-review entries remain open. | Keep every generated name-review group synchronized with source-review decisions. |
| `digest-review-decisions` | `PASS` | 0 digest-review entries remain open. | Keep every generated duplicate-content group synchronized with digest-review decisions. |
| `cleanup-ledger-approval` | `REVIEW_REQUIRED` | 13 proposed replacements and 0 approved replacements are recorded. | Treat PROPOSED entries as review records only; execute nothing until explicit approval changes status. |
| `external-retention` | `PASS` | 28 generated review groups are intentionally retained in external owners. | Do not replace RETAIN_EXTERNAL groups from this repository unless a new canonical promotion is reviewed. |
| `runtime-activation-approval` | `PASS` | 0 inactive runtime link or marketplace import plans still require explicit approval. | Use runtime-links.json as activation planning evidence, not as permission to write runtime paths. |

## Proposed Replacements

These entries are review records only. They do not authorize deletion or symlink writes.

| Source | Path | Canonical | Evidence |
|---|---|---|---|
| `apollo-skills` | `agent-profile-authoring` | `skills/agent-profile-authoring` | `sha256:f7789cf6996c66a8ae0d09857c127c597bbeabaa5805be3d90b481374726c4f9` |
| `apollo-skills` | `github-ci-operations` | `skills/github-ci-operations` | `sha256:88fc2c00d20207399b75db33be2b67e7ae8e4ec717511c008ac0825909a33603` |
| `apollo-skills` | `hook-primitive-authoring` | `skills/hook-primitive-authoring` | `sha256:e22bd11538027c86a30c78e5cf1c342b5523dfc976ea3021caf101787963ce71` |
| `apollo-skills` | `local-repository-navigation` | `skills/local-repository-navigation` | `sha256:ac83ea1916cdcab2c49e2ee63a8bca81c0fbafcf56db22c5bc0202d8f0b276d8` |
| `apollo-skills` | `plugin-composition-authoring` | `skills/plugin-composition-authoring` | `sha256:cb665208e75877050d2c2fbb6ac4d08deb0241afbd3c254497c28becc0b886f6` |
| `apollo-skills` | `primitive-quality-audit` | `skills/primitive-quality-audit` | `sha256:4ee7ae20c6010911f68c103495ddf8a93080b378cd2330797c35ca5214ee0a97` |
| `apollo-skills` | `primitive-routing-evaluation` | `skills/primitive-routing-evaluation` | `sha256:c7a9697567dfe4442abe481162aae4f5724b108db671b41586bbc38d30f62e7a` |
| `apollo-skills` | `repository-signature-indexing` | `skills/repository-signature-indexing` | `sha256:8494dd6b59ead010e7f071cb22d5c85d818842801dcc7494f7e443157db83a73` |
| `apollo-skills` | `runtime-linking` | `skills/runtime-linking` | `sha256:58e05aee752728295f29ee6327d4101634b15847818a967c53035f0a28e1ead4` |
| `apollo-skills` | `shell-script-safety` | `skills/shell-script-safety` | `sha256:fee78b7f191c58520722e4ecc99cdd31ed9b668f5c4c4dba68a1993d8c361a1f` |
| `apollo-skills` | `site-docs-authoring` | `skills/site-docs-authoring` | `sha256:3d4683e1792a3ec3b33809425e80f0af61c1be1a49ce789423612b0aeeeffb00` |
| `apollo-skills` | `skill-primitive-authoring` | `skills/skill-primitive-authoring` | `sha256:acc08f67edeac53ff96d28117913c11e55abc3a144724077bb6e8ba376acdee7` |
| `apollo-skills` | `source-graph-consolidation` | `skills/source-graph-consolidation` | `sha256:86dbd7f4878f531348acb3daf82db00fc25c1cf797d0335b77fc44e14c726f7e` |
