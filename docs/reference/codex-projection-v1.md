---
type: Decision Record
title: Codex projection V1 contract
description: Minimal deterministic projection from a portable package to an installable Codex plugin.
resource: https://github.com/amichne/intelligence/issues/35
tags: [codex, projection, plugins, skills, hooks, v1]
timestamp: 2026-07-12T23:58:00-04:00
---

# Codex Projection V1 Contract

This decision record defines the complete Codex projection required by V1. It
targets the documented Codex plugin surface and deliberately excludes local
Codex configuration. The same validated source and package digest must always
produce byte-identical output.

## Decision

One portable package projects to one Codex plugin. V1 projects only `skill` and
`hook` primitives because those are the provider-native primitive kinds shared
by the two required providers. Other content may travel only as a private
supporting asset owned by one of those primitives.

This narrows the earlier exploratory eight-kind domain. It is the smallest
contract that can satisfy all three V1 requirements simultaneously:

- every public primitive has a semantics-preserving projection;
- every valid package projects to both required providers; and
- projection never edits provider or repository configuration.

An agent, instruction, prompt, concept, schema, or document presented as a
public primitive fails source validation with `UNSUPPORTED_PRIMITIVE_KIND`.
The projector never reclassifies one as a skill, copies one into an ignored
directory, or claims that an unconsumed file is a successful projection.

## Canonical Plugin Tree

The output root is generated content and is never authoring source.

```text
<package-name>/
├── .codex-plugin/
│   └── plugin.json
├── .intelligence/
│   ├── projection.json
│   └── checksums.sha256
├── skills/
│   └── <skill-name>/
│       ├── SKILL.md
│       └── <private supporting assets>
└── hooks/
    ├── hooks.json
    └── <hook-name>/
        └── <private supporting assets>
```

Empty component directories are omitted. `.codex-plugin` contains only
`plugin.json`. Provider semantic files use their documented locations;
projection evidence lives under `.intelligence` and is not listed as a Codex
component.

Portable package names and primitive names must already satisfy the common
provider-safe kebab-case contract. Projection does not sanitize, truncate, or
deduplicate an invalid identity because doing so would change identity. A name
that cannot be represented fails validation before any output is written.

## Plugin Manifest

`.codex-plugin/plugin.json` contains exactly the required identification and
component pointers plus deterministic descriptive metadata:

- `name` is the exact portable package name;
- `version` is `0.0.0-intelligence.<package-sha256>`;
- `description` is the package description;
- `skills` is `./skills/` when at least one skill exists; and
- `hooks` is `./hooks/hooks.json` when at least one hook exists.

The generated prerelease version is an adapter identity required by Codex. It
is not a portable package version, does not participate in resolution, and is
derived only from the complete package digest. The full digest avoids a second
collision policy. V1 emits no app, MCP, capability, presentation, visual,
publisher, legal, or default-prompt metadata.

JSON object members use a fixed generator-defined order and UTF-8 encoding.
Files end with one newline. The generator never embeds timestamps, absolute
paths, local usernames, cache paths, Git checkout paths, or transport URLs.

## Skill Mapping

Each `skill` maps one-to-one to `skills/<name>/SKILL.md`. The portable skill
must provide non-empty `name`, `description`, and Markdown instructions. The
projected frontmatter uses the exact portable name and description; the body is
the validated instruction body without semantic rewriting.

Every private asset owned by the skill is copied beneath that skill directory
at its declared safe relative path. Paths are normalized before validation and
must remain inside the directory. Two inputs cannot project to the same path.
Symlinks, hard links, devices, sockets, and other non-regular entries are
forbidden.

Provider-only skill metadata is absent in V1. In particular, the projection
does not synthesize `agents/openai.yaml`, invocation policy, UI metadata, or
tool dependencies.

## Hook Mapping

Every `hook` contributes one or more matcher groups to the single canonical
`hooks/hooks.json`. The portable event, matcher, command, timeout, and status
message fields map directly to the same Codex command-hook fields. The
projector accepts only event and field combinations documented as executable
by the supported Codex release contract. Parsed-but-skipped handler kinds or
fields are unsupported.

V1 therefore requires:

- handler type `command`;
- a supported lifecycle event;
- a non-empty command;
- an optional matcher only for an event that honors matchers;
- an optional positive timeout; and
- an optional non-empty status message.

Hook groups are sorted by event, hook name, matcher, and source declaration
order. Private hook assets are copied to `hooks/<hook-name>/`. Portable command
arguments address owned files through a logical plugin-root placeholder; the
Codex adapter renders it as `${PLUGIN_ROOT}`. Raw absolute paths and traversal
segments fail validation.

Projection does not execute hooks or prove that an external command exists on
the consumer machine. It does prove that every referenced owned file is
present and covered by package integrity evidence. Installing or enabling a
plugin never implies hook trust; Codex retains its separate hash-based review.

## Projection Receipt

`.intelligence/projection.json` is strict, schema-owned evidence with:

- the projection contract version and provider identifier `codex`;
- marketplace, snapshot, and package identities;
- the exact provider-neutral package digest and size;
- the generated plugin name and adapter version;
- one mapping record for every source primitive;
- every source-asset path, digest, and generated relative path; and
- the deterministic generator identity.

Because the source contract admits only supported V1 kinds, every mapping
record is successful. Missing, duplicate, or extra mapping records invalidate
the projection. Unsupported source kinds are validation failures rather than
receipt entries that normalize an unusable artifact.

`.intelligence/checksums.sha256` lists every generated regular file except
itself, sorted by UTF-8 relative path. It includes `projection.json`, the plugin
manifest, all skills, the hook configuration, and all private assets. Each line
uses lowercase SHA-256, two spaces, and a slash-separated relative path.

## Transaction Boundary

Projection is a pure staged write:

1. validate the hydrated provider-neutral package and all assets;
2. compute the complete output plan in memory;
3. write only to a new sibling staging directory;
4. validate the staged plugin, receipt, and checksums from disk;
5. atomically replace the target directory; and
6. remove the staging directory after any failure.

The target is unchanged on failure. The projector does not merge with existing
output and does not preserve unknown files. An already-valid byte-identical
target is a successful no-op.

## Validation And Failure Rules

A Codex projection is valid only when:

- the canonical tree contains no unknown semantic component paths;
- the manifest identity and adapter version match the source package evidence;
- every manifest path is `./`-relative, exists, has the expected file kind,
  and remains inside the plugin root;
- every skill and hook passes its provider-specific structural checks;
- source-to-output primitive and asset coverage is exact;
- the receipt is strict and agrees with hydrated content;
- every checksum recomputes exactly; and
- regenerating into an empty directory yields identical paths and bytes.

Failures use stable typed codes and deterministic ordering. At minimum the
contract distinguishes unsupported primitive kind, invalid identity, invalid
component, unsafe path, missing asset, digest mismatch, projection collision,
non-deterministic output, and target replacement failure. The later CLI
contract owns their JSON envelope and process exit mapping.

## Provider State Is Out Of Scope

The V1 command produces and validates payloads only. It never:

- registers, refreshes, or removes a Codex marketplace;
- installs, enables, disables, or updates a plugin;
- edits `~/.codex/config.toml`, `.codex/config.toml`, or `AGENTS.md`;
- writes `.codex/agents` custom-agent configuration;
- approves MCP tools or trusts hook definitions; or
- writes into the Codex installation cache.

Those operations remain explicit user- or provider-owned actions. Publication
may distribute the validated tree, but distribution is not installation.

## Deferred Beyond V1

V1 does not project custom agents, repository instructions, prompts, concepts,
schemas, documents, MCP servers, apps, connectors, LSP servers, UI assets, or
provider capabilities. It also does not define compatibility gates for Codex
versions. A later contract may add a component only after both required
providers have stable installable semantics or after the product explicitly
permits provider-specific packages.

## Decision History

| Choice | V1 disposition |
|---|---|
| One package becomes one plugin. | Accepted. |
| Skills and hooks are the public portable intersection. | Accepted. |
| Other document-like material travels only as private owned content. | Accepted. |
| Generated provider versions are derived from the package digest. | Accepted; this is adapter identity, not package versioning. |
| Public primitive lowering into a different provider kind. | Rejected as lossy and difficult to reverse. |
| Custom-agent files under consumer `.codex/agents`. | Rejected because projection cannot mutate provider configuration. |
| Installation, enablement, and hook trust. | Deferred to explicit provider-owned workflows outside V1. |

## Sources And Validation

The provider boundary is grounded in [Codex projection
research](codex-projection-research.md) and current official documentation:
[Build plugins](https://developers.openai.com/codex/plugins/build/), [Build
skills](https://developers.openai.com/codex/build-skills/),
[Hooks](https://learn.chatgpt.com/docs/hooks), and
[Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents).

Validate this reference page with:

```sh
python3 /Users/amichne/.codex/plugins/cache/slopsentral/code-knowledge-base/0.1.0/skills/code-knowledge-base/scripts/code_kb.py check --repo . --docs docs
zensical build --clean
```
