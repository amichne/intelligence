# Projection

Projection converts one provider-neutral source graph into one
harness-specific filesystem tree.

```mermaid
flowchart TD
  Catalog["source/adaptable.marketplace.json"]
  Plugins["source/plugins/*/plugin.json"]
  Primitives["skills, agents, instructions, hooks"]
  Validator["Source validation"]
  Adapter["Harness adapter"]
  Output["Generated marketplace and plugins"]
  TargetValidation["Hydrated-output validation"]

  Catalog --> Validator
  Plugins --> Validator
  Primitives --> Validator
  Validator --> Adapter
  Adapter --> Output
  Output --> TargetValidation
```

## Source Ownership

The marketplace catalog names plugins and standalone primitives. Plugin
manifests compose skills, agents, instructions, hooks, and supporting files.
Paths are resolved inside the owning source repository, then rewritten to their
generated target locations.

External marketplace references may be resolved as source input. Resolution is
part of obtaining the source graph; it does not install or register a provider
plugin.

## Target Ownership

| Harness | Marketplace entry point | Plugin root |
|---|---|---|
| Codex | `.agents/plugins/marketplace.json` | `.agents/plugins/<plugin>/` |
| GitHub Copilot | `.github/plugin/marketplace.json` | `.github/plugin/<plugin>/` |

Generated files are proof artifacts. Change provider-neutral source or adapter
logic, then project again; do not hand-author the target tree.
