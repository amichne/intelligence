# amichne-intelligence

`amichne-intelligence` is the Kotlin CLI, Ratatui marketplace browser, and
schema contract repo for portable marketplace browsing, import, validation,
materialization, and publishing.

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
- `cli/` owns browsing, validation, RPC dispatch, marketplace import,
  materialization, and publication.
- `tui/` owns the optional Ratatui marketplace browser launched explicitly with
  `intelligence marketplace ui`.
- `.intelligence/adaptable.marketplace.json` records install-only adaptable
  marketplace state for consumer repos.
- `.intelligence/marketplace-lock.json` records imported marketplace references
  and resolved content evidence for reconstruction.

## Start With The CLI

Users with the CLI installed should start from shell discovery. The CLI can
check its GitHub host configuration, search repositories through the active
`gh` host, inspect marketplace offerings, import selected plugins, install all
exposed plugins, update, pin, and validate without memorizing provider
entrypoints.

```sh
intelligence doctor
intelligence --version
intelligence marketplace search kotlin
intelligence marketplace inspect amichne/slopsentral
intelligence marketplace search kotlin --repository amichne/slopsentral
```

Owner/repo shorthand resolves through the active GitHub CLI host. If `gh` is
configured for GitHub Enterprise, `intelligence marketplace search` and
`intelligence marketplace inspect acme/tools` use that host by default. Use
`--host github.enterprise.example` to target a specific configured host.

Use JSON when another program or agent needs stable output.

```sh
intelligence doctor --format json
intelligence marketplace search kotlin --format json
intelligence marketplace inspect amichne/slopsentral --format json
intelligence marketplace installed list --format json
```

Open the Ratatui browser explicitly when a full-screen marketplace view is more
useful than shell output.

```sh
intelligence marketplace ui
```

The JSON-RPC boundary remains available for custom clients.

```sh
intelligence marketplace browse amichne/slopsentral --format json
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
```

Import, install, update, pin, and unpin commands run portable validation after
writing marketplace state. Use `--no-validate` only when a script already has a
separate validation gate.

Named remotes remain available as an advanced path when a repository wants
stable local aliases:

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
cargo test --manifest-path tui/Cargo.toml
intelligence validate --portable
```

## Marketplace Materialization

```sh
intelligence marketplace materialize --repo /path/to/slopsentral
```

Materialization defaults to all providers and writes to
`build/intelligence/marketplace` under the target repository. Use `--provider`
or `--out` only when a script needs a single provider or a custom output root.

## Publication

Publish default harness payloads from the marketplace repository, with source
and hydrated-output checks first:

```sh
intelligence marketplace publish --repo /path/to/slopsentral --check
```

Preview provider orphan branch publication locally with:

```sh
intelligence marketplace publish --repo /path/to/slopsentral --codex --no-push
intelligence marketplace publish --repo /path/to/slopsentral --github --no-push
```

Build the self-contained native CLI executable with:

```sh
./gradlew :cli:nativeCompile
cargo build --release --manifest-path tui/Cargo.toml
```

The release workflow publishes one archive per supported platform/architecture
target containing `intelligence` and `intelligence-tui`, plus `SHA256SUMS`. It
does not publish JVM application archives.

Use the release checklist in
[`docs/reference/publication.md`](docs/reference/publication.md) before cutting
the first stable native CLI release.

After a stable native release, install the CLI with Homebrew:

```sh
brew install amichne/intelligence/intelligence
```
