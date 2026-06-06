# amichne-apm

`amichne-apm` is an APM-native marketplace for reusable AI tooling primitives.
The repository publishes package-owned skills, agents, hooks, and instructions
through the Microsoft APM marketplace flow.

APM is the point of arrival here. The root `apm.yml` is the marketplace source
of truth, packages live under `packages/`, and generated marketplace JSON is
created by `apm pack`.

## Repository Shape

- `source/adaptable.marketplace.json` is the curated provider-neutral marketplace catalog.
- `source/agents/` contains independent reusable agent profiles.
- `source/skills/` contains independent reusable skills.
- `source/concepts/` contains portable instruction and principle documents.
- `source/hooks/` contains hook metadata, implementations, requirements, and provider
  adapters.
- `source/plugins/` contains referential plugin composition manifests.
- `source/profiles/` contains workflow profiles for target repositories.
- `source/templates/` contains primitive scaffold templates used by repository CLI tooling.
- `source/schemas/` contains public provider-neutral and adapter schema contracts.
- Provider marketplace payloads are materialized outputs generated from
  `source/` by the Kotlin CLI. They belong in explicit output directories or
  generated branches, not in the source tree.
- `scripts/` contains root validation helpers invoked by the Kotlin CLI.
- `docs/` contains the public documentation site source.

## Validate

Use SDKMAN to select the pinned GraalVM JDK when building the Kotlin CLI:

```sh
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env install
```

Install pinned validation dependencies and run the manifest gate:

```sh
npm ci
npm run cli:install-dev
.local/intelligence/bin/intelligence validate
```

Build or refresh the local Kotlin CLI during development:

```sh
./gradlew installDevelopmentCli
.local/intelligence/bin/intelligence validate
```

Build docs after documentation or navigation changes.

```sh
zensical build --clean
```

## Publish

`source/adaptable.marketplace.json` keeps the provider-neutral source catalog.
The generated `codex` and `github` branches are materialized from that source,
and local previews should be written outside the repository. The referential
plugin manifests live under `source/plugins/`.

Preview the branch output locally:

```sh
npm ci
npm run cli:install-dev
.local/intelligence/bin/intelligence marketplace materialize --provider codex --out /tmp/intelligence-codex-marketplace
.local/intelligence/bin/intelligence validate --portable --hydrated /tmp/intelligence-codex-marketplace
.local/intelligence/bin/intelligence marketplace publish-branch --provider codex --branch codex --no-push
.local/intelligence/bin/intelligence marketplace materialize --provider github --out /tmp/intelligence-github-marketplace
.local/intelligence/bin/intelligence validate --portable --hydrated /tmp/intelligence-github-marketplace
.local/intelligence/bin/intelligence marketplace publish-branch --provider github --branch github --no-push
.local/intelligence/bin/intelligence marketplace materialize --provider all --out /tmp/intelligence-marketplace
```

Merges to `main` run `.github/workflows/publish-marketplace.yml`, which
validates source contracts, materializes the Codex and GitHub marketplace roots,
and force-updates the generated `codex` and `github` branches. The source branch
does not carry materialized provider payloads.

## CLI Archives

Build local distribution archives:

```sh
npm run package:cli
```

Gradle writes CLI distributions under `cli/build/distributions/`.
