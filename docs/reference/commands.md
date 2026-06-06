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
| `.local/intelligence/bin/intelligence validate` | Run manifest validation gates. |
| `.local/intelligence/bin/intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace` | Materialize a Codex marketplace root outside the source tree. |
| `.local/intelligence/bin/intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace` | Materialize a GitHub Copilot marketplace root outside the source tree. |
| `.local/intelligence/bin/intelligence marketplace publish-branch --provider codex --branch codex --no-push` | Build the generated Codex branch locally without pushing. |
| `.local/intelligence/bin/intelligence marketplace publish-branch --provider github --branch github --no-push` | Build the generated GitHub branch locally without pushing. |

## npm Scripts

The root `package.json` pins validator dependencies and exposes common tasks.

| Script | Purpose |
|---|---|
| `npm run cli:install-dev` | Refresh `.local/intelligence/bin/intelligence` from the Kotlin build output. |
| `npm run cli:run -- --args='validate'` | Run the Kotlin CLI through Gradle. |
| `npm run validate:manifests` | Run `node scripts/validate-manifests.mjs`. |
| `npm run marketplace:materialize` | Materialize Codex marketplace output under `/tmp/intelligence-codex-marketplace`. |
| `npm run marketplace:materialize:github` | Materialize GitHub marketplace output under `/tmp/intelligence-github-marketplace`. |
| `npm run marketplace:materialize:all` | Materialize both provider projections under `/tmp/intelligence-marketplace`. |
| `npm run marketplace:publish:preview` | Build the generated Codex branch locally without pushing. |
| `npm run marketplace:publish:github:preview` | Build the generated GitHub branch locally without pushing. |
| `npm run marketplace:publish:all:preview` | Build the combined generated marketplace branch locally without pushing. |
| `npm run package:cli` | Build local CLI archives under `cli/build/distributions/`. |

## Documentation

Build the Zensical site from the repository root.

```sh
zensical build --clean
```

Install the docs toolchain when needed.

```sh
python3 -m venv .venv-docs
. .venv-docs/bin/activate
python -m pip install -r requirements-docs.txt
```
