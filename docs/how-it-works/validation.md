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
intelligence marketplace materialize --repo /path/to/slopsentral --provider all --out /tmp/slopsentral-marketplace
intelligence validate --repo /path/to/slopsentral --portable --hydrated /tmp/slopsentral-marketplace
```

## Publish Proof Path

For publish flows, validate source, materialize provider outputs, then verify
hydration.

```sh
intelligence marketplace materialize --repo /path/to/slopsentral --provider codex --out /tmp/slopsentral-codex
intelligence validate --repo /path/to/slopsentral --portable --hydrated /tmp/slopsentral-codex
intelligence marketplace materialize --repo /path/to/slopsentral --provider github --out /tmp/slopsentral-github
intelligence validate --repo /path/to/slopsentral --portable --hydrated /tmp/slopsentral-github
```

If a provider check fails, repair projection logic or schema boundaries, then
regenerate outputs instead of patching generated payloads by hand.
