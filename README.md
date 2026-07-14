# Intelligence

Intelligence is a Kotlin/JVM projector for agent tooling. It reads one
provider-neutral marketplace source tree and converts it into the native layout
expected by one target harness.

The initial product boundary is deliberately narrow:

```text
provider-neutral source -> validate -> project -> validate -> harness payload
```

It does not install, register, publish, discover, select, cache consumer state,
modify harness configuration, or open a TUI.

## Project a Marketplace

Use one explicit source, harness, and output directory.

```sh
intelligence project \
  --source /path/to/slopsentral \
  --harness codex \
  --out /tmp/slopsentral-codex

intelligence project \
  --source /path/to/slopsentral \
  --harness github-copilot \
  --out /tmp/slopsentral-copilot
```

The command projects the marketplace, plugins, skills, agents, instructions,
hooks, and their supporting material. It validates the provider-neutral source
before conversion and validates the generated harness payload before reporting
success.

Stdout is compact TOON suitable for agents and scripts:

```text
status: projected
harness: codex
files: 42
source: "/path/to/slopsentral"
output: "/tmp/slopsentral-codex"
```

## Project in GitHub Actions

The repository also exposes a composite action that acquires a verified stable
release and projects one checked-out source tree. Its default output is a fresh
directory under `RUNNER_TEMP`.

```yaml
- name: Check out source
  uses: actions/checkout@v5

- name: Project for Codex
  id: projection
  uses: amichne/intelligence@main
  with:
    source: .
    harness: codex
    version: v0.2.7

- name: Upload projection
  uses: actions/upload-artifact@v4
  with:
    name: codex-projection
    path: ${{ steps.projection.outputs.projection-path }}
```

Use `github-copilot` for the other harness. The action returns
`projection-path`, `files`, and the resolved `version`. Pin an immutable action
ref and an exact projector version in production workflows.

The action only projects. Uploading, committing, installing, or registering the
generated payload remains a separate, explicit workflow decision.

## Repository Shape

- `source/adaptable.marketplace.json` is the provider-neutral marketplace
  entry point used by repository fixtures.
- `/Users/amichne/code/slopsentral/source/` is the canonical local source for
  reusable personal tooling.
- `schemas/core/` owns provider-neutral primitive contracts.
- `schemas/adapters/` and `schemas/marketplace/` own generated harness
  contracts.
- `cli/` owns validation and projection.
- `docs/` and `zensical.toml` own the documentation site.

## Development

Run the Kotlin and distribution gates after changing the projector.

```sh
./gradlew :cli:test installDevelopmentCli verifyKotlinOnlyDevelopmentCli
.local/intelligence/bin/intelligence --help
zensical build --clean
git diff --check
```

Reusable marketplace content belongs in
[amichne/slopsentral](https://github.com/amichne/slopsentral). This repository
owns the conversion engine and its schemas, not the personal marketplace
source.
