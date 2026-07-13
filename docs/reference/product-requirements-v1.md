---
type: Product Requirements
title: Portable marketplace V1 product requirements
description: Concise product boundary and authoritative specification index for the first useful release.
resource: https://github.com/amichne/intelligence/issues/39
tags: [product, marketplace, v1, reproducibility]
timestamp: 2026-07-13T01:11:59-04:00
---

# Portable Marketplace V1 Product Requirements

Intelligence V1 is a Kotlin command-line product that turns a validated,
provider-neutral collection of skill packages into immutable marketplace
snapshots, lets a repository select whole packages from one exact snapshot,
reconstructs locked bytes, and projects deterministic Codex and GitHub Copilot
package payloads into explicit output roots.

## Problem and Outcome

Reusable agent tooling is commonly coupled to one provider layout, mutable
repository state, or an implicit “current” version. A consumer cannot reliably
prove what it selected, recreate it offline, or project the same authored skill
package to another provider.

V1 succeeds when an author can publish one immutable, self-contained snapshot
and a consumer can select, lock, cache, validate, and reconstruct its packages
without version solving, dependency traversal, provider configuration mutation,
or access to the original authored repository.

## Actors and Owned Boundaries

| Actor | Owns | Does not own |
|---|---|---|
| Marketplace author | Provider-neutral package source, exact snapshot materialization, publication request | Consumer selection and provider installation state |
| Marketplace publisher | GitHub repository authority and immutable release operation | Building or changing release bytes during publication |
| Consumer maintainer | Repository intent, exact package set, lock evidence, and reconstruction | Marketplace source history or provider-specific authoring |
| Automation | Stable JSON envelopes, exit classes, portable validation, deterministic diagnostics | Interactive choices or inferred upgrades |
| Intelligence CLI | Typed validation, canonical bytes, resolution, atomic files, projections, and publication protocol | Hosted registry, trust service, workflow engine, or provider runtime configuration |

## Supported Flows

V1 supports readiness checks; read-only discovery; exact snapshot inspection;
first-time default setup; exact package addition, removal, or all-package
selection; explicit snapshot replacement with a stable package set; explicit
resolution and interrupted-transaction recovery; digest-addressed offline
reconstruction; selected-package projection to an explicit provider output;
portable validation; canonical package and dual-provider release materialization;
immutable GitHub publication and verification; and concise author guidance.

## Mandatory Requirements

1. The shipped CLI and its runtime distribution are Kotlin/JVM only, with no
   Rust, Python, or native runtime payload.
2. Package is the sole selection and exposure unit. Supporting assets remain
   private to the package, and V1 public primitives are skills only.
3. Persisted intent names one exact immutable source and a non-empty whole-
   package set. The lock and cache carry sufficient digest evidence to
   reconstruct without source checkout or graph traversal.
4. All untrusted bytes pass strict typed boundaries, resource limits, path
   checks, and complete schema coverage before they influence trusted state.
5. Equivalent valid input produces identical canonical JSON, archives,
   provider projections, checksums, diagnostics, and release bytes.
6. Consumer and authoring mutations, including package removal, are
   transactional. Validation or conflict failure preserves the last complete
   state; uncertain remote publication is explicit and reconcilable.
7. Every valid package projects to both required providers without changing
   provider or repository configuration.
8. GitHub publication accepts an already-complete release directory, performs
   read-only preflight first, and never replaces a tag, release, or asset.
9. The public CLI is non-interactive and automation-safe, with a versioned JSON
   envelope, stable diagnostic codes, and stable exit-code classes.
10. The black-box acceptance suite proves the installed distribution and every
    supported flow before release.

## Explicit Non-Goals

V1 does not provide package semantic versions, version ranges, “latest”,
dependency resolution, dependency locking, graph traversal, workflows,
capabilities, hooks as public primitives, per-primitive selection, partial
provider release materialization or publication, provider installation or
configuration mutation, a hosted registry, mirrors, a daemon, a TUI, or silent
contract migration.

## Authoritative Specification Index

| Concern | Authority |
|---|---|
| Product entities, skill-only scope, and reproducibility | [Portable package and marketplace V1](portable-package-marketplace-v1.md) |
| Consumer intent, lock, cache, update, reconstruction, and atomicity | [Consumer selection and reproducibility V1](consumer-selection-reproducibility-v1.md) |
| Untrusted input, schemas, limits, paths, archives, and diagnostics | [Validation trust boundary V1](validation-trust-boundary-v1.md) |
| Codex output | [Codex projection V1](codex-projection-v1.md) |
| GitHub Copilot output | [GitHub Copilot projection V1](github-copilot-projection-v1.md) |
| Canonical release directory and immutable GitHub publication | [Immutable snapshot publication V1](immutable-snapshot-publication-v1.md) |
| Commands, JSON, dry-run, offline behavior, and exit classes | [Minimal public CLI V1](cli-contract-v1.md) |
| Executable release proof | [Minimal V1 black-box acceptance scenarios](acceptance-scenarios-v1.md) |
| Delivery order | [Minimal implementation tracers](implementation-tracers-v1.md) |

Detailed contracts win over this synthesis when additional mechanics are
needed. A change that contradicts a mandatory requirement or expands a non-goal
requires an explicit V1 decision-record update before implementation.
