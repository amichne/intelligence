---
type: Reference
title: Intelligence commands
description: User-facing command catalog for the minimal portable marketplace V1.
tags: [cli, commands, v1]
timestamp: 2026-07-13T01:11:59-04:00
---

# Commands

The CLI is non-interactive and exposes one stable human/automation surface.
[Minimal public CLI V1](cli-contract-v1.md) is authoritative for option shapes,
JSON envelopes, dry-run and offline behavior, diagnostics, and exit codes.

## Start and Validate

```sh
intelligence doctor
intelligence doctor --format json
intelligence validate --portable
intelligence validate --repository /path/to/repository
```

`doctor` is read-only. Portable validation does not depend on the local home
directory, GitHub authentication, or network access.

## Discover and Inspect

Discovery searches one explicit repository or user-maintained catalog. It never
selects a result.

```sh
intelligence marketplace discover --github amichne/slopsentral
intelligence marketplace discover --catalog ./marketplaces.json --query kotlin
intelligence marketplace inspect \
  --github amichne/slopsentral \
  --snapshot initial-kotlin-marketplace
```

Local inspection names both the directory and its expected canonical index
digest:

```sh
intelligence marketplace inspect \
  --local-snapshot /tmp/intelligence-snapshot \
  --index-sha256 8a7d9e8d9fbd780b5c57a94c155c2f68844c1ca9f8ae7e46a02f2992fdc786ab
```

## Set Up and Select

First-time setup with no source uses exact bootstrap GitHub coordinates and
index digest metadata packaged with the CLI. It requires network access. With
no selection option it selects that snapshot's default package.

```sh
intelligence setup
intelligence setup --package kotlin-engineering
intelligence setup --all --dry-run
```

An explicit selection adds whole packages and writes a complete replacement
state transaction. It never adds dependencies or exposes individual
primitives. Local snapshot paths used by consumer mutations are relative to the
target repository.

```sh
intelligence marketplace select slopsentral \
  --github amichne/slopsentral \
  --snapshot initial-kotlin-marketplace \
  --package kotlin-engineering

intelligence marketplace select slopsentral \
  --local-snapshot .fixtures/intelligence-snapshot \
  --index-sha256 8a7d9e8d9fbd780b5c57a94c155c2f68844c1ca9f8ae7e46a02f2992fdc786ab \
  --all

intelligence marketplace remove slopsentral \
  --package kotlin-engineering
```

## Update and Reconstruct

Update requires an explicit replacement snapshot and preserves the existing
package names. Reconstruction needs no marketplace source when the lock and
digest-addressed cache are complete.

```sh
intelligence marketplace update slopsentral \
  --github amichne/slopsentral \
  --snapshot corrected-kotlin-marketplace

intelligence marketplace resolve slopsentral
intelligence marketplace recover --dry-run
intelligence marketplace recover
intelligence marketplace reconstruct
intelligence marketplace reconstruct slopsentral --offline
```

Reconstruction restores verified cache content. Projection is a separate,
explicit output transaction for one marketplace and one provider, so package
names from unrelated marketplaces never collide implicitly.

```sh
intelligence marketplace project slopsentral \
  --provider codex \
  --out /tmp/slopsentral-codex

intelligence marketplace project slopsentral \
  --provider github-copilot \
  --out /tmp/slopsentral-copilot
```

## Materialize and Publish

Materialization always produces the complete provider-neutral snapshot and both
required provider projections. Publication accepts only that already-complete
release directory.

```sh
intelligence marketplace materialize \
  --source /path/to/marketplace-source \
  --snapshot initial-kotlin-marketplace \
  --out /tmp/intelligence-release

intelligence marketplace publish \
  --release-dir /tmp/intelligence-release \
  --github amichne/slopsentral \
  --commit 0123456789abcdef0123456789abcdef01234567 \
  --dry-run

intelligence marketplace verify-publication \
  --github amichne/slopsentral \
  --snapshot initial-kotlin-marketplace
```

## Author Guidance and Help

```sh
intelligence marketplace author
intelligence --help
intelligence marketplace --help
intelligence marketplace select --help
```

The author command points to the source contracts and does not create a second
manifest format or mutate a repository.

## Build the CLI

```sh
./gradlew :cli:test installDevelopmentCli
./gradlew :cli:distTar verifyKotlinOnlyDevelopmentCli
```

The release distribution is a JVM application archive containing launchers and
JARs only. See [Publication](publication.md) for repository release mechanics.
