---
type: Test Strategy
title: Minimal V1 black-box acceptance scenarios
description: Small end-to-end scenario set proving the skill-only V1 through the installed Kotlin CLI.
resource: https://github.com/amichne/intelligence/issues/42
tags: [acceptance, cli, v1, reproducibility]
timestamp: 2026-07-13T01:11:59-04:00
---

# Minimal V1 Black-Box Acceptance Scenarios

These scenarios are the release-level proof for V1. They invoke the installed
distribution as an external process, inspect only public output and filesystem
state, and never call Kotlin implementation classes directly.

## Harness Contract

The hermetic suite starts from the Gradle distribution archive, extracts it
into an empty directory, and runs its `bin/intelligence` launcher with a
temporary home, cache, and Git repository. Fixtures contain signed-off exact
local snapshots; a protocol-faithful fake GitHub transport supplies fixed API
responses and records requests. Every scenario runs in both human and JSON mode
where output behavior differs.

The release suite repeats the remote publication scenario against a disposable
GitHub repository with immutable releases enabled. Hermetic scenarios gate
every pull request; the live scenario gates a release because local emulation
cannot prove GitHub policy or authority.

## Required Scenario Set

| ID | Operation | Observable proof |
|---|---|---|
| `A01` | First-time `setup` with no source or selection flags | Dry-run fetches and validates the packaged exact GitHub snapshot metadata but writes nothing. The real run selects only its default package; verified cache objects may precede one atomic intent/lock commit; a second identical run is byte-for-byte idempotent. |
| `A02` | `discover` and exact `inspect` | Candidate JSON is deterministic and no repository or cache path changes; inspection rejects a candidate until an exact snapshot is supplied and verified. |
| `A03` | Select, then remove, one package from an exact local snapshot | Selection adds exactly that package, lock evidence and cache digests match the fixture, and neither other packages nor private source assets are exposed. Removal deletes the package and deletes the marketplace selection when it becomes empty. |
| `A04` | Select `--all` from the same snapshot | Every package in the exact index is selected in canonical order, with no per-primitive choice surface and no dependency traversal or extra network request. |
| `A05` | Update to an explicitly named replacement snapshot | The selected package names are preserved, all evidence moves to the supplied snapshot, and a replacement missing one selected package fails unchanged. |
| `A06` | Delete one cache object, reconstruct, then project each provider | Online reconstruction refetches only the exact locked object; offline reconstruction subsequently proves the cache complete. Codex and GitHub Copilot projection into separate explicit output roots produces exact package trees. A missing or corrupt offline object returns exit `4` and changes no state or output. |
| `A07` | Inject unresolved, stale, invalid, and interrupted consumer states | `resolve` creates matching lock evidence for valid unresolved or stale intent without changing selections. Malformed input fails unchanged. `recover --dry-run` explains, and `recover` deterministically completes or restores, each valid journal shape before other stateful commands proceed. Digest, projection, concurrency, and destination failures preserve prior intent, lock, and output; verified unreferenced cache blobs are permitted. |
| `A08` | Repeat equivalent failures with shuffled fixture and filesystem order | JSON envelopes, diagnostic codes, diagnostic order, human-leading error codes, and exit classes are identical across runs. |
| `A09` | Materialize one valid authored marketplace twice | The release directories are byte-for-byte identical and contain canonical package archives, complete Codex and GitHub Copilot projections, index, checksums, and no undeclared file. |
| `A10` | Validate source and hydrated output with `--portable`, then request author guidance | The valid fixture passes without home-directory, GitHub, or network access; an unknown JSON file and each trust-boundary limit fail with contract exit `3`; every author-guidance record names an existing repository-relative contract page and writes nothing. |
| `A11` | Publish, verify, and retry one snapshot | Preflight precedes mutation; the exact commit, tag, checksums, assets, and immutable state verify; retry reports an existing-state conflict and never replaces an asset or tag. An injected uncertain response returns exit `6` with reconciliation data. |
| `A12` | Inspect the shipped distribution and run smoke commands | The archive is reproducible; contains only launchers and JVM JARs; contains no Rust, Python, or native runtime payload; and runs `--version`, `doctor`, portable validation, and one local setup on a supported JDK. |

## Assertions Shared by Every Scenario

Each scenario records command line, environment allowlist, exit code, standard
output, standard error, pre/post tree digest, and outbound requests. Secrets and
payload content are redacted before artifacts are retained. A scenario fails if
an undeclared network request, write outside the transaction roots, prompt,
terminal-control sequence in non-terminal output, or nondeterministic ordering
is observed.

JSON success and failure output is validated against the V1 envelope. Files are
validated with their owning schema or typed parser and then hashed from bytes.
Assertions compare canonical content rather than modification timestamps.

## Coverage Boundary

This set proves the [minimal public CLI contract](cli-contract-v1.md) and links
each required user flow to at least one executable path. Unit and property tests
remain responsible for exhaustive identifier, parser, archive, canonical JSON,
limit, and state-machine cases. The acceptance suite does not introduce version
resolution, dependency graphs, workflows, capabilities, hooks, per-primitive
selection, or provider configuration mutation.
