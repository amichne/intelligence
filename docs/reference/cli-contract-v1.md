---
type: Decision Record
title: Minimal public CLI V1 contract
description: Stable non-interactive command, result, diagnostic, and failure surface for the skill-only V1.
resource: https://github.com/amichne/intelligence/issues/32
tags: [cli, v1, automation, reproducibility]
timestamp: 2026-07-13T01:11:59-04:00
---

# Minimal Public CLI V1 Contract

This decision record defines the stable public CLI surface for the first useful
release. It deliberately exposes product operations rather than storage,
provider, or transport internals. Detailed package, consumer, validation, and
publication behavior remains authoritative in the linked contracts.

## Interaction Contract

Every command is non-interactive. A command either has enough explicit and
validated input to finish or fails without prompting. V1 never opens a TUI,
asks for confirmation, infers a moving version, repairs persisted state
silently, or changes the selected package set implicitly.

Consumer commands default `--repository` to the current directory. Authoring
commands require explicit input and output paths. Every leaf command accepts
`--format human|json`; `human` is the default. Mutating commands accept
`--dry-run`, and network-capable consumer commands accept `--offline`.

## Exact Source Input

Commands that resolve a new marketplace snapshot accept exactly one of these
closed source forms:

```text
--github OWNER/REPOSITORY --snapshot SNAPSHOT_ID
--local-snapshot DIRECTORY --index-sha256 SHA256
```

The GitHub form names one immutable release. The local form names one directory
and the expected digest of its canonical marketplace index. Consumer mutations
require that directory to be relative to the target repository so persisted
state remains portable; read-only inspection may use an absolute directory. A
moving branch, `latest`, version range, implicit local directory, or partially
specified source is invalid.

`setup` may omit the source. In that one case it uses an exact GitHub repository
locator, snapshot ID, and index digest packaged as metadata with that CLI build.
Snapshot and package bytes are not embedded in the distribution. Default setup
therefore requires network access and rejects `--offline`; an explicit local
source remains available for offline setup. This keeps first-run setup
convenient without making its result time-dependent.

## Stable Command Grammar

The following command set is the complete stable V1 surface. Options may gain
new additive fields, but command meaning and persisted effects require a
contract-version change to evolve incompatibly.

| Command | Required behavior |
|---|---|
| `intelligence doctor` | Report local runtime, repository, cache, and GitHub readiness without mutation. |
| `intelligence setup [SOURCE] [--package NAME ... | --all]` | Populate verified cache objects, then create first-time consumer intent and lock atomically. No selection option means the snapshot's one default package. |
| `intelligence validate [--portable]` | Validate all owned structured state reachable from the target repository. Portable mode forbids host-local or network assumptions. |
| `intelligence marketplace discover (--catalog FILE | --github OWNER/REPOSITORY) [--query TEXT]` | Search one explicit repository or user-maintained catalog and return read-only candidate coordinates. A candidate is never selected or trusted by discovery alone. |
| `intelligence marketplace inspect SOURCE` | Validate and describe one exact snapshot without changing consumer state. |
| `intelligence marketplace select MARKETPLACE_ID SOURCE (--package NAME ... | --all)` | Add the exact requested whole packages, or expand all packages from that snapshot, then write complete replacement state atomically. A source differing from installed state requires `update` first. |
| `intelligence marketplace remove MARKETPLACE_ID --package NAME ...` | Remove exact whole packages from the installed selection and remove the marketplace entry when its package set becomes empty. |
| `intelligence marketplace update MARKETPLACE_ID SOURCE` | Move one installed marketplace to the explicitly supplied snapshot while preserving its selected package names; fail if any selected package is absent. |
| `intelligence marketplace resolve [MARKETPLACE_ID]` | Explicitly create or replace lock evidence for valid unresolved or stale intent without changing its source or package selections. |
| `intelligence marketplace recover` | Explicitly complete or restore one interrupted repository transaction from its typed journal before any other stateful operation. |
| `intelligence marketplace reconstruct [MARKETPLACE_ID]` | Re-populate missing digest-addressed cache objects only from exact lock evidence, or prove the cache complete offline. It does not project provider output. |
| `intelligence marketplace project MARKETPLACE_ID --provider codex|github-copilot --out DIRECTORY` | Replace one explicit output directory with one canonical package-plugin tree per selected package for exactly one marketplace and provider. It emits no marketplace catalog and never composes marketplaces. |
| `intelligence marketplace materialize --source DIRECTORY --snapshot SNAPSHOT_ID --out DIRECTORY` | Build the complete canonical local release directory, including both required provider projections, in one transaction. |
| `intelligence marketplace publish --release-dir DIRECTORY --github OWNER/REPOSITORY --commit COMMIT_SHA` | Preflight and publish one already-materialized immutable GitHub release. |
| `intelligence marketplace verify-publication --github OWNER/REPOSITORY --snapshot SNAPSHOT_ID` | Re-read and verify immutable remote release evidence without mutation. |
| `intelligence marketplace author` | Print concise paths to the source, validation, materialization, and publication contracts. It does not generate a second authoring model. |

`SOURCE` is syntax shorthand used only in this document for one of the two
exact source forms above. Repeated `--package` values are parsed as a non-empty,
duplicate-free set and persisted in canonical order. `--package` and `--all`
are mutually exclusive. Selection is always at package granularity.

Discovery never performs a global repository search. A catalog is untrusted
input that supplies explicit candidate coordinates, not an install source or a
central registry. Inspecting or selecting a candidate still requires the exact
source form and all normal verification.

Projection output is the sorted union of the selected packages' canonical
provider plugin trees at `<out>/<package-name>/`. Package names are unique
within one marketplace. Requiring one marketplace and one empty-or-replaceable
output root per invocation prevents cross-marketplace composition and name
collision policy from entering V1.

## Dry-Run and Offline Semantics

`--dry-run` performs the same parsing, reads, resolution, digest verification,
validation, conflict detection, and remote preflight as the real operation. It
returns the proposed canonical change set but performs no consumer-state,
cache, output-directory, Git tag, release, or asset write. Temporary verified
downloads are discarded. A dry run that could not safely become the real
operation fails.

`--offline` forbids every network request. Reconstruction succeeds only when
the lock is complete and every required object is present and valid in the
digest-addressed cache. Discovery, remote inspection, publication, and remote
publication verification reject `--offline` as incompatible rather than
changing meaning.

## Machine Output

JSON mode writes exactly one UTF-8 JSON object followed by one newline to
standard output on both success and expected failure. It writes no progress,
color, or diagnostic text to standard error. The envelope is:

```json
{
  "schemaVersion": 1,
  "command": "marketplace.select",
  "ok": true,
  "result": {},
  "error": null,
  "diagnostics": []
}
```

On failure, `ok` is `false`, `result` is `null`, and `error` contains stable
`code`, human-readable `message`, and structured `details`. Each diagnostic has
stable `code`, closed `INFO|ERROR` severity, a human-readable `message`, and only
applicable structured location fields. Collections preserve their owning
contract's canonical order. Validation diagnostics are ordered by gate,
subject, path, pointer, then code.

The envelope and every command result are a closed union in the public CLI
result schema. The `command` field selects exactly one result shape:

| `command` | Required `result` fields |
|---|---|
| `doctor` | `status`, ordered `checks` with stable names and outcomes |
| `validate` | The validation report fields: subject, outcome, gates, and diagnostics |
| `marketplace.discover` | Ordered `candidates`, each with canonical repository URL and untrusted advertised identity fields |
| `marketplace.inspect` | Exact source, marketplace and snapshot IDs, index digest, default package, and ordered package summaries |
| `setup`, `marketplace.select`, `marketplace.remove`, `marketplace.update`, `marketplace.resolve`, `marketplace.recover` | Operation, repository, state before and after, `dryRun`, ordered change records, normalized selections, and resulting lock digest when resolved |
| `marketplace.reconstruct` | Ordered marketplace IDs, required-object count, cache-hit count, fetched count, and `dryRun` |
| `marketplace.project` | Marketplace and snapshot IDs, provider, output path, ordered package names, complete tree digest, and `dryRun` |
| `marketplace.materialize` | Marketplace and snapshot IDs, output path, ordered asset name/size/digest records, and `dryRun` |
| `marketplace.publish`, `marketplace.verify-publication` | The typed publication result: repository and release identity, snapshot, commit, immutable state, ordered asset evidence, completed gates, and `dryRun` when applicable |
| `marketplace.author` | Ordered documentation records containing stable topic and repository-relative path |

Change records are a closed `CREATE|REPLACE|REMOVE` union with a repository-
relative path and old/new SHA-256 values when applicable. Paths use forward
slashes. Results expose identities and digest evidence, never credentials,
cached bytes, private asset content, or author-source paths internal to a
package.

Envelope fields and enum values are compatibility promises within schema
version 1. Human prose and whitespace are not. Unknown fields must be rejected
when reading persisted contracts but tolerated by automation reading a CLI
result envelope.

## Human Output

Human mode writes the primary result to standard output and informational or
error diagnostics to standard error. It may use color only when standard output
is a terminal and color has not been disabled. It never prints credentials,
authentication tokens, cached payload bytes, or private supporting assets.
Every failure starts with the same stable error code returned in JSON mode.

## Exit Codes

The process exits with one of these stable classes:

| Code | Meaning |
|---:|---|
| `0` | Operation completed successfully, including a successful dry run. |
| `2` | CLI usage or option-shape error. |
| `3` | Parsed input or persisted content violates a contract. |
| `4` | Required external input is unavailable, including offline cache miss, network, or authentication failure. |
| `5` | Safe mutation is blocked by a concurrent write, existing state, or remote precondition conflict. |
| `6` | A remote mutation began but its final state cannot be proven; the result includes reconciliation guidance. |
| `70` | Unexpected internal failure. |

Expected domain failures never use `70`. Commands preserve the underlying
typed failure until this outermost process boundary maps it to an exit class.

The outer error catalog is closed in V1. More specific contract diagnostics
remain in `diagnostics`; they do not invent new process mappings.

| Error code | Exit |
|---|---:|
| `USAGE_ERROR` | `2` |
| `CONTRACT_VIOLATION`, `VALIDATION_FAILED`, `UNSUPPORTED_CONTRACT_VERSION` | `3` |
| `EXTERNAL_UNAVAILABLE`, `OFFLINE_CACHE_MISS`, `AUTHENTICATION_FAILED` | `4` |
| `STATE_CONFLICT`, `REMOTE_PRECONDITION_FAILED` | `5` |
| `REMOTE_STATE_UNKNOWN`, `PUBLISHED_UNVERIFIED` | `6` |
| `INTERNAL_ERROR` | `70` |

## Deferred Beyond V1

V1 has no prompt mode, TUI, daemon, RPC API, shell completion contract, dynamic
plugin command registration, workflow command, capability negotiation, package
version or dependency commands, pin/unpin operations, per-primitive selection,
partial-provider materialization, provider installation, or hidden migration.

## Authoritative Links

The domain is defined by [Portable package and marketplace V1](portable-package-marketplace-v1.md),
consumer mutation by [Consumer selection and reproducibility V1](consumer-selection-reproducibility-v1.md),
input safety by [Validation trust boundary V1](validation-trust-boundary-v1.md),
and release behavior by [Immutable snapshot publication V1](immutable-snapshot-publication-v1.md).
