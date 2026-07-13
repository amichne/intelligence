---
type: Decision Record
title: GitHub Copilot projection V1 contract
description: Minimal deterministic projection from a portable skill package to a GitHub Copilot plugin.
resource: https://github.com/amichne/intelligence/issues/38
tags: [github-copilot, projection, plugins, skills, v1]
timestamp: 2026-07-13T00:42:00-04:00
---

# GitHub Copilot Projection V1 Contract

This decision record defines the complete GitHub Copilot package projection
required by V1. It targets the canonical Copilot CLI plugin shape, preserves
the same skill semantics as the Codex projection, and never mutates a Copilot
installation or consumer repository.

## Decision

One portable package projects to one GitHub Copilot plugin. The only public V1
primitive is `skill`. A package containing any other public primitive fails
source validation with `UNSUPPORTED_PRIMITIVE_KIND` before projection begins.

Skills are the minimum useful portable product because both providers consume
the same directory-oriented Agent Skills format: `SKILL.md` with `name` and
`description`, plus referenced files beneath the skill directory. The adapter
does not rewrite instructions or invent provider-specific policy.

Lifecycle hooks are explicitly deferred. Although both providers package files
called hooks, their event sets, matcher namespaces, input and output protocols,
failure behavior, timeouts, trust model, supported shells, and cloud execution
environments differ. V1 does not add wrappers, runtime shims, or a second hook
language to conceal those differences.

## Canonical Plugin Tree

```text
<package-name>/
├── plugin.json
├── .intelligence/
│   ├── projection.json
│   └── checksums.sha256
└── skills/
    └── <skill-name>/
        ├── SKILL.md
        └── <private supporting assets>
```

`plugin.json` is always at the plugin root. The projection does not use
alternate compatibility locations such as `.plugin/`, `.github/plugin/`, or
`.claude-plugin/`. `.intelligence` contains generator evidence only and is not
declared as a Copilot component.

Package and skill names must already be lowercase kebab-case identifiers with
only ASCII letters, digits, and hyphens and at most 64 characters. Projection
does not normalize, shorten, or disambiguate identities. The skill directory
name, portable primitive name, and `SKILL.md` frontmatter name are identical.

## Plugin Manifest

The root `plugin.json` contains exactly:

- `name`: the exact portable package name;
- `description`: the package description;
- `version`: `0.0.0-intelligence.sha<package-sha256>`; and
- `skills`: `skills/`.

The `version` field is optional to Copilot but emitted so the two provider
payloads share one deterministic adapter identity. It is not a portable package
version, a selectable release, or an input to resolution. The complete package
digest remains authoritative identity and appears in the receipt.

V1 emits no agents, commands, hooks, extensions, MCP servers, LSP servers,
capabilities, publisher metadata, tags, or provider invocation policy. The
manifest uses fixed member order, UTF-8, and one trailing newline. It contains
no timestamps, absolute paths, local usernames, cache paths, Git checkout paths,
or transport URLs.

## Skill Mapping

Each portable skill maps one-to-one to `skills/<name>/SKILL.md`. The projected
file contains:

- strict YAML frontmatter with the exact `name` and `description`; and
- the validated Markdown instruction body without semantic rewriting.

Descriptions are non-empty and at most 1,024 characters. Provider-only fields
such as `allowed-tools`, argument hints, invocation controls, and model policy
are absent from V1 because the Codex skill contract does not assign them the
same meaning.

Every private supporting asset owned by the skill is copied beneath that skill
directory at its declared normalized relative path. This includes referenced
Markdown, examples, schemas, templates, and optional scripts. Assets have no
standalone marketplace identity or provider component declaration.

Every source path must remain inside its skill directory after normalization.
Symlinks, hard links, devices, sockets, pipes, path traversal, case-folding
collisions, and two inputs mapping to one output path fail validation.

## Projection Receipt

`.intelligence/projection.json` is strict schema-owned evidence containing:

- `type: "INTELLIGENCE_PACKAGE_PROJECTION"` and `schemaVersion: 1`;
- `provider: "github-copilot"` and `generator: "intelligence-kotlin-v1"`;
- `marketplaceId`, `snapshotId`, and `packageName` identities;
- `adapterVersion`, derived exactly from the complete package digest;
- `packageArchive`, containing the canonical archive `name`, `sha256`, and
  `size`;
- `skills`, containing the exact `name`, `sourcePath`, and `generatedPath` for
  every skill; and
- `files`, containing the exact `sourcePath`, `generatedPath`, `sha256`,
  `size`, and `executable` mode for every projected source file.

The owning contracts are
[`package-projection-receipt-v1.schema.json`](../../schemas/core/package-projection-receipt-v1.schema.json)
and the minimal Copilot
[`plugin-v1.schema.json`](../../schemas/adapters/github-copilot/plugin-v1.schema.json).
Both reject unknown fields. Package identity is the sole selection and exposure
unit; the receipt accounts for skills and private assets but does not make them
independently installable or selectable.

The receipt is complete accounting, not a place to record partial output.
Missing, duplicate, or extra mapping records invalidate the projection.
Unsupported public kinds block projection and therefore never appear as a
nominally successful receipt entry.

`.intelligence/checksums.sha256` covers every generated regular file except
itself. Entries are sorted by UTF-8 relative path and use lowercase SHA-256,
two spaces, and a slash-separated relative path. The list includes
`projection.json`, `plugin.json`, every `SKILL.md`, and every private asset.

## Marketplace Assembly Boundary

This contract produces package plugins. The immutable snapshot publication
contract later assembles them into one provider marketplace rooted at
`.github/plugin/marketplace.json`.

That catalog must point each entry at its exact bundled plugin directory and
set `strict` to `true`. It may include the same digest-derived adapter version
for presentation, but it cannot introduce ranges, moving Git references, or a
provider version solver. Marketplace owner metadata, release asset naming, and
snapshot-level checksums belong to publication rather than each package
projection.

## Transaction Boundary

Projection is a pure staged write:

1. validate the hydrated provider-neutral package and all assets;
2. compute the complete output plan in memory;
3. write only to a new sibling staging directory;
4. validate the staged plugin, receipt, and checksums from disk;
5. atomically replace the target directory; and
6. remove the staging directory after any failure.

The previous target remains unchanged on failure. The projector does not merge
with an existing tree or preserve unknown files. A byte-identical valid target
is a successful no-op.

## Hydrated Validation

A GitHub Copilot projection passes only when:

- the canonical tree has one root `plugin.json` and no undeclared provider
  components;
- manifest identity, description, adapter version, and `skills` path agree
  with the validated package;
- every declared path resolves inside the plugin root and has the expected
  file kind;
- every skill has strict frontmatter, matching identity, a valid description,
  and non-empty instructions;
- source-to-output skill and asset coverage is exact;
- the receipt agrees with the hydrated source and output;
- every checksum recomputes exactly; and
- a second projection into an empty directory yields identical paths and bytes.

Portable validation is structural and offline. When a supported Copilot CLI is
available, an additional native smoke test may install the staged local plugin
under disposable `HOME`, `COPILOT_HOME`, and cache directories, verify the
installed identity, and discard the entire environment. Native validation must
never read or write the user's real Copilot configuration or installation
cache. Its absence cannot be reported as native proof.

Failures use stable typed codes and deterministic ordering. At minimum the
contract distinguishes unsupported primitive kind, invalid identity, invalid
component, unsafe path, missing asset, digest mismatch, projection collision,
non-deterministic output, and target replacement failure. The CLI contract owns
the machine envelope and process exit mapping.

## Provider State Is Out Of Scope

Projection and validation never:

- register, refresh, or remove a Copilot marketplace;
- install, enable, disable, update, or uninstall a user plugin;
- edit `.github/copilot/settings.json`, local settings, `AGENTS.md`, repository
  instructions, prompt files, or personal configuration;
- approve tools, configure secrets, or alter enterprise policy; or
- write into the user's installed-plugin or marketplace cache.

Publication distributes validated bytes. Installation and enablement remain
explicit downstream user or provider operations.

## Deferred Beyond V1

V1 does not project custom agents, lifecycle hooks, commands, instructions,
prompts, concepts, schemas, documents, MCP servers, LSP servers, extensions,
repository settings, or provider capabilities. It does not promise Copilot
cloud-agent installation semantics beyond the generated plugin artifact. A
later contract must justify and test any broader provider surface explicitly.

## Decision History

| Choice | V1 disposition |
|---|---|
| One package becomes one Copilot plugin. | Accepted. |
| Skill is the sole public primitive. | Accepted as the useful stable provider intersection. |
| Hook primitives and protocol adapters. | Deferred; current provider semantics diverge materially. |
| Supporting files remain private beneath their skill. | Accepted. |
| Root `plugin.json` and explicit `skills/` path. | Accepted as the canonical layout. |
| Digest-derived semantic version. | Accepted as adapter identity only. |
| Alternate compatibility paths or relaxed marketplace validation. | Rejected. |
| Provider installation or settings mutation. | Rejected from projection scope. |

## Sources And Validation

This contract is grounded in [GitHub Copilot projection
research](github-copilot-projection-research.md) and the current official
[plugin reference](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference),
[plugin creation guide](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/plugins-creating),
[Agent Skills guide](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-skills),
and [hooks reference](https://docs.github.com/en/copilot/reference/hooks-reference).

Validate this reference page with:

```sh
python3 /Users/amichne/.codex/plugins/cache/slopsentral/code-knowledge-base/0.1.0/skills/code-knowledge-base/scripts/code_kb.py check --repo . --docs docs
zensical build --clean
```
