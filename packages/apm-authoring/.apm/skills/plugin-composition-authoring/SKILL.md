---
name: "plugin-composition-authoring"
description: "Create, revise, or validate APM package and marketplace manifests that publish package-owned .apm primitives. Use when adding a package family, updating marketplace entries, checking package contents, or converting legacy plugin bundles into APM-native packages."
---

# Plugin Composition Authoring

Use this skill to create or revise APM packages and marketplace entries. The package owns its `.apm/` primitives, root `apm.yml` owns marketplace exposure, and generated marketplace JSON is produced only by `apm pack`.

## Operating Contract

- Author primitives in `.apm/skills`, `.apm/agents`, `.apm/instructions`, `.apm/hooks`, and `.apm/prompts`.
- Keep package `apm.yml` small and declarative.
- Treat the root `apm.yml` `marketplace:` block as the marketplace source of truth.
- Use map-form `marketplace.outputs`; include Codex `category` on every package when Codex output is enabled.
- Add marketplace entries only after the package directory and package `apm.yml` exist.
- Keep primitive provenance public-safe; the package itself should not be the
  only place provenance lives.
- Validate with APM checks before committing marketplace or package changes.

## Workflow

1. Define the package family.
   Name the composed capability set, the primitives it should assemble, and
   what should remain outside the package.

2. Confirm primitive independence.
   Verify every skill, agent, hook, and instruction exists in the package
   `.apm/` tree and makes sense beyond one marketplace listing.

3. Write `packages/<name>/apm.yml` and package-owned `.apm/` primitives.
   Prefer `targets: [claude, codex]` unless the package has a narrower target.

4. Update the root `apm.yml` `marketplace.packages` entry.
   Use `packages:` in `apm.yml`; APM emits `plugins:` in generated marketplace JSON.

5. Validate.
   Run `apm pack --marketplace=all --dry-run --check-versions --json` and `apm audit --ci --no-policy`.

## Reference Routing

- Load [referential-plugin-shape.md](references/referential-plugin-shape.md)
  when converting legacy plugin references into APM package contents.
- Load [marketplace-catalog.md](references/marketplace-catalog.md) when editing
  the root `apm.yml` marketplace block or marketplace package metadata.

## Completion Criteria

- Package primitives live under `.apm/` and package scripts/assets stay
  package-adjacent.
- The root `apm.yml` marketplace block points at an existing local package.
- APM package manifests and marketplace outputs validate.
- First-party source handling remains clean.
