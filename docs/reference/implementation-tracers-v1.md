---
type: Implementation Plan
title: Minimal V1 implementation tracers
description: Dependency-ordered Kotlin-only vertical slices for the approved skill-package product.
resource: https://github.com/amichne/intelligence/issues/40
tags: [implementation, kotlin, v1, tracer-bullets]
timestamp: 2026-07-13T01:11:59-04:00
---

# Minimal V1 Implementation Tracers

Each tracer is sized for one focused implementation session and leaves the
repository green. A tracer includes production Kotlin, tests, CLI proof where
applicable, documentation impact, and the repository verification gates. Work
starts only when its listed blockers are complete.

## Delivery Sequence

| ID | Vertical result | Blocked by | Proof |
|---|---|---|---|
| `T01` | Kotlin/JVM-only Gradle distribution and release pipeline | None | Distribution inspection, launcher smoke test, reproducible archive. **Complete:** `daa3ecc`. |
| `T02` | Typed marketplace, snapshot, package, skill, and safe-entry-path identities | `T01` | Boundary and adversarial parser tests. **Complete:** `8d07bf1`. |
| `T03` | Canonical ZIP bytes with closed file modes and trust-boundary limits | `T02` | Golden digest, raw metadata, immutability, limit, collision, and interoperability tests. **Complete:** `9b1642d`. |
| `T04` | Canonical JSON bytes, SHA-256 values, and typed size/count limits | `T02` | Property tests for key order, integer/string rules, forbidden values, and stable digests. **Complete:** `e2bb039`. |
| `T05` | Strict skill-package and exact-source parsers | `T03`, `T04` | Valid minimal fixture plus unknown-field, path, asset-closure, duplicate, and limit failures. **Complete:** `e422d13`. |
| `T06` | Deterministic package archive and marketplace-index materializer | `T05` | Two shuffled source trees produce identical complete release-neutral package bytes and index. **Complete:** package archive `5e49b32`; canonical snapshot index `8090b1d`; archive reader `d457ab0`. |
| `T07` | Complete Codex and GitHub Copilot projections with receipts | `T06` | One fixture projects to both providers; coverage, path, digest, and regeneration checks pass. **Complete:** package projections `bf337f0`; provider marketplaces `d2853e8`. |
| `T08` | Canonical all-provider local release directory transaction | `T07` | Full asset set, checksums, replacement atomicity, and repeat materialization equality. **Complete:** release kernel `f0072ae`; directory transaction `4928bd3`. |
| `T09` | Typed consumer intent, lock, journal, and recovery state machine | `T05` | Legal transition tests; invalid, missing-package, stale-lock, interrupted-transaction, and concurrency outcomes are sealed failures. **Complete:** intent `b37f0a9`; lock `20104c8`; recovery state `3692d81`. |
| `T10` | Digest-addressed cache and exact local/offline resolver | `T06`, `T09` | Cache hit, miss, corruption, reconstruction, and no-source-checkout tests. **Complete:** `ab8c617`. |
| `T11` | Atomic local-source consumer commands and explicit provider projection | `T07`, `T10` | Local `select`, `remove`, `update`, `resolve`, `recover`, `reconstruct`, and `project` prove dry-run/write parity, idempotence, rollback, and JSON/exit behavior. **Complete:** projection transaction `0f3baa5`; public command boundary `cf62a93`. |
| `T12` | Read-only GitHub discovery and exact immutable-release resolver | `T06`, `T09` | Recorded protocol fixtures prove no discovery mutation, exact identity, checksums, auth, and immutable-state failures. |
| `T13` | Immutable GitHub publisher and remote verifier | `T08`, `T12` | Preflight-before-write, ordered draft protocol, conflict, cleanup, uncertain-state, and live disposable-repository proof. |
| `T14` | Complete public CLI and remote consumer integration | `T08`, `T11`, `T12`, `T13` | Doctor, default setup, discovery, inspect, validation, author guidance, every exact GitHub consumer path, help snapshots, JSON schema, secret redaction, and exit mapping. |
| `T15` | Installed-distribution black-box acceptance suite | `T14` | All [V1 acceptance scenarios](acceptance-scenarios-v1.md) pass from the Gradle archive. |

## Slice Rules

Each tracer begins with one failing test at the public boundary, implements the
smallest typed path that makes it pass, and adds adversarial cases before
handoff. Domain validation returns proof-carrying values or sealed failures;
callers never discard a successful check and continue with the original
primitive. Filesystem and remote effects remain behind explicit transaction
and transport boundaries.

The dependency edges above are the entire V1 implementation graph. They prevent
premature coupling without introducing a package dependency solver or a second
planning system. Independent branches may proceed concurrently only after
their blockers are green.

## Verification Per Tracer

Kotlin changes run Kast workspace verification and zero-error diagnostics for
changed files, focused red-green tests, then `./gradlew :cli:test
installDevelopmentCli`. JSON or schema changes also run
`.local/intelligence/bin/intelligence validate --portable`. Documentation
changes run the knowledge-base drift check and a clean Zensical build. Release
and shell changes run their repository-specific static checks.

Only scoped files are staged. The evidence record names each requirement,
command, exit result, and any tooling concern so a later tracer can resume from
facts rather than reconstruction.

## Scope Guard

No tracer may add hooks or workflows as portable primitives, capabilities,
dependencies, semantic versions, version ranges, moving selectors, graph
traversal, per-primitive controls, provider configuration mutation, partial
provider publication, a TUI, Rust, or Python runtime content. Such work starts
only in a separately approved post-V1 contract.
