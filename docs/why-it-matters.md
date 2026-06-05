# Why It Matters

Reusable agent behavior becomes difficult to trust when it is scattered across
runtime caches, one-off prompts, or provider-specific plugin folders.

`amichne-apm` keeps the durable boundary simple:

| Problem | APM-native answer |
|---|---|
| Scattered primitives | Package-owned `.apm/` directories. |
| Unclear marketplace surface | Root `apm.yml` `marketplace.packages`. |
| Provider-specific output drift | `apm pack` generates marketplace JSON. |
| Unverified package contents | APM pack, audit, and marketplace checks. |

The repository is useful when the same agent behavior should survive across
repositories, machines, runtimes, or release cycles.
