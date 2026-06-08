# Commands

This page collects the commands most readers need while working in this
repository.

## Repository CLI

The local orchestration entrypoint is generated from the Kotlin build during
development.

```sh
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env install
./gradlew installDevelopmentCli
```

| Command | Purpose |
|---|---|
| `.local/intelligence/bin/intelligence --help` | Show available subcommands. |
| `.local/intelligence/bin/intelligence validate` | Run source graph validation gates. |
| `.local/intelligence/bin/intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace` | Materialize a Codex marketplace root outside the source tree. |
| `.local/intelligence/bin/intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace` | Materialize a GitHub Copilot marketplace root outside the source tree. |
| `.local/intelligence/bin/intelligence marketplace materialize --provider all --out /tmp/intelligence-marketplace` | Materialize all provider projections outside the source tree. |
| `.local/intelligence/bin/intelligence marketplace publish-branch --provider codex --branch codex --no-push` | Build the generated Codex branch locally without pushing. |
| `.local/intelligence/bin/intelligence marketplace publish-branch --provider github --branch github --no-push` | Build the generated GitHub branch locally without pushing. |
| `./gradlew :cli:distTar :cli:distZip` | Build local CLI archives under `cli/build/distributions/`. |

## Documentation

Build the Zensical site from the repository root.

```sh
zensical build --clean
```
