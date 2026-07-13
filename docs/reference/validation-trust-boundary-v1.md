---
type: Decision Record
title: Validation trust boundary V1 contract
description: Closed validation gates for source, snapshots, consumer state, cache, projections, publication, and the Kotlin distribution.
resource: https://github.com/amichne/intelligence/issues/43
tags: [validation, integrity, projection, publication, trust, v1]
timestamp: 2026-07-12T23:36:22-04:00
---

# Validation Trust Boundary V1 Contract

This decision record defines the checks that make marketplace content safe to
select, cache, project, publish, and reproduce. Validation is the trust
boundary: parsing a file, downloading bytes, or generating provider output
never makes that content usable by itself.

The contract applies to the domain in [Portable package and marketplace V1
contract](portable-package-marketplace-v1.md) and the state model in [Consumer
selection and reproducibility V1
contract](consumer-selection-reproducibility-v1.md).

## Validation Axioms

Every V1 validator follows these rules:

1. Parse untrusted input into a typed candidate before constructing a trusted
   domain value.
2. Reject unsupported contract versions, unknown fields, unknown enum values,
   duplicate JSON keys, and ambiguous identities.
3. Run structural, semantic, and integrity checks before a candidate crosses a
   trust boundary.
4. Validate staged output from disk, not only the in-memory value that produced
   it.
5. Return all deterministic findings that can be collected safely; never
   mutate input to make it pass.
6. Treat missing evidence as failure rather than inferring, substituting, or
   reaching across the network for an alternative.
7. Use the same validators in explicit validation commands and mutation flows.

Validation never repairs, migrates, downloads, publishes, installs, or changes
provider configuration. A command that performs one of those operations runs
validation as a prerequisite and again against its staged result.

## Validation Result

Human and machine output are renderings of one typed report. A report contains:

- report schema version;
- validated subject kind and stable subject identity;
- overall `PASS` or `FAIL` outcome;
- the ordered set of gates that ran;
- deterministic diagnostics; and
- explicit gates that could not run because a prerequisite failed.

Each diagnostic contains a stable code, severity, subject identity, repository
path or artifact identity, optional JSON Pointer, and concise message. V1 has
only `ERROR` and `INFO`; warnings do not create a second ambiguous acceptance
policy. Any error makes the report fail.

Diagnostics are sorted by gate, subject, path, pointer, and code. Human output
may add color and prose, but it cannot suppress or reinterpret a machine
diagnostic.

## Persisted JSON Coverage

Every persisted JSON document has exactly one owning schema and typed parser.
The portable repository gate scans authored and checked state and fails when a
JSON file is unrecognized, has multiple possible owners, or bypasses its owner.
Runtime journals have typed parsers even though they are ignored by version
control.

All V1 JSON contracts require:

- UTF-8 without a byte-order mark;
- one root object;
- one supported `schemaVersion` and one closed `type` discriminator;
- required fields present and non-null;
- unknown fields rejected;
- duplicate keys rejected before object construction;
- finite, bounded integers and strings; and
- deterministic serialization with stable key and array ordering.

Repository-owned JSON Schemas use the JSON Schema 2020-12 dialect and validate
against its official meta-schema. Schema primitives must declare
`https://json-schema.org/draft/2020-12/schema`. Remote `$ref`, `$dynamicRef`, and
custom meta-schema retrieval are forbidden in V1; all referenced schema
resources must be present in the same package bundle and resolved without
network access.

## Resource Limits

Validation enforces contract-versioned limits before allocation or archive
extraction. V1 uses one fixed limit set rather than user configuration:

| Resource | V1 limit |
|---|---:|
| One JSON document | 4 MiB |
| One compressed package bundle | 32 MiB |
| One expanded package bundle | 128 MiB |
| One bundle entry | 16 MiB |
| Bundle entries | 4,096 |
| Primitive definitions per package | 512 |
| Normalized archive path | 240 UTF-8 bytes |

The validator counts expanded bytes while streaming and stops at the first
limit breach. It does not trust archive headers to describe actual expansion.
Supporting assets may contain arbitrary bytes, but the CLI never recursively
extracts a supporting asset.

## Authored Snapshot Source

Source validation proves that authoring input can produce one unambiguous V1
snapshot.

### Marketplace checks

The validator requires:

- one supported marketplace manifest and immutable opaque marketplace ID;
- at least one package with a unique package name;
- exactly one default package that names an exposed package;
- tags normalized, duplicate-free, and used only as metadata;
- no semantic versions, dependency declarations, workflows, capabilities, or
  per-primitive exposure controls; and
- every referenced source path normalized and contained in the authoring root.

### Package checks

Each package must have one manifest, a unique marketplace-qualified identity,
at least one primitive, and a closed file set. Every bundle file is either the
manifest, one primitive definition, one primitive's primary content, or one
supporting asset owned by exactly one primitive. Missing, unreferenced, multiply
owned, or out-of-root files fail validation.

Primitive identities are unique by package, kind, and name. The kind must be
one of `skill`, `agent`, `hook`, `instruction`, `prompt`, `concept`, `schema`,
or `document`.

All primitive definitions use their owning kind-specific schema and the common
rules below:

- the name is non-empty, normalized, and safe as a logical identifier;
- primary and supporting paths are relative, normalized, and package-contained;
- declared sizes and SHA-256 digests match the referenced bytes;
- no symlink, hard link, device, socket, or named pipe is accepted; and
- no provider directory or provider-specific generated path is authoring
  source.

The Markdown-backed kinds—skill, agent, instruction, prompt, concept, and
document—require non-empty UTF-8 Markdown without NUL characters. Schema
primitives require a valid offline JSON Schema 2020-12 resource. Hook
primitives require the strict provider-neutral hook schema; provider-specific
event support is checked by the requested projection gate.

## Package Bundle and Snapshot Index

Bundle validation happens before extraction into a destination directory. It
rejects:

- absolute paths, parent traversal, backslashes, empty path segments, or
  non-normalized Unicode;
- duplicate paths or collisions after Unicode normalization or case folding;
- symlinks, hard links, devices, sockets, pipes, or nested extraction;
- entries, expanded bytes, or path lengths above the V1 limits; and
- archive entries not declared by the package manifest.

After safe inspection, the validator applies the same package checks used for
authored source. Source and hydrated bundle validation therefore accept the
same semantic package or both fail.

Snapshot-index validation requires:

- supported contract type and version;
- the expected marketplace and immutable snapshot identities;
- one unique package record for every exposed package;
- exactly one valid default package;
- stable asset filenames, positive byte sizes, and lowercase SHA-256 digests;
- no duplicate asset identity, filename, package identity, or digest role; and
- a checksum manifest whose entries exactly equal the index, package-bundle,
  and provider-payload asset set—no missing or extra entry.

The snapshot index cannot contain its own digest. The checksum manifest and
release-asset evidence cover the index externally. The checksum manifest does
not list itself; GitHub's release-asset digest covers the checksum-manifest
bytes.

## Consumer Intent and Lock

Consumer validation first classifies the closed state model defined by the
consumer contract. Only `Resolved` is usable for reconstruction or projection.

Intent validation enforces exact snapshot locators, explicit sorted package
sets, closed source kinds, portable local paths, and the absence of moving
selectors or credentials. Lock validation enforces stable GitHub or local
locators, exact asset identities, sizes and digests, deterministic ordering,
and the absence of credentials, cache paths, timestamps, package versions, or
dependency edges.

Agreement validation compares the typed marketplace, source, exact snapshot,
and package-selection values. An orphan, stale pair, unsupported version, or
active transaction journal fails consumer validation. Recovery is a separate
mutation that must complete before validation is retried.

## Cached Content

Cache validation derives each path from its lowercase SHA-256 digest, opens the
blob without following symlinks, streams its bytes, and requires the locked
size and digest. A cache filename is never evidence for content it has not
verified.

Offline validation requires every locked index, checksum manifest, and selected
package bundle. Online validation may report a cache miss separately, but it
does not fetch content; the calling resolution or reconstruction command owns
that explicit mutation.

Corruption fails without deleting or overwriting the blob. Cache repair is not
a V1 validation side effect.

## Provider Projection

Projection validation has two phases for one requested provider:

1. **Mapping validation** proves that every selected primitive has one
   semantics-preserving provider mapping and that no portable primitive or
   supporting asset is silently omitted.
2. **Hydrated-output validation** parses the generated provider payload using
   that provider's contract, verifies every generated file and receipt, and
   proves that output is marked as generated rather than authoring source.

The projector writes only to a temporary directory. Hydrated validation must
pass before an existing generated tree is replaced. An unsupported primitive
blocks the requested provider projection; the projector never emits a partial
package.

Materializing one provider does not require the other provider to pass.
Publishing V1 requires both the Codex and GitHub Copilot projections to pass.
Neither gate may mutate provider installation, settings, credentials, or hook
trust.

## Publication Readiness

Publication is the broadest pre-mutation gate. Before a draft GitHub release is
created, the publisher requires:

- authored snapshot source validation;
- deterministic provider-neutral package bundles and complete snapshot index;
- two clean materializations whose asset bytes and digests are identical;
- passing Codex and GitHub Copilot mapping and hydrated-output validation;
- a checksum manifest covering the exact asset set;
- an exact source commit SHA and unused release tag; and
- repository immutable-release policy verified as enabled.

The publisher then uploads to a draft, reads every asset back, and rechecks
identity, filename, state, size, and digest before the single publish action.
After publication it reads the release back and requires the expected release
ID, tag, commit SHA, asset set, and `immutable: true` state.

V1 requires GitHub immutable-release and checksum evidence but no custom key,
signature format, or user-managed signing service. GitHub's release attestation
is additional provenance; it does not replace content or semantic validation.

## Kotlin Distribution Gate

The shipped CLI runtime is Kotlin/JVM only. Kast remains development tooling
and is never embedded or invoked by the installed CLI.

Distribution validation fails when the packaged runtime contains or invokes:

- Rust source, Cargo metadata, Rust executables, or Rust-backed helper binaries;
- Python source, virtual environments, Python interpreters, or Python-backed
  helper commands; or
- any native helper used to implement a required CLI behavior.

Gradle launch scripts and JVM dependencies are allowed. Every required runtime
behavior must be reachable through compiled Kotlin/JVM code in the distribution
and must pass from a clean environment without Rust or Python on `PATH`.

## Gate Matrix

Each operation runs the smallest sufficient gate set, but cannot skip a gate
that protects its output.

| Operation | Required gates | Failure blocks |
|---|---|---|
| Read-only discovery | Transport response and snapshot-index structure | Presenting the candidate as valid or selectable |
| Validate authored source | JSON coverage, source structure, semantics, paths, content | Bundle creation, projection, publication |
| Set up, select, remove, update | Exact snapshot, selected bundles, proposed intent and lock, cache insertions, staged state | All consumer-state replacement |
| Reconstruct online | Resolved consumer state, exact locked transport evidence, downloaded bytes | Cache insertion and projection |
| Reconstruct offline | Resolved consumer state and all locked cache bytes | Projection or use |
| Materialize provider | Resolved consumer state, cache, mapping, hydrated provider output | Generated-tree replacement |
| Publish | Source, bundles, index, both projections, determinism, checksums, immutable-release preflight | Draft creation or publication |
| Post-publish verify | Release identity, immutable state, read-back asset set, sizes, digests | Success receipt |
| Portable repository validation | Owned JSON coverage and all checked local contracts, without network access or mutation | CI success |
| Distribution validation | Kotlin-only runtime contents and clean-environment smoke tests | Release or installation success |

If a broad operation fails after a safe draft or temporary directory was
created, cleanup may remove only that uncommitted staging area. Published
content, prior valid consumer state, cache metadata, and existing generated
output remain unchanged.

## Portable and CI Validation

Portable validation is deterministic, read-only, and network-free. It scans
the repository for owned JSON, validates authored marketplace source, validates
checked consumer state when present, validates checked generated payloads when
present, and runs the distribution-content audit when a distribution is
supplied.

CI must run the same Kotlin validators used by the CLI. Test-only validators,
shell approximations, Rust tools, or Python scripts cannot serve as the V1
product trust boundary.

## Deferred Beyond V1

V1 validation intentionally excludes:

- automatic repair, migration, or normalization of invalid state;
- remote schema resolution or custom JSON Schema dialects;
- user-configurable security limits or warning policies;
- custom signing keys, signature policy, transparency logs, or revocation;
- package-version, dependency-graph, capability, or workflow validation; and
- runtime-provider installation or trust decisions.

## Sources and Validation

This contract resolves [Define V1 validation as the trust
boundary](https://github.com/amichne/intelligence/issues/43). Its source
contracts are [Portable package and marketplace V1
contract](portable-package-marketplace-v1.md), [Consumer selection and
reproducibility V1 contract](consumer-selection-reproducibility-v1.md), and
[GitHub marketplace release mechanics
research](github-release-mechanics-research.md).

Schema primitives use the [official JSON Schema 2020-12
dialect](https://json-schema.org/draft/2020-12) and its published meta-schema.

Validate this page with:

```sh
python3 /Users/amichne/.codex/plugins/cache/slopsentral/code-knowledge-base/0.1.0/skills/code-knowledge-base/scripts/code_kb.py check --repo . --docs docs
zensical build --clean
```
