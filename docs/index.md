# amichne-intelligence

`amichne-intelligence` is a marketplace-only source graph for reusable AI
tooling primitives and plugin families. The source graph is authored under
`source/`, with `source/adaptable.marketplace.json` controlling marketplace
exposure.

```mermaid
flowchart LR
  primitive[Independent primitives in source/]
  plugin[source/plugins/*/plugin.json]
  marketplace[source/adaptable.marketplace.json]
  cli[Kotlin CLI]
  output[Provider marketplace payloads]
  runtime[Consumer runtimes]

  primitive --> plugin --> marketplace --> cli --> output --> runtime
```

## Start Here

Browse the marketplace from the installed CLI.

```sh
intelligence marketplace browse amichne/intelligence
intelligence marketplace browse amichne/intelligence --format json
```

Validate the source graph when authoring this repository.

```sh
intelligence validate
```

## What You Can Do

| Job | Entry Point | Result |
|---|---|---|
| Inspect plugin families | [What is available](available/index.md) | A map of plugin families and primitives. |
| Consume the marketplace | [Marketplace](getting-started/marketplace.md) | Browse marketplace offerings by repository reference. |
| Author a primitive | [Author a primitive](getting-started/author-a-primitive.md) | A source-owned primitive referenced by plugins. |
| Validate publishing | [Validation](how-it-works/validation.md) | Source and hydrated output checks before release. |
