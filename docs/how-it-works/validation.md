# Validation

Validation is an internal boundary of `intelligence project`, not a separate
product workflow.

## Source Gate

Before conversion, the projector checks the marketplace document, plugin
references, primitive paths, external-source policy, exact imported versions,
JSON syntax, and interface metadata. Invalid source produces no successful
projection result.

## Target Gate

After conversion, the projector checks the generated Codex or GitHub Copilot
marketplace entry point and every referenced plugin payload. This validation is
filesystem- and schema-oriented; it never invokes a provider CLI or installs a
plugin.

## Development Gate

Repository changes use the executable Kotlin proof:

```sh
./gradlew :cli:test installDevelopmentCli verifyKotlinOnlyDevelopmentCli
zensical build --clean
git diff --check
```
