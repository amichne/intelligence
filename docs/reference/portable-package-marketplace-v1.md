---
type: Decision Record
title: Portable package and marketplace V1 contract
description: Minimal provider-neutral domain for immutable, digest-addressed marketplace packages.
resource: https://github.com/amichne/intelligence/issues/33
tags: [marketplace, package, primitives, v1, reproducibility]
timestamp: 2026-07-12T23:13:11-04:00
---

# Portable Package and Marketplace V1 Contract

This decision record defines the smallest provider-neutral domain that the
first useful release must implement. It is authoritative for V1 package and
marketplace behavior. Later contracts may add dependency and version
management through an explicit contract-version change; they must not silently
expand this surface.

## Product Boundary

V1 publishes immutable marketplace snapshots containing independently
selectable packages. A consumer selects whole packages and records exact
content evidence. Provider projections translate each selected package into a
Codex or GitHub Copilot payload without making the provider layout part of the
portable package model.

Reproducibility comes from immutable snapshot identity and content digests. V1
does not implement a package version solver, dependency graph, workflow engine,
or capability system.

## Domain Entities

The V1 domain contains five entity types. Their identities are values in the
contract, not filesystem paths or generated-provider directories.

| Entity | Identity | Required role |
|---|---|---|
| Marketplace | Immutable opaque marketplace ID | Stable namespace independent of GitHub or local filesystem location. |
| Marketplace snapshot | Marketplace ID plus immutable release identity and index digest | Self-contained publication and discovery unit. |
| Package | Marketplace ID plus package name | Sole selection, exposure, and installation unit. |
| Primitive | Package ID plus primitive kind plus primitive name | Typed public member of exactly one package. |
| Supporting asset | Content digest within its owning package bundle | Private implementation content reachable only through its owning primitive. |

Transport coordinates locate an entity but never define it. Moving or
mirroring a marketplace therefore cannot silently change package identity.

Marketplace, snapshot, package, and skill IDs match
`[a-z0-9]+(?:-[a-z0-9]+)*` and are at most 64 characters. A GitHub publication
uses the snapshot ID as its exact release tag. The identifier is opaque: it has
no ordering or semantic-version meaning.

## Marketplace Snapshot

Each marketplace release publishes one complete immutable snapshot. Its index
must contain:

- the persisted contract version;
- the marketplace ID and immutable release identity;
- exactly one default package ID;
- every package exposed by that snapshot;
- each package bundle's artifact reference, byte size, and content digest.

The release checksum manifest covers the index and every referenced artifact.
The index cannot contain its own digest because that would create a recursive
content definition.

The index is a complete snapshot, not a delta. Reading it must never require an
earlier marketplace release. A later snapshot may expose a different package
set, but it cannot mutate an earlier snapshot or reuse an earlier digest for
different bytes.

## Package Contract

A package is the only public selection and exposure unit. Its canonical bundle
contains:

- package ID and descriptive metadata;
- discovery-only tags;
- a closed collection of typed primitive definitions; and
- every supporting asset referenced by those primitives, including its relative
  path, byte size, SHA-256 digest, and executable bit.

The package description is non-empty and at most 1,024 characters so it can be
copied to both provider manifests without truncation.

The SHA-256 digest of the complete canonical provider-neutral ZIP bytes is the
package digest. This includes ZIP metadata as well as file content. Changing the
manifest, a primitive definition, a supporting asset, or canonical archive
metadata therefore produces a different package digest in a new marketplace
snapshot.

V1 packages do not declare dependencies. A package cannot reference primitives
owned by another package, whether directly or transitively. This removes
dependency discovery, network graph traversal, cycle handling, version
conflicts, and cross-package reach-through from the first implementation.

## Primitive Contract

The sole V1 primitive kind is `skill`.

Unknown primitive kinds fail validation. Skill names are unique within a
package. Every declared skill is public, discoverable, and included when its
package is selected. There are no per-skill visibility, selection, or version
controls.

Supporting assets are not primitives. They have no public name, version,
dependency coordinate, or exposure lifecycle. They are immutable package
content and are accessible only through a primitive definition.

Agent profiles, hooks, instructions, prompts, concepts, schemas, documents,
scripts, and other resources may be private supporting files owned and consumed
by a skill. They are not standalone V1 primitives. This is deliberate: Codex
and GitHub Copilot share the Agent Skills package shape, while their lifecycle
hook protocols differ in event, matcher, output, failure, trust, shell, and
cloud-execution semantics. Treating those surfaces as portable would either
make some valid packages unprojectable or silently change behavior.

The skill directory name and frontmatter name are identical. Skill descriptions
are non-empty and at most 1,024 characters. These are the strict common provider
boundaries; projection never sanitizes or truncates identity.

## Reproducibility Rules

Consumer and publication contracts must preserve these domain invariants:

1. A selected marketplace snapshot is resolved by exact immutable release
   identity, never by a moving `latest` alias.
2. A selected package is verified by the exact digest and size recorded in the
   snapshot index.
3. A lock records exact snapshot and package evidence; validation never
   substitutes different content.
4. Cached content is addressed by digest and reused only after digest and size
   verification.
5. Missing or mismatched content fails explicitly without mutating consumer
   intent, locks, cache metadata, or generated provider output.

An update is an explicit move from one immutable marketplace snapshot to
another followed by creation of new exact lock evidence. It is not semantic
version resolution.

## Exposure and Discovery

Marketplaces expose whole packages only. Each snapshot selects exactly one
default package for first-time setup. Selecting “all” means every package
listed as exposed in that exact snapshot.

Tags support search and presentation only. They never participate in identity,
selection, defaulting, locking, or integrity checks.

## Deferred Beyond V1

The following behavior is intentionally absent from V1:

- semantic package versions, ranges, constraints, catalogs, pins, unpins, and
  version-conflict resolution;
- package dependencies, transitive resolution, dependency locking, and
  dependency graph traversal;
- version inventories, yanking, deprecation, or historical-version lifecycle;
- capability declarations, capability matching, provider selection, or
  substitution;
- workflow primitives, workflow graphs, conditions, loops, retries, dataflow,
  or execution semantics;
- public lifecycle hooks or provider-specific executable plugin components;
- per-primitive selection, visibility, dependencies, or independent versions;
  and
- provider-specific installation or runtime-configuration mutation.

These are potential later contract versions, not latent V1 requirements. A
future proposal must justify its use case, failure modes, security boundary,
and effect on reproducibility before adding any of them.

## Decision History

The human-led domain session established the following choices. The final three
rows intentionally supersede earlier exploratory decisions.

| Choice | V1 disposition |
|---|---|
| Package is the sole public selection and exposure unit. | Accepted. |
| Primitive identity includes package, kind, and name. | Accepted. |
| Primitive kinds form a closed contract-versioned set. | Accepted; narrowed to skill after provider comparison exposed incompatible lifecycle-hook semantics. |
| Tags are discovery metadata only. | Accepted. |
| Exactly one default package exists per marketplace snapshot. | Accepted. |
| Supporting assets remain private package content. | Accepted. |
| Marketplace identity is opaque and independent of transport. | Accepted. |
| Release indexes are complete immutable snapshots. | Accepted. |
| A digest covers the full provider-neutral package bundle. | Accepted. |
| Capabilities validate cross-package compatibility. | Superseded; capabilities are absent from V1. |
| Workflows form typed acyclic primitive graphs. | Superseded; workflows are absent from V1. |
| Packages use semantic versions, constraints, dependencies, and exact transitive locks. | Superseded; V1 uses snapshot and content identity without package dependency resolution. |

## Sources and Validation

This contract records the decisions from [Define the portable package and
marketplace domain](https://github.com/amichne/intelligence/issues/33) under the
[Wayfinder map](https://github.com/amichne/intelligence/issues/30). GitHub
transport mechanics remain documented in [GitHub marketplace release
mechanics research](github-release-mechanics-research.md). Provider mappings
remain documented separately in [Codex projection
research](codex-projection-research.md) and [GitHub Copilot projection
research](github-copilot-projection-research.md).

Validate this reference page with:

```sh
python3 /Users/amichne/.codex/plugins/cache/slopsentral/code-knowledge-base/0.1.0/skills/code-knowledge-base/scripts/code_kb.py check --repo . --docs docs
zensical build --clean
```
