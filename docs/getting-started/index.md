# Getting Started

The projector needs three explicit values: a provider-neutral source repository,
a target harness, and an output directory.

## Project to Codex

```sh
intelligence project \
  --source /path/to/slopsentral \
  --harness codex \
  --out /tmp/slopsentral-codex
```

A successful command returns compact TOON and writes the Codex marketplace at
`.agents/plugins/marketplace.json` beneath the output root.

## Project to GitHub Copilot

```sh
intelligence project \
  --source /path/to/slopsentral \
  --harness github-copilot \
  --out /tmp/slopsentral-copilot
```

The GitHub Copilot marketplace is written at
`.github/plugin/marketplace.json` beneath the output root.

## Verify the Repository Build

```sh
./gradlew :cli:test installDevelopmentCli verifyKotlinOnlyDevelopmentCli
.local/intelligence/bin/intelligence --help
```

Continue with [How projection works](../how-it-works/projection.md) for the
source and target ownership model.
