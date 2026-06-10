# Intelligence Codex Marketplace

This branch is generated from the referential source graph on `main`.

Codex expects the marketplace manifest at `.agents/plugins/marketplace.json` and plugin payloads under `.agents/plugins/plugins/`. Each plugin payload is fully hydrated from the provider-neutral primitives and contains its own `.codex-plugin/plugin.json`.

Install with:

```sh
codex plugin marketplace add amichne/intelligence --ref codex
```
