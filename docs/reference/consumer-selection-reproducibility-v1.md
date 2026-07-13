---
type: Decision Record
title: Consumer selection and reproducibility V1 contract
description: Minimal intent, exact lock, atomic mutation, and digest-cache rules for marketplace consumers.
resource: https://github.com/amichne/intelligence/issues/37
tags: [consumer, intent, lock, cache, reproducibility, v1]
timestamp: 2026-07-12T23:31:33-04:00
---

# Consumer Selection and Reproducibility V1 Contract

This decision record defines how a consumer repository selects packages from
an immutable marketplace snapshot and reproduces those exact bytes later. It
implements the domain in [Portable package and marketplace V1
contract](portable-package-marketplace-v1.md) without adding package versions,
dependencies, or a resolver graph.

## Decision Boundary

V1 separates human intent from resolved evidence in two version-controlled
files:

- `.intelligence/adaptable.marketplace.json` records the exact snapshot locator
  and explicit package names the consumer wants; and
- `.intelligence/marketplace-lock.json` records the verified transport and
  content evidence needed to reproduce that selection.

The repository does not vendor provider-neutral package bundles. Immutable
remote assets are stored in a user-level content-addressed cache, while
provider projections remain replaceable generated output.

## Consumer States

The CLI recognizes a closed set of consumer states. It must parse both files
into typed values before deciding which state exists.

| State | Evidence | Permitted behavior |
|---|---|---|
| Uninitialized | Neither file exists. | Read-only discovery or atomic setup. |
| Resolved | Intent and lock both validate and describe the same selections. | Inspect, validate, reconstruct, project, or explicitly mutate. |
| Unresolved | Valid intent exists without a lock. | Inspect intent or perform an explicit resolve; never materialize or use packages. |
| Stale | Both files parse, but their selections differ. | Report the difference or explicitly resolve; never use the lock as current. |
| Orphaned | Lock exists without intent. | Fail validation; never infer intent from the lock. |
| Recovering | A typed mutation journal exists. | Recover the interrupted transaction before any other operation. |
| Invalid | Any persisted file is unsupported or malformed. | Fail closed without rewriting it. |

Unsupported `schemaVersion` values are invalid. The CLI never migrates or
normalizes unsupported persisted state implicitly.

## Intent Contract

Intent is explicit and fully normalized. Convenience commands such as default
setup and select-all expand to concrete package names before writing the file.
The persisted contract never contains moving selectors such as `latest`, a
default marker, tag queries, package wildcards, or version ranges.

Each intent selection contains:

- the expected opaque marketplace ID;
- one exact snapshot source locator; and
- a non-empty, sorted, duplicate-free list of package names.

The source locator is a closed union:

- `GITHUB_RELEASE` contains a canonical repository URL and exact immutable
  release tag; or
- `LOCAL_SNAPSHOT` contains a consumer-repository-relative directory and the
  expected SHA-256 digest of its snapshot index.

Absolute local paths are non-portable and fail persisted-state validation.
Local snapshots exist for authoring and black-box tests; published consumer
flows use immutable GitHub releases.

An illustrative intent has this semantic shape:

```json
{
  "type": "MARKETPLACE_INTENT",
  "schemaVersion": 1,
  "selections": [
    {
      "marketplaceId": "example-marketplace",
      "source": {
        "type": "GITHUB_RELEASE",
        "repository": "https://github.com/example/marketplace",
        "tag": "snapshot-2026-07-12-01"
      },
      "packages": ["code-review", "kotlin-engineering"]
    }
  ]
}
```

Marketplace selections are sorted by marketplace ID and unique by marketplace
ID. Package names are local to that marketplace. The CLI writes deterministic
JSON and rejects duplicate object keys and unknown fields.

The owning public schema is
`schemas/core/marketplace-intent-v1.schema.json`. Persisted GitHub repository
URLs use lowercase canonical `https://github.com/<owner>/<repository>` form
without `.git`, query, fragment, or trailing slash variants. Local directories
use normalized forward-slash repository-relative paths; absolute paths,
backslashes, empty segments, and dot segments are invalid.

## Lock Contract

The lock is generated evidence, not an authoring surface. It contains exactly
one entry for each intent selection and exactly one package record for each
explicit package name.

For a GitHub snapshot, each lock entry records:

- marketplace ID and canonical repository URL;
- release ID, exact tag, locked tag commit SHA, and immutable-release proof;
- marketplace-index asset ID, filename, byte size, and SHA-256 digest;
- checksum-manifest asset ID, filename, byte size, and SHA-256 digest; and
- each selected package name plus its bundle asset ID, filename, byte size, and
  SHA-256 digest.

For a local snapshot, the equivalent entry records the normalized relative
path, index size and digest, checksum-manifest size and digest, and selected
package bundle sizes and digests. A local path remains a locator; digests are
the content evidence.

The lock contains no package versions, constraints, dependency edges,
selection aliases, timestamps, cache paths, or generated-provider paths. The
same verified inputs therefore produce byte-identical lock JSON.

The owning public schema is
`schemas/core/marketplace-lock-v1.schema.json`. A GitHub lock entry is itself
proof that immutable-release validation passed: its source has fixed
`immutable: true`, positive safe-integer release ID, exact tag and lowercase
tag commit SHA, and every asset has a positive stable asset ID. Callers cannot
construct a trusted GitHub lock variant with `immutable: false`. Local entries
omit remote IDs entirely. Mixing local and GitHub asset evidence in one entry
is invalid.

Intent and lock files never contain credentials, authorization headers,
temporary download URLs, or signed URLs. Authentication is execution-time
input. Locks retain stable repository, release, asset, size, and digest
evidence only.

Intent and lock agreement is semantic, not byte-based. Typed validation
compares marketplace IDs, source kind and locator, exact snapshot selector,
and the explicit package-name set. Whitespace or object-key order in intent
cannot make an otherwise equal lock stale.

## Selection Semantics

Every mutation produces a complete replacement intent and lock. Commands do
not append partially resolved entries.

| User operation | Normalized intent result |
|---|---|
| Set up default | The snapshot's default package becomes one explicit package name. |
| Select package | The named package is added to the explicit sorted set. |
| Select all | Every package exposed by that exact snapshot becomes explicit. |
| Remove package | The named package is removed; an empty marketplace selection is removed. |
| Update snapshot | The exact snapshot locator changes while the explicit package set stays unchanged. |

Updating fails if the new snapshot does not expose every already-selected
package. It never drops packages, adopts a changed default, or includes newly
exposed packages implicitly. A user who wants the new complete package set
must explicitly select all against the new snapshot.

No command persists a read-time answer such as “newest snapshot.” Discovery
may find a candidate, but mutation resolves that candidate to an exact release
tag and displays the resulting plan before replacing state.

## Resolution and Verification

Resolution has no package graph. For each selected marketplace, the CLI follows
one bounded sequence:

1. load the exact snapshot index from the selected source;
2. require the expected marketplace ID and supported schema version;
3. verify the index against the release checksum manifest;
4. find every explicit package name in that index;
5. fetch only those package bundles;
6. verify every filename, byte size, and SHA-256 digest; and
7. construct the complete lock only after every check succeeds.

The resolver never follows a primitive reference outside its package, inspects
another marketplace, discovers dependencies, or searches the network for
missing functionality. One selected marketplace requires one index read and a
bounded set of explicitly indexed asset reads.

GitHub resolution requires a published immutable release. A mutable tag,
draft release, missing asset, renamed asset, non-uploaded asset, digest
mismatch, or marketplace-ID mismatch fails the entire operation.

## Content-Addressed Cache

The cache is a user-level immutable blob store rooted at
`$XDG_CACHE_HOME/intelligence/sha256`, defaulting to
`~/.cache/intelligence/sha256` when `XDG_CACHE_HOME` is absent. A blob's path is
derived only from its lowercase SHA-256 digest:

```text
sha256/<first-two-hex>/<remaining-sixty-two-hex>
```

The cache stores the exact marketplace index, checksum manifest, and selected
provider-neutral package bundles. It stores raw bytes only; source locators,
filenames, selections, and trust decisions remain in the lock.

A cache insertion writes to a temporary sibling, verifies size and digest, and
flushes it. It then atomically publishes a no-replace hard link at the digest
path and removes the temporary name. This avoids the platform-defined target
replacement behavior of Java atomic moves: concurrent writers converge on the
first exact blob, while an existing mismatched target remains corruption.
Existing blobs are re-verified before use; ordinary selection does not silently
repair or overwrite one.

V1 has no cache catalog, mutable access metadata, background eviction, or
retention promise. Users and operating systems may remove blobs. Missing cache
content may be fetched from its exact locked source in online mode and fails in
offline mode.

## Reconstruction and Offline Behavior

Reconstruction starts from valid matching intent and lock state. It never
re-runs discovery or selects a different snapshot.

Online reconstruction may fetch a missing locked asset only by its exact
repository, release, asset identity, filename, size, and digest. Offline
reconstruction performs no network request and succeeds only when the locked
index, checksum manifest, and every selected package bundle are present and
valid in the cache.

Missing or corrupt content fails without changing intent, lock, provider
output, or cache metadata. A cached answer is never described as current remote
state.

## Atomic Mutation

Consumer-repository state is one logical transaction even though intent and
lock are separate files. Every mutating command:

1. acquires an exclusive repository mutation lock;
2. validates or recovers the existing state;
3. resolves and verifies all remote or local inputs;
4. validates the complete new intent and lock pair and all referenced cached
   content;
5. records a typed transaction journal containing old and new file digests
   before either persisted target can change;
6. writes forced backup and staged files at paths derived by that journal;
7. atomically replaces both targets using journaled rename operations; and
8. flushes each affected directory and removes the journal only after the pair
   validates from disk.

The exclusive lock is `.intelligence/.marketplace-mutation.lock`. The typed
journal is `.intelligence/.marketplace-transaction.json`, and its staged and
backup files live below `.intelligence/.marketplace-transactions/`. These are
ephemeral runtime files with owning typed parsers; they are not consumer intent
or lock evidence and must be ignored by version control.

Every CLI reader checks for the journal before reading consumer state. After a
crash, recovery completes the new pair only when both staged files and their
digests remain valid; otherwise it restores the old pair. A reader never treats
one file from each generation as resolved state.

The journal transaction ID is derived from the old-or-absent and new digests
for the intent and lock pair. Target, staged, and backup paths are fixed by that
identity rather than accepted from a caller. The owning runtime schema is
`schemas/core/marketplace-transaction-v1.schema.json`; the typed parser also
requires exactly the intent record followed by the lock record and recomputes
every derived value. Recovery refuses an observed target digest that is
neither the recorded old value nor the recorded new value.

Removing the final marketplace returns the repository to the uninitialized
state by journaling both new targets as absent. A partially absent new pair is
not representable: the parser accepts either two new digests or two nulls.
Recovery may therefore complete a partial deletion or restore the prior pair
without inventing an empty sentinel intent or exposing a non-atomic shortcut.

The content cache is outside the repository transaction. Verified immutable
blobs may remain unreferenced after a failed state commit, but no mutable cache
metadata or consumer pointer refers to them. This is safe because blob identity
is its digest and later use must verify it again.

## Failure Invariants

Any of these conditions prevents state replacement:

- unsupported or malformed intent, lock, index, checksum, or package contract;
- concurrent mutation or an unrecoverable transaction journal;
- mutable, missing, or inaccessible snapshot source;
- unexpected marketplace ID, package name, asset identity, filename, or size;
- checksum or content-digest mismatch;
- selected package absent from the exact snapshot;
- intent and proposed lock disagreement; or
- offline cache miss or corruption.

Failures return structured diagnostics and leave the last valid consumer state
usable. They never fall back to another release, package, source, cache entry,
or provider payload.

## Deferred Beyond V1

The consumer contract intentionally excludes:

- moving snapshot selectors in persisted intent;
- package semantic versions, ranges, constraints, catalogs, pins, or yanking;
- package dependencies, lock graphs, cycles, conflicts, or transitive fetches;
- implicit package-set changes during snapshot updates;
- cache eviction policy, mutable cache indexes, or shared remote caches; and
- automatic migration of persisted contract versions.

## Sources and Validation

This contract resolves [Define consumer selection and
reproducibility](https://github.com/amichne/intelligence/issues/37) and depends
on [Portable package and marketplace V1
contract](portable-package-marketplace-v1.md). GitHub release identity,
immutability, asset, checksum, and offline constraints are detailed in [GitHub
marketplace release mechanics research](github-release-mechanics-research.md).

Validate this page with:

```sh
python3 /Users/amichne/.codex/plugins/cache/slopsentral/code-knowledge-base/0.1.0/skills/code-knowledge-base/scripts/code_kb.py check --repo . --docs docs
zensical build --clean
```
