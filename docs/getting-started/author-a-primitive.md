# Author A Primitive

Author primitives inside the package that ships them. Do not create a separate
source tree that requires a custom generator.

## Layout

Primitive scaffolding is being moved into the Kotlin CLI. Until that command is
ported, create the primitive under `source/` from the local templates and add
plugin or marketplace references deliberately.

1. Choose the package under `packages/`.
2. Add or edit the primitive under that package's `.apm/` tree.
3. Keep hook scripts under package `hooks/` when hook JSON references them.
   Keep hook sidecar JSON under `hook-config/`.
4. Update the package `apm.yml` only when metadata changes.
5. Update root `apm.yml` only when marketplace exposure changes.

## Validate

```sh
./gradlew installDevelopmentCli
.local/intelligence/bin/intelligence validate
```
