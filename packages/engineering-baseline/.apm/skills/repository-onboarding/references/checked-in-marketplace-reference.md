# Checked-In Marketplace Reference

Use this reference when creating the durable setup file for a newly onboarded
repository.

## Default Path

Use `.agents/marketplaces.md` unless the repo already has a clear agent-tooling
setup path. The file should be committed with the repository.

## Required Content

The reference should include:

- Purpose: why the repo uses marketplace-driven tooling.
- Marketplace sources: every configured marketplace, its provider, source URL or
  local path, branch or ref when known, and provider entrypoint.
- Default scope: state that setup applies to the full configured marketplace set
  unless listed exclusions narrow it.
- Expected packages: package names, marketplace names, and why each is needed for
  this repo.
- Local setup notes: repo-specific instruction files, hook configs, or runtime
  settings that the marketplace setup must respect.
- Refresh path: commands or UI steps used to refresh marketplace entries.
- Validation: checks proving the reference is current and no installed cache
  payload was copied into source.

## Markdown Template

```markdown
# Agent Marketplaces

This repository consumes agent tooling from configured APM marketplaces.
Installed package payloads and runtime caches are not source-of-truth files.

## Marketplace Sources

| Provider | Marketplace | Source | Entrypoint | Scope |
|---|---|---|---|---|
| APM | amichne-apm | https://github.com/amichne/intelligence | .claude-plugin/marketplace.json | all configured packages |

## Expected Packages

| Package | Marketplace | Purpose | Status |
|---|---|---|---|
| engineering-baseline | amichne-apm | Baseline type-safety, schema-driven design, onboarding, and hooks | installed |

## Exclusions

No configured marketplaces are excluded by default.

## Refresh

- Refresh marketplace configuration through the runtime marketplace mechanism.
- Re-run the repo validation checks after changing this file.

## Validation

- The marketplace sources above resolve.
- Expected packages are installed or explicitly marked pending.
- No installed package cache or generated marketplace payload is committed.
```

## Structured Variant

If the repo needs a machine-readable reference instead of Markdown, add or name
the schema, parser, generator, or validation command in the same change. Do not
commit ungoverned JSON, TOML, or YAML setup state.
