# Getting Started

Start by choosing the job you need. The same repository can act as a local
source graph, a marketplace publisher, a workflow-profile author, and a
primitive scaffolder.

## Prerequisites

The common paths use Python, Node, npm, and Zensical.

```sh
python3 --version
node --version
npm --version
zensical --version
```

Install validation dependencies before running repository gates.

```sh
npm ci
```

Install the docs toolchain only if `zensical` is not already available.

```sh
python3 -m venv .venv-docs
. .venv-docs/bin/activate
python -m pip install -r requirements-docs.txt
```

## Choose A Path

Each path has a dry-run or validation command before it mutates anything.

| Path | Command | Use When |
|---|---|---|
| Validate this repository | `bin/intelligence validate` | You changed manifests, hooks, schemas, or source graph files. |
| Create a repo profile | `bin/intelligence profile init --repo /path/to/repo --profile kotlin-repo-default` | A target repo should declare which Intelligence plugins and hooks it uses. |
| Dry-run install | `bin/intelligence install --repo /path/to/repo --profile .agents/intelligence-profile.json --runtime codex` | You want to inspect runtime and marketplace changes first. |
| Scaffold a primitive | `bin/intelligence primitive new skill example-skill --plugin primitive-authoring` | You are adding a reusable building block. |
| Build docs | `zensical build --clean` | You changed this documentation site or navigation. |

## First Validation

Run the repo gate before trusting local state.

```sh
bin/intelligence validate
```

This wraps the source graph and manifest validation path. Use the expanded
commands in [Validation](../how-it-works/validation.md) when you need to isolate
a failing generator or schema check.
