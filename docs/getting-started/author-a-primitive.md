# Author A Primitive

Author primitives inside the package that ships them. Do not create a separate
source tree that requires a custom generator.

## Layout

```text
packages/example-package/
  apm.yml
  .apm/
    skills/example-skill/SKILL.md
    agents/example-agent.agent.md
    instructions/example.instructions.md
    hooks/example-codex-hooks.json
  hook-config/example.requirements.json
  hooks/example.sh
```

## Workflow

1. Choose the package under `packages/`.
2. Add or edit the primitive under that package's `.apm/` tree.
3. Keep hook scripts under package `hooks/` when hook JSON references them.
   Keep hook sidecar JSON under `hook-config/`.
4. Update the package `apm.yml` only when metadata changes.
5. Update root `apm.yml` only when marketplace exposure changes.

## Validate

```sh
python3 -m json.tool packages/example-package/.apm/hooks/example-codex-hooks.json
bash -n packages/example-package/hooks/example.sh
apm pack --marketplace=all --dry-run --check-versions --json
apm audit --ci --no-policy
```
