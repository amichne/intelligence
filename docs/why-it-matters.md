# Why It Matters

Reusable agent behavior is hard to trust when authoring, provider projection,
and local runtime installation are mixed together.

`intelligence` keeps the boundary explicit:

| Problem | Boundary |
|---|---|
| Reusable skill ownership | Authored in `amichne/slopsentral`. |
| Provider-specific payload drift | Generated through `intelligence marketplace materialize` and `publish`. |
| Consumer repo installs | Recorded in `.intelligence/adaptable.marketplace.json` and `.intelligence/marketplace-lock.json`. |
| Structured data drift | Checked by schemas and `intelligence validate`. |

The CLI is useful when the same marketplace behavior should survive across
repositories, machines, runtimes, or release cycles.
