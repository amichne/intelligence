---
type: Decision Record
title: Immutable snapshot publication V1 contract
description: Deterministic local materialization and one-way promotion to an immutable GitHub release.
resource: https://github.com/amichne/intelligence/issues/34
tags: [publication, github, releases, snapshots, reproducibility, v1]
timestamp: 2026-07-13T01:28:00-04:00
---

# Immutable Snapshot Publication V1 Contract

This decision record defines how validated marketplace source becomes local
release assets and how those exact bytes are promoted to one immutable GitHub
release. Local materialization and remote publication are separate operations
with different authority and failure boundaries.

## Decision

Materialization is a deterministic offline build. Publication is a one-way
remote promotion of an already-materialized directory. The publisher never
rebuilds, repairs, or rewrites an asset, and it never changes the predetermined
asset set during upload.

One snapshot produces one complete asset set and one GitHub release. The
snapshot ID is the exact release tag. It is an opaque lowercase kebab-case
identifier, not a semantic version and not an ordering key. Clients resolve
that exact tag and never depend on GitHub's moving latest-release endpoint.

Corrections require a newly built snapshot with a new snapshot ID. Published
releases, tags, and assets are terminal V1 state.

## Local Release Directory

The materializer writes exactly this flat directory:

```text
<output>/
├── marketplace.json
├── package-<package-name>.zip
├── codex-marketplace.zip
├── github-copilot-marketplace.zip
└── SHA256SUMS
```

There is one provider-neutral package archive per package. The two provider
archives contain the complete snapshot marketplace for their provider. The
directory contains no logs, temporary files, mutable status, source archives,
or publication receipt.

The flat asset namespace is deliberate. It avoids a release-side directory
protocol, makes duplicate detection exact, and lets the snapshot index name
every remotely uploaded asset directly.

## Canonical Package Archives

Each `package-<package-name>.zip` contains:

```text
package.json
skills/<skill-name>/SKILL.md
skills/<skill-name>/<private supporting assets>
```

`package.json` is the strict provider-neutral package manifest. It records the
package identity and description, sorted tags, every skill identity and
description, and every primary or supporting asset's path, byte size, SHA-256
digest, and executable bit. It contains no package version, dependency,
capability, workflow, provider path, or transport coordinate.

The package archive is self-contained. No file may refer to another package or
require an earlier snapshot. Extracting and validating one archive requires no
network access and no other release asset.

`package.json` has the closed V1 shape below. `skills` is the primitive-kind
boundary; V1 has no generic primitive bag or `kind` value that could admit a
second public primitive accidentally. Each `primary` path is exactly
`skills/<skill-name>/SKILL.md`. `assets` contains only private files owned by
that skill and is ordered by path.

```json
{
  "description": "A review package",
  "marketplaceId": "example-marketplace",
  "name": "review-tools",
  "schemaVersion": 1,
  "skills": [
    {
      "assets": [
        {
          "executable": true,
          "path": "skills/review/scripts/check.sh",
          "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "size": 12
        }
      ],
      "description": "Review code",
      "name": "review",
      "primary": {
        "executable": false,
        "path": "skills/review/SKILL.md",
        "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "size": 24
      }
    }
  ],
  "tags": ["kotlin", "review"],
  "type": "INTELLIGENCE_PACKAGE"
}
```

The owning public schema is
`schemas/core/portable-package-v1.schema.json`. The typed parser additionally
enforces canonical array order, portable path byte length, exact skill
ownership, ASCII case-folding collisions, aggregate entry and expanded-byte
limits, and canonical RFC 8785 bytes.

Every packaged `SKILL.md` has one canonical portable form:

```yaml
---
name: review
description: "Review code"
---

Review the supplied code and report evidence.
```

The metadata contains exactly `name` followed by `description`. The description
value is an RFC 8785 canonical JSON string token, which is also a YAML
double-quoted scalar. The opening and closing delimiters, one blank line before
the non-empty Markdown body, and LF line endings are exact. NUL bytes, carriage
returns, extra metadata, non-canonical string escapes, an identity mismatch, or
an empty body fail package materialization. This intentionally excludes
provider-only skill metadata from the portable V1 archive instead of assigning
it new cross-provider semantics.

## Canonical Provider Archives

The Codex archive contains:

```text
.agents/plugins/marketplace.json
.intelligence/projection.json
.intelligence/checksums.sha256
plugins/<package-name>/.codex-plugin/plugin.json
plugins/<package-name>/.intelligence/...
plugins/<package-name>/skills/...
```

The GitHub Copilot archive contains:

```text
.github/plugin/marketplace.json
.intelligence/projection.json
.intelligence/checksums.sha256
plugins/<package-name>/plugin.json
plugins/<package-name>/.intelligence/...
plugins/<package-name>/skills/...
```

Each provider catalog has the exact marketplace ID as its name and one entry
per package, sorted by package name. Sources are local relative paths of the
form `./plugins/<package-name>`. Copilot entries set `strict` to `true`. Codex
entries use fixed V1 policy values `AVAILABLE` and `ON_INSTALL` and the fixed
category `Productivity`. Provider adapter versions are the package-derived
`0.0.0-intelligence.sha<package-sha256>` values.

The archive-level projection receipt accounts for every package plugin and
records the snapshot identity and provider. Its checksum file covers every
regular archive entry except itself. Per-plugin receipts remain inside each
plugin and prove the package-level mapping.

The exact provider archive path set is the union of its catalog, archive-level
projection receipt, archive checksum file, and the complete canonical plugin
tree for every package as defined by the linked provider projection contract.
No other path is permitted.

Provider archives are distribution payloads, not installation actions. Their
catalogs make the extracted archive consumable by the provider's explicit
marketplace workflow, but the CLI never registers or installs them.

## Deterministic ZIP Contract

All three archive roles use one canonical ZIP writer implemented by the Kotlin
CLI. The writer:

- emits only regular file entries and omits directory entries;
- sorts slash-separated relative paths by UTF-8 byte order;
- requires every path segment to match `[A-Za-z0-9._-]+`, rejects `.` and `..`,
  and rejects duplicate paths or collisions after ASCII lowercasing;
- uses UTF-8 entry names and the `STORED` method without compression;
- records exact size and CRC-32 before writing each entry;
- writes the fixed raw DOS date and time fields for `1980-01-01 00:00:00` with
  no timezone conversion;
- writes deterministic Unix mode `0755` when the manifest declares an entry
  executable and `0644` otherwise;
- emits no archive comment, entry comment, platform extras, or local metadata;
  and
- writes entries from validated bytes rather than filesystem metadata.

Using stored entries avoids compressor-version drift across JVMs. Identical
validated input therefore produces identical archive bytes independently of
checkout path, wall clock, locale, timezone, username, or file modification
time.

The SHA-256 digest of the entire canonical
`package-<package-name>.zip` byte sequence is the package digest. Package
archives are completed first; the index, provider adapter versions, and
projection receipts then reference that digest. Nothing inside a package
archive references its own package digest, so the definition is acyclic.

## Canonical JSON Bytes

Every generated JSON file is serialized as RFC 8785 JSON Canonicalization Scheme
bytes followed by exactly one newline. Duplicate input keys fail before typed
construction. Arrays use the ordering declared by their owning contract; the
serializer never guesses that an array is a set.

## Marketplace Index

`marketplace.json` is the canonical provider-neutral snapshot index. It is
strict JSON with:

- schema version and type discriminator;
- marketplace ID and snapshot ID;
- exact default package name;
- packages sorted by name, each with description, sorted tags, and the exact
  package asset name, byte size, and lowercase SHA-256 digest;
- Codex and GitHub Copilot projection entries with exact asset name, byte size,
  and lowercase SHA-256 digest; and
- the fixed checksum asset name `SHA256SUMS`.

The closed V1 shape is:

```json
{
  "checksumAsset": "SHA256SUMS",
  "defaultPackage": "review-tools",
  "marketplaceId": "example-marketplace",
  "packages": [
    {
      "archive": {
        "name": "package-review-tools.zip",
        "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "size": 1024
      },
      "description": "A review package",
      "name": "review-tools",
      "tags": ["kotlin", "review"]
    }
  ],
  "projections": [
    {
      "archive": {
        "name": "codex-marketplace.zip",
        "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "size": 2048
      },
      "provider": "codex"
    },
    {
      "archive": {
        "name": "github-copilot-marketplace.zip",
        "sha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
        "size": 2048
      },
      "provider": "github-copilot"
    }
  ],
  "schemaVersion": 1,
  "snapshotId": "snapshot-one",
  "type": "INTELLIGENCE_MARKETPLACE_SNAPSHOT"
}
```

Packages are ordered by name. Projection evidence is exactly Codex followed by
GitHub Copilot. Package and projection archive names are derived from their
typed identities rather than supplied independently. Archive names and digests
are unique across the index, every archive size is positive and at most 256
MiB, and a snapshot exposes at most 512 packages. The owning public schema is
`schemas/core/marketplace-snapshot-v1.schema.json`; the typed parser additionally
enforces canonical order, identity-derived archive names, the default-package
reference, the exact provider set, and cross-record uniqueness.

The index does not contain its own digest or the checksum file's digest. Doing
so would create a recursive content definition. The release API and consumer
lock record those two remote asset digests after publication.

The index contains no repository URL, GitHub release ID, source commit,
publication time, download URL, branch, latest alias, package version, or
dependency graph. Those are transport evidence or deferred behavior rather
than snapshot semantics.

## Release Checksums

`SHA256SUMS` covers every other local release asset and no unlisted file.
Entries are sorted by UTF-8 asset name and use:

```text
<lowercase-sha256><two spaces><asset-name><newline>
```

The checksum file ends with one newline. Asset names cannot contain whitespace,
backslashes, control characters, or path separators. GitHub's server-side
digest covers `SHA256SUMS` itself after upload.

## Local Materialization Transaction

Materialization runs without network access:

1. validate the authored snapshot source;
2. create every provider-neutral package archive in a private staging root;
3. project and validate each package for Codex and GitHub Copilot;
4. assemble and validate both provider marketplace archives;
5. write and reparse `marketplace.json` from the generated artifact evidence;
6. write and verify `SHA256SUMS`;
7. repeat the complete build in a second empty staging root;
8. compare the two path sets and every file byte; and
9. promote one validated staging tree according to the immutable output rule.

Staging roots are siblings of the output on the same filesystem. If the output
does not exist, the materializer atomically renames one staging tree into place.
If the output is a valid byte-identical tree, materialization is a successful
no-op. Any missing, changed, or additional output path fails with
`OUTPUT_EXISTS`; the materializer never overwrites or repairs a non-identical
output. Temporary roots are removed after failure and are never eligible for
publication.

## Publication Inputs

Publication requires only:

- the validated local release directory;
- one canonical GitHub `owner/repository` target; and
- one exact lowercase 40-character hexadecimal source commit SHA whose object
  exists and is a commit in that repository.

The tag comes from `marketplace.json.snapshotId`. The publisher does not accept
a second tag override, branch, package selector, asset selector, or version
argument. This prevents local and remote identity from diverging.

## Read-Only Remote Preflight

Before creating a draft, the publisher verifies:

- authentication and the required repository authority;
- the repository response's `full_name` equals the requested `owner/repository`
  under ASCII case-insensitive comparison, with no redirect to a different
  canonical name, and records its numeric repository ID;
- the exact source object exists and is a commit, without branch or history
  traversal;
- immutable releases are already enabled;
- no Git tag, draft release, or published release uses the snapshot ID; and
- every local asset, checksum, index reference, package archive, and provider
  archive passes its owning validation gate again from disk.

The command never enables immutable releases. That is an explicit repository
administrator operation. Preflight performs no release mutation and can be
used as the publication dry run.

## Draft Promotion Protocol

After preflight, publication performs this fixed mutation sequence:

1. create one draft release with the snapshot ID as tag and release name, the
   exact source commit SHA as `target_commitish`, `prerelease: false`,
   `generate_release_notes: false`, and `make_latest: false`;
2. upload every local asset once in UTF-8 filename order;
3. after each upload, require the exact name, expected content type, state
   `uploaded`, byte size, and `sha256:<digest>` response;
4. list all draft assets and require exact set equality with local assets;
5. download every draft asset and verify its size and SHA-256 from bytes;
6. publish once by changing only `draft` to `false` while retaining
   `make_latest: false`; and
7. resolve the exact tag and require the release ID, tag, target commit,
   non-draft state, `immutable: true`, and exact asset evidence.

JSON uses `application/json`, ZIP archives use `application/zip`, and
`SHA256SUMS` uses `text/plain`. The publisher does not use GitHub-generated
source archives as marketplace assets.

Uploads are sequential in V1. The publisher does not retry a failed upload or
attempt to resume an existing draft. This keeps ambiguous duplicate, rename,
rate-limit, and partial-upload behavior out of the initial contract. A caller
may rerun only after the failed invocation has conclusively removed its own
draft.

## Failure And Cleanup

Before the publish request, a failure may delete only the draft release and
draft assets created by the current invocation. Cleanup first re-reads the
release ID and requires that it is still a draft for the expected tag and
commit. It never deletes a pre-existing release, tag, or asset. A conclusive
cleanup failure returns `DRAFT_RETAINED` with the draft ID; no rerun is permitted
until verification proves the exact tag absent.

If GitHub returns an uncertain mutation result and the command cannot prove
whether a draft or publication occurred, it returns `REMOTE_STATE_UNKNOWN` and
includes every known remote ID. It does not guess, retry, or delete. This applies
to uncertain draft creation, upload, cleanup, and publish results. The exact-tag
verification operation is the only safe next step.

After the publish request is sent, no rollback is attempted. If post-publish
verification fails, the outcome is `PUBLISHED_UNVERIFIED`, not an ordinary
failure that permits republishing. Verification can later recover the terminal
receipt from the exact tag.

Verification classifies the exact snapshot ID as exactly one of `ABSENT`,
`DRAFT`, `PUBLISHED_VERIFIED`, or `PUBLISHED_INVALID`. Authenticated verification
includes draft releases. Only `ABSENT` permits another publication attempt;
`PUBLISHED_INVALID` is terminal evidence that requires a new snapshot ID.

## Publication Result

Successful publication returns one typed result containing:

- canonical repository identity and numeric repository ID;
- release ID and HTML URL;
- snapshot ID and locked tag;
- exact source commit SHA;
- published timestamp and immutable state;
- every asset ID, name, size, content type, and SHA-256 digest; and
- the completed validation gates.

The result is command output, not another release asset. It contains remote IDs
and timestamps that do not exist during deterministic materialization. A
consumer later records the same remote evidence in its lock when it resolves
the release.

## Authorization Boundary

Read-only discovery of public releases may be unauthenticated. Publication
requires GitHub Contents write authority; checking immutable-release policy
requires Administration read authority. Repository metadata and policy
responses are preflight evidence, but GitHub does not expose a complete proof of
all token scopes. An API permission rejection is a typed publication failure and
triggers cleanup when the invocation's draft identity is known conclusively.

The normal publisher never requests Administration write and never changes
repository policy. It honors GitHub rate-limit and retry headers for read-only
requests, but it does not automatically repeat content-creating mutations.

## Rejected V1 Complexity

V1 deliberately excludes:

- package versions, version ranges, latest aliases, release ordering, and
  automatic update selection;
- delta releases, previous-release reconstruction, or asset reuse across
  snapshots;
- multiple transports, mirrors, registries, signing services, or custom
  attestations;
- parallel uploads, resumable drafts, automatic upload retries, or publication
  queues;
- mutable releases, replacing published assets, moving tags, or deleting a
  published release as correction;
- publishing a subset of packages or only one provider; and
- building provider output inside the remote publication mutation.

GitHub's release attestation is useful additional provenance, but it does not
replace source validation, hydrated provider validation, local checksums, or
byte-for-byte asset read-back. Local checksums prove internal consistency;
GitHub's authenticated asset digests, immutable-release state, and the
consumer's exact lock are the terminal remote evidence. V1 adds no custom
signature protocol.

## Decision History

| Choice | V1 disposition |
|---|---|
| Local materialization and remote publication are separate operations. | Accepted. |
| One complete snapshot becomes one release. | Accepted. |
| Snapshot ID is the exact tag but has no version semantics. | Accepted. |
| One archive per package and one aggregate archive per provider. | Accepted. |
| Canonical stored ZIPs rather than compressed or platform archives. | Accepted for JVM reproducibility. |
| Flat release asset namespace and one checksum manifest. | Accepted. |
| Draft, upload, read back, then publish exactly once. | Accepted. |
| Final publication receipt as a release asset. | Rejected because remote IDs and timestamps do not exist during deterministic build. |
| Automatic immutable-policy enablement, retries, or draft resumption. | Rejected from V1. |
| Latest-release designation. | Rejected; publication sets `make_latest: false`. |

## Sources And Validation

This contract refines [GitHub marketplace release mechanics
research](github-release-mechanics-research.md), [Validation trust boundary V1
contract](validation-trust-boundary-v1.md), the [Codex projection V1
contract](codex-projection-v1.md), and the [GitHub Copilot projection V1
contract](github-copilot-projection-v1.md). Current GitHub behavior is
documented by [Immutable
releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases),
[REST release endpoints](https://docs.github.com/en/rest/releases/releases),
[REST release asset endpoints](https://docs.github.com/en/rest/releases/assets),
and [Verifying release
integrity](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/verify-release-integrity).

Validate this reference page with:

```sh
python3 /Users/amichne/.codex/plugins/cache/slopsentral/code-knowledge-base/0.1.0/skills/code-knowledge-base/scripts/code_kb.py check --repo . --docs docs
zensical build --clean
```
