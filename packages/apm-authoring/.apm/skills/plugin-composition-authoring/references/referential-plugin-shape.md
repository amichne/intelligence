# APM Package Shape

Use this reference when creating or reviewing `packages/<name>/apm.yml` and the
matching package `.apm/` tree.

## Manifest Pattern

```yaml
name: example-package
version: 0.1.0
description: Capability family composed from package-owned primitives.
author: Amichne
license: Apache-2.0
type: hybrid
targets: [claude, codex]
```

## Primitive Layout

```text
packages/example-package/
  apm.yml
  .apm/
    skills/example-skill/SKILL.md
    agents/example-agent.agent.md
    instructions/example.instructions.md
    hooks/example-codex-hooks.json
  hooks/example.sh
```

## Rules

- Keep primitives under `.apm/`; do not route through a custom generator.
- Keep hook scripts under package `hooks/` when hook JSON invokes them.
- Use target suffixes such as `-codex-hooks.json` for target-specific hooks.
- Keep root and package `apm.yml` files declarative.
- Validate with `apm pack --marketplace=all --dry-run --check-versions --json`.
