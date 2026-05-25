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
| `bin/intelligence primitive new skill example-skill --plugin primitive-authoring` | Scaffold a new primitive and optionally reference it. |

## npm Scripts

The root `package.json` pins validator dependencies and exposes common tasks.

| Script | Purpose |
|---|---|
| `npm run intelligence -- --help` | Run the repository CLI through npm. |
| `npm run validate:manifests` | Run `node scripts/validate-manifests.mjs`. |
| `npm run marketplace:materialize` | Materialize marketplace output under `/tmp/intelligence-marketplace`. |
| `npm run marketplace:publish:preview` | Build the generated branch locally without pushing. |
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
