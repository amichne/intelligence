# Author A Primitive

Author primitives under `source/` and reference them from plugin manifests. Do
not create provider payloads by hand.

## Layout

Primitive scaffolding is being moved into the Kotlin CLI. Until that command is
ported, create the primitive under `source/` from the local templates and add
plugin or marketplace references deliberately.

1. Choose the primitive kind under `source/skills/`, `source/agents/`,
   `source/hooks/`, or `source/concepts/`.
2. Add or edit the primitive in the source-owned location.
3. For hooks, keep provider-neutral metadata in `source/hooks/*.hook.json` and
   adapter JSON under directories such as `source/hooks/codex/`.
4. Update `source/plugins/<name>/plugin.json` when a plugin should compose the
   primitive.
5. Update `source/adaptable.marketplace.json` only when marketplace exposure
   changes.

## Validate

```sh
./gradlew installDevelopmentCli
.local/intelligence/bin/intelligence validate
```
