# Getting Started

Start with the APM manifest. The root `apm.yml` exposes local packages through
the `amichne-apm` marketplace.

## Common Tasks

| Task | Command |
|---|---|
| Preview marketplace outputs | `apm pack --marketplace=all --dry-run --check-versions --json` |
| Audit package content | `apm audit --ci --no-policy` |
| Build docs | `zensical build --clean` |

## Next Pages

- [Marketplace](marketplace.md) explains consumption and publishing shape.
- [Author a primitive](author-a-primitive.md) explains package-owned `.apm/`
  primitives.
