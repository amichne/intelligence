# Validation

Validation is the trust boundary for authored package source, canonical
snapshots, consumer intent and lock state, provider projections, publication
evidence, and the Kotlin distribution.

## Repository Gate

```sh
./gradlew :cli:test installDevelopmentCli verifyKotlinOnlyDevelopmentCli
.local/intelligence/bin/intelligence validate --portable
zensical build --clean
git diff --check
```

`--portable` forbids host-local and network assumptions. Unknown structured
data, malformed canonical JSON, undeclared source files, digest mismatches, and
incomplete consumer state fail explicitly.

## Author Proof

Materialize into an absent proof root, inspect the exact result, and publish
only that already-complete directory.

```sh
intelligence marketplace materialize \
  --source /path/to/marketplace-source \
  --snapshot SNAPSHOT_ID \
  --out /tmp/marketplace-release

intelligence marketplace inspect \
  --local-snapshot /tmp/marketplace-release \
  --index-sha256 SHA256
```

Regenerate source-owned output after a failure; do not patch release assets or
consumer locks by hand.
