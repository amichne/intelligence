# Commands

This page collects the commands most readers need while working in this
repository.

## Repository CLI

`bin/intelligence` is the local orchestration entrypoint.

| Command | Purpose |
|---|---|
| `bin/intelligence --help` | Show available subcommands. |
| `bin/intelligence validate` | Run manifest validation gates. |
| `bin/intelligence profile init --repo /path/to/repo --profile kotlin-repo-default` | Create a target-repository workflow profile. |
| `bin/intelligence install --repo /path/to/repo --profile .agents/intelligence-profile.json` | Dry-run profile installation. |
| `bin/intelligence primitive new skill example-skill --plugin primitive-systems-authoring` | Scaffold a new primitive and optionally reference it. |

## npm Scripts

The root `package.json` pins validator dependencies and exposes common tasks.

| Script | Purpose |
|---|---|
| `npm run intelligence -- --help` | Run the repository CLI through npm. |
| `npm run validate:manifests` | Run `node scripts/validate-manifests.mjs`. |
| `npm run marketplace:materialize` | Materialize Codex marketplace output under `/tmp/intelligence-codex-marketplace`. |
| `npm run marketplace:materialize:github` | Materialize GitHub marketplace output under `/tmp/intelligence-github-marketplace`. |
| `npm run marketplace:sync-main` | Regenerate the adapted marketplace manifests checked into `main`. |
| `npm run marketplace:sync-main:check` | Fail if the adapted marketplace manifests on `main` are stale. |
| `npm run marketplace:publish:preview` | Build the generated Codex branch locally without pushing. |
| `npm run marketplace:publish:github:preview` | Build the generated GitHub branch locally without pushing. |
| `npm run package:cli -- --version local` | Build local CLI archives under `dist/`. |

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
