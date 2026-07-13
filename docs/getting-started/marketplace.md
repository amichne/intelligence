# Marketplace

V1 exposes immutable marketplace snapshots and whole packages only. Package
supporting files are verified content, not independently discoverable assets.

## Discover and Inspect

Discovery reads one explicit GitHub repository or one typed local catalog. It
never performs global search and never makes a candidate trusted by itself.

```sh
intelligence marketplace discover --github amichne/slopsentral
intelligence marketplace discover --catalog ./marketplaces.json --query kotlin

intelligence marketplace inspect \
  --github amichne/slopsentral \
  --snapshot SNAPSHOT_ID
```

## Set Up and Select

Release builds may package one exact default GitHub coordinate, snapshot, and
index digest. Otherwise, provide one complete exact source form.

```sh
intelligence setup \
  --local-snapshot snapshots/initial \
  --index-sha256 SHA256

intelligence marketplace select slopsentral \
  --github amichne/slopsentral \
  --snapshot SNAPSHOT_ID \
  --package kotlin-engineering
```

Use `--all` only when every package in that exact snapshot should be selected.
Update always names the replacement snapshot and preserves existing package
names. Remove always names whole packages.

```sh
intelligence marketplace update slopsentral \
  --github amichne/slopsentral \
  --snapshot REPLACEMENT_SNAPSHOT
intelligence marketplace remove slopsentral --package kotlin-engineering
```

## Resolve, Recover, Reconstruct, and Project

```sh
intelligence marketplace resolve
intelligence marketplace recover --dry-run
intelligence marketplace recover
intelligence marketplace reconstruct --offline
intelligence marketplace project slopsentral --provider codex --out /tmp/codex
intelligence marketplace project slopsentral --provider github-copilot --out /tmp/copilot
```

Projection writes one marketplace and one provider to an absent-or-identical
output root. It never installs provider state or composes unrelated
marketplaces.

## Materialize and Publish

An authored source contains exactly `default-package` and
`packages/<name>/package.json` plus the files declared by each package manifest.

```sh
intelligence marketplace materialize \
  --source /path/to/marketplace-source \
  --snapshot SNAPSHOT_ID \
  --out /tmp/release

intelligence marketplace publish \
  --release-dir /tmp/release \
  --github OWNER/REPOSITORY \
  --commit COMMIT_SHA

intelligence marketplace verify-publication \
  --github OWNER/REPOSITORY \
  --snapshot SNAPSHOT_ID
```

See [Commands](../reference/commands.md) for the complete stable grammar and
[Immutable snapshot publication](../reference/immutable-snapshot-publication-v1.md)
for the one-way publication protocol.
