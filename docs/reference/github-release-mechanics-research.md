---
type: Research Note
title: GitHub marketplace release mechanics research
description: GitHub repository and release contracts for immutable marketplace publication and reconstruction.
resource: https://github.com/amichne/intelligence/issues/41
tags: [github, releases, marketplace, immutability, checksums, attestations]
timestamp: 2026-07-12T22:00:00-04:00
---

# GitHub Marketplace Release Mechanics Research

This note identifies the GitHub repository and release capabilities that can
transport immutable marketplace indexes and package artifacts. It separates
publication mutations from read-only discovery and describes what exact lock
evidence remains usable after the network is unavailable.

## Finding

GitHub Releases can provide the remote transport required by the portable
marketplace when release immutability is enabled before publication. A
published immutable release locks its Git tag and all uploaded assets. GitHub
also creates a release attestation covering the tag, commit SHA, and release
assets.

Immutability is repository or organization policy, not a per-release field in
the create request. It applies only to releases published after the policy is
enabled. The safe lifecycle is therefore:

1. verify that immutable releases are enabled;
2. create an unpublished draft at an exact commit;
3. upload every provider-neutral manifest, provider payload, checksum file, and
   reconstruction artifact;
4. verify asset names, sizes, states, and SHA-256 digests;
5. publish the draft once; and
6. read the published release back and require `immutable: true`.

If any pre-publication step fails, delete or repair the draft. If publication
succeeds, corrections require a new marketplace version and tag. Published
assets must never be replaced in place.

## Immutable Release Contract

GitHub's native protection provides the following guarantees after an
immutable release is published:

- the release tag is locked to one commit and cannot be moved;
- the tag cannot be deleted while the release exists;
- release assets cannot be modified or deleted;
- deleting the release does not permit reuse of the tag name; and
- a release attestation records the release tag, commit SHA, and assets.

The repository endpoint
`GET /repos/{owner}/{repo}/immutable-releases` returns `200` with the policy
state when enabled and `404` when it is not enabled. It requires Administration
read permission. Enabling the setting uses `PUT` on the same endpoint and
requires Administration write permission.

The portable publisher should treat the check as a readiness gate. Enabling or
disabling repository policy is an explicit administrator operation outside the
normal publish command. Silently enabling policy would broaden a package
publication into repository administration.

## Release And Asset API

The REST API supplies the operations needed for publication and exact
resolution.

| Operation | Endpoint or capability | Portable use |
|---|---|---|
| Create draft | `POST /repos/{owner}/{repo}/releases` with `draft: true` | Reserve the release tag and stage metadata before public visibility. |
| Upload asset | Release-specific `upload_url` on `uploads.github.com` | Upload raw binary content with explicit name, content type, and content length. |
| List assets | Release assets collection | Confirm the server-visible names, states, sizes, and digests. |
| Publish | `PATCH /repos/{owner}/{repo}/releases/{release_id}` with `draft: false` | Perform the single promotion step after all checks pass. |
| Resolve exact release | `GET /repos/{owner}/{repo}/releases/tags/{tag}` | Resolve a selected immutable marketplace release without relying on “latest.” |
| Download asset | Asset API with `Accept: application/octet-stream` or `browser_download_url` | Fetch exact release content; clients handle either a `200` response or a `302` redirect. |
| Verify release | `gh release verify <tag>` | Confirm the release exists and is immutable. |
| Verify local asset | `gh release verify-asset <tag> <path>` | Confirm a local file exactly matches an immutable release asset. |

Release and asset responses expose stable release and asset IDs, tag name,
asset name, size, state, content type, download URL, and a digest in
`sha256:<hex>` form. The transport contract should consume the digest and size
from the API, while the provider-neutral manifest remains the semantic source
of package identities and dependencies.

The upload API can rename unsafe filenames. Duplicate asset names fail with
`422`. An upstream `502` can leave an empty asset in `starter` state. A draft
publisher must list assets after upload, reject renamed or non-`uploaded`
entries, delete failed draft assets, and retry only before publication.

## Release Asset Set

Each marketplace release should upload an explicit, deterministic asset set.
The exact names belong in the later publication contract, but the set must
cover these roles:

| Asset role | Required contents |
|---|---|
| Marketplace index | Persisted contract version, marketplace release identity, exact package versions, package content digests, dependencies, default package, and asset references. |
| Provider-neutral package artifacts | One deterministic archive per selected package version, or one deterministic aggregate archive with an indexed file map. |
| Codex payloads | Hydrated, validated Codex plugin artifacts and projection receipts. |
| GitHub Copilot payloads | Hydrated, validated Copilot plugin artifacts and projection receipts. |
| Checksums | A deterministic checksum manifest covering every uploaded semantic asset by exact filename, size, and SHA-256 digest. |
| Publication receipt | Repository identity, release ID, immutable tag, tag commit SHA, publication timestamp, and validation evidence. |

GitHub's asset `digest` field and release attestation provide independent
server-side integrity evidence. The checksum asset remains necessary because a
consumer can cache it with the lock graph and verify local content without
calling GitHub.

GitHub-generated source archives are not suitable package artifacts. They are
created on demand and `gh release verify-asset` cannot verify them. The
publisher should upload deterministic package archives explicitly.

## Exact Resolution And Reconstruction

Discovery may list releases or inspect a user-selected repository, but a
resolved lock must never depend on the moving “latest release” endpoint. Exact
resolution starts from a chosen immutable tag and records enough evidence to
detect substitution.

For each marketplace release, lock evidence should include:

- canonical repository URL and repository owner/name;
- release ID and exact immutable tag;
- tag commit SHA from verified release or attestation evidence;
- marketplace index asset ID, filename, size, and SHA-256 digest;
- every selected package version and content digest;
- each required package or provider asset ID, filename, size, and SHA-256
  digest; and
- the persisted contract versions used to parse the index and lock.

The content digest is the final identity check. Repository, release, and asset
IDs are transport locators and substitution evidence; they cannot replace
content verification.

## Authentication And Authorization Boundaries

Read and write operations have intentionally different authority.

| Capability | Minimum documented authority |
|---|---|
| Read public release or asset | No authentication required, subject to unauthenticated limits. |
| Read private release or asset | Contents read using a supported GitHub App, installation, or fine-grained personal token. |
| Check immutable-release policy | Repository Administration read. |
| Create, update, or publish release | Repository Contents write; some cases may also require Workflows write. |
| Upload, rename, or delete draft assets | Repository Contents write. |
| Enable or disable immutable releases | Repository Administration write. |

GitHub Actions supplies an ephemeral, repository-scoped `GITHUB_TOKEN`. A
publication workflow should grant only the permissions it uses, normally
Contents write for release operations. The immutable-policy preflight requires
Administration read, which may need a separately authorized GitHub App or
fine-grained token. Policy enablement must remain a separate administrator
workflow.

Public unauthenticated REST requests are limited to 60 requests per hour per IP.
Authenticated user requests normally receive 5,000 requests per hour, while a
repository `GITHUB_TOKEN` normally receives 1,000 requests per hour. Secondary
limits also constrain concurrency and content creation. Clients must inspect
rate-limit headers, honor `retry-after` and reset times, serialize publication
mutations, and use bounded exponential backoff rather than blind retries.

## Mutating Operations

The publication boundary consists of a small ordered mutation set.

1. Create a draft release against an existing exact commit SHA.
2. Upload assets sequentially or with bounded concurrency.
3. Delete only failed or duplicate assets from the still-draft release.
4. Publish by changing the draft state once validation is complete.

Creating releases triggers notifications and counts as content generation.
Update and delete endpoints exist, but they are not normal maintenance tools
for immutable marketplace releases. Once published, the portable contract must
model the release as terminal. A correction is a new semantic marketplace
release, not an edit, deletion, tag move, or asset overwrite.

## Attestations And Checksums

An immutable release automatically receives a release attestation. Consumers
can use `gh release verify` and `gh release verify-asset` to verify server-side
release identity and local asset equality. GitHub Actions can also generate
artifact attestations that record workflow and source provenance.

Attestations strengthen provenance but do not prove that an artifact is safe or
semantically valid. Publication still requires the source, projection, and
hydrated-output validation gates. The lock and checksum manifest remain the
portable integrity contract; attestation verification is additional evidence
that the assets came from the expected GitHub release and build identity.

## Offline Limitations

GitHub discovery, release lookup, asset download, policy inspection, and remote
attestation lookup require network access. A consumer can operate offline only
from already-cached content and already-persisted lock evidence.

Offline reconstruction is valid when:

- the exact marketplace index, package artifacts, provider payloads, and
  checksum manifest are present in the content-addressed cache;
- every cached byte sequence matches its locked SHA-256 digest and size;
- the lock graph is complete and its persisted contract versions are
  supported; and
- no operation requires discovering a newer version or fetching an uncached
  dependency.

When any required artifact is absent, offline resolution must fail without
changing consumer intent, lock state, provider output, or cache metadata. A
cached “latest” answer must not be presented as current remote state.

## Consequences For The Publication Contract

The later specification should encode these non-negotiable rules:

- immutable-release policy is a precondition, verified before creating a draft;
- the release tag selects one immutable marketplace release, while package
  versions remain independently versioned inside its index;
- every publication uses a draft-upload-verify-publish lifecycle;
- all package and provider artifacts are explicit release assets, never
  generated source archives;
- publication is rejected unless source validation, deterministic projection,
  hydrated provider validation, checksum agreement, and asset read-back all
  pass;
- successful publication records exact GitHub locators and content digests;
- published releases are never updated in place; and
- offline use is cache-only and fail-closed.

## Sources

1. [Immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases)
   — tag and asset protection, automatic release attestations, resurrection
   protection, and the draft-first publication workflow.
2. [Preventing changes to releases](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/establish-provenance-and-integrity/prevent-release-changes)
   — repository and organization policy scope and the future-release boundary.
3. [Repository REST endpoints](https://docs.github.com/en/rest/repos/repos)
   — immutable-release policy checks and Administration permission boundaries.
4. [Release REST endpoints](https://docs.github.com/en/rest/releases/releases)
   — draft creation, publication, exact tag lookup, release fields, and
   Contents permissions.
5. [Release asset REST endpoints](https://docs.github.com/en/rest/releases/assets)
   — uploads, downloads, asset digests, duplicate names, failed uploads, and
   public/private read behavior.
6. [Verifying release integrity](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/verify-release-integrity)
   — immutable-release and local-asset verification, including the source
   archive limitation.
7. [Artifact attestations](https://docs.github.com/en/actions/concepts/security/artifact-attestations)
   — provenance claims, verification responsibilities, and public/private
   transparency boundaries.
8. [REST API rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)
   — primary and secondary limits, response headers, and retry requirements.
9. [GITHUB_TOKEN](https://docs.github.com/en/actions/concepts/security/github_token)
   — repository-scoped workflow authentication and token lifetime.
