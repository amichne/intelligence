# amichne-intelligence

`amichne-intelligence` is the Kotlin CLI and schema contract repo for portable
marketplace browsing, import, validation, materialization, and publishing.

Reusable personal skills and plugin families now live in
[`amichne/slopsentral`](https://github.com/amichne/slopsentral). This repository
keeps only a minimal CLI validation catalog under `source/` that imports
`slopsentral/kotlin-engineering`, so the binary can prove marketplace parsing
without re-owning reusable skill source.
Consumer projects record install intent under
`.intelligence/adaptable.marketplace.json`, pin resolved content in
`.intelligence/marketplace-lock.json`, and cache source assets outside the
target repo.

## Repository Shape

- `source/adaptable.marketplace.json` is a minimal CLI-owned validation catalog
  that imports `slopsentral/kotlin-engineering`.
- `amichne/slopsentral` owns reusable skills, plugin families, hooks, agents,
  concepts, profiles, and generated provider payloads.
- `schemas/` owns the public JSON contracts for source and provider payloads.
- `cli/` owns browsing, validation, RPC dispatch, interactive marketplace
  import, materialization, and publication.
- `.intelligence/adaptable.marketplace.json` records install-only adaptable
  marketplace state for consumer repos.
- `.intelligence/marketplace-lock.json` records imported marketplace references
  and resolved content evidence for reconstruction.

## Browse Marketplace Offerings

Users with the CLI installed can browse a repository's marketplace without
knowing provider entrypoints or plugin paths.

```sh
intelligence marketplace browse amichne/slopsentral
intelligence marketplace browse /path/to/slopsentral --provider source
intelligence marketplace browse amichne/slopsentral --format json
```

Automation and future interactive clients can use the same marketplace semantics
through the JSON-RPC stdio contract:

```sh
printf '%s\n' '{"jsonrpc":"2.0","id":"browse","method":"marketplace.browse","params":{"repository":"amichne/slopsentral","provider":"auto"}}' \
  | intelligence rpc
```

## Import Marketplace References

Marketplace repos can import plugin entries by repository reference. Users do
not need to clone the source repository first: the CLI resolves the reference,
defaults to `main` when no ref is supplied, writes a `MARKETPLACE_SOURCE` plugin
reference in the existing authored marketplace or in
`.intelligence/adaptable.marketplace.json`, and records lock evidence under
`.intelligence/marketplace-lock.json` instead of copying provider payloads into
the target repo. Resolved source assets are cached globally at
`~/.local/share/intelligence/marketplace-assets` by default, or at
`INTELLIGENCE_MARKETPLACE_ASSET_ROOT` when that environment variable is set, so
later materialization can reuse the locked assets without requiring the remote
checkout to still be present.

```sh
intelligence marketplace import amichne/slopsentral/kotlin-engineering
intelligence marketplace import amichne/slopsentral/kotlin-engineering --ref main
intelligence marketplace install amichne/slopsentral
intelligence marketplace ui
```

Named remotes remain available when a repository wants stable local aliases:

```sh
intelligence marketplace remote add shared-tools acme/shared-tools
intelligence marketplace remote list
intelligence marketplace import shared-tools/review-stack
```

The CLI stays harness-agnostic. It can print provider-specific next steps, but it
does not mutate local Codex, Copilot, or other harness user configuration.

## Development Validation

```sh
./gradlew :cli:test installDevelopmentCli
intelligence validate --portable
```

## Marketplace Materialization

```sh
intelligence marketplace materialize --repo /path/to/slopsentral --provider codex --out /tmp/slopsentral-codex-marketplace
intelligence validate --repo /path/to/slopsentral --portable --hydrated /tmp/slopsentral-codex-marketplace
intelligence marketplace materialize --repo /path/to/slopsentral --provider github --out /tmp/slopsentral-github-marketplace
intelligence validate --repo /path/to/slopsentral --portable --hydrated /tmp/slopsentral-github-marketplace
intelligence marketplace materialize --repo /path/to/slopsentral --provider all --out /tmp/slopsentral-marketplace
```

## Publication

Publish default harness payloads from the marketplace repository:

```sh
intelligence marketplace publish --repo /path/to/slopsentral
```

Preview provider orphan branch publication locally with:

```sh
intelligence marketplace publish --repo /path/to/slopsentral --codex --no-push
intelligence marketplace publish --repo /path/to/slopsentral --github --no-push
```

Build the self-contained native CLI executable with:

```sh
./gradlew :cli:nativeCompile
```

The release workflow publishes one GraalVM native executable per supported
platform/architecture target, plus `SHA256SUMS`. It does not publish JVM
application archives.

After a stable native release, install the CLI with Homebrew:

```sh
brew install amichne/intelligence/intelligence
```
