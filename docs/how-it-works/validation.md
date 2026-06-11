# Validation

Validation checks marketplace source documents, hydrated provider output, and
the CLI repo's own schema contracts.

## CLI Repository

Run these checks when changing the Kotlin CLI, schemas, packaging, or docs.

```sh
./gradlew :cli:test installDevelopmentCli
.local/intelligence/bin/intelligence validate --portable
zensical build --clean
```

## Marketplace Repository

Run these checks when changing `slopsentral`.

```sh
intelligence validate --repo /path/to/slopsentral --portable
intelligence marketplace materialize --repo /path/to/slopsentral
intelligence validate --repo /path/to/slopsentral --portable --hydrated /path/to/slopsentral/build/intelligence/marketplace
```

## Publish Proof Path

For publish flows, let the CLI validate source, materialize provider output into
a temporary proof root, verify hydration, and publish only after those checks
pass.

```sh
intelligence marketplace publish --repo /path/to/slopsentral --check
```

If a provider check fails, repair projection logic or schema boundaries, then
regenerate outputs instead of patching generated payloads by hand.
