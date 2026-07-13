---
type: Research Note
title: Codex projection contract research
description: Official Codex packaging and runtime boundaries for a provider-neutral package projection.
resource: https://github.com/amichne/intelligence/issues/36
tags: [codex, projection, plugins, skills, hooks, marketplace]
timestamp: 2026-07-12T21:30:00-04:00
---

# Codex Projection Contract Research

This note identifies the current official Codex artifacts that a portable
package projection can target. It distinguishes provider requirements from
projection conventions that `intelligence` may define. It does not make the
portable source model depend on Codex names or paths.

## Finding

The Codex distribution unit is a plugin. Every projected plugin has one
required `.codex-plugin/plugin.json` entry point. The documented plugin
components are skills, lifecycle hooks, MCP server configuration, app or
connector mappings, and presentation assets. Skills remain the reusable
workflow authoring format; plugins are the installable distribution unit.

Codex does not document first-class plugin component slots for agents, prompts,
instructions, concepts, schemas, or general documentation. Custom agents are
first-class local configuration under `.codex/agents`, but they are not a
plugin component and their authoring format is explicitly described as subject
to evolution. `AGENTS.md` is a separate repository-guidance mechanism
discovered from the user's filesystem, not a plugin component. A portable
projection therefore cannot claim to preserve an unsupported primitive merely
by copying it into a plugin folder.

The projection contract should expose a typed support result for every
primitive. A required primitive without a semantics-preserving Codex mapping
must fail projection. The materializer must never silently omit it or mutate a
consumer repository's `AGENTS.md` or Codex configuration.

## Required Codex Artifacts

These requirements come from the current official Codex documentation.

| Surface | Required contract |
|---|---|
| Plugin root | Contains `.codex-plugin/plugin.json`; only `plugin.json` belongs in `.codex-plugin/`. |
| Plugin identity | `name`, `version`, and `description` identify the plugin. The documented minimal example uses a stable kebab-case name. |
| Component paths | Manifest paths start with `./`, resolve from the plugin root, and remain inside that root. |
| Skill | Lives at `skills/<skill-name>/SKILL.md`; `SKILL.md` contains non-empty `name` and `description` metadata. |
| Hook | Uses the default `hooks/hooks.json` or a manifest `hooks` value. Manifest hook paths remain inside the plugin root. |
| MCP servers | A manifest `mcpServers` path points to `.mcp.json`, represented either as a direct server map or a wrapped `mcp_servers` object. |
| App or connector | A manifest `apps` path points to `.app.json`. |
| Marketplace | Repo marketplaces live at `.agents/plugins/marketplace.json`; each entry identifies a plugin source and includes installation policy, authentication policy, and category. |

The manifest may additionally carry publisher, discovery, interface, legal,
and visual metadata. Those fields are optional for local packaging even though
public submissions commonly require a richer listing.

## Runtime Consumption Boundary

Materialization and Codex installation are separate operations.

1. A marketplace catalog points at a plugin source.
2. The ChatGPT desktop app installs the plugin into
   `~/.codex/plugins/cache/<marketplace>/<plugin>/<version>/` and loads the
   installed copy.
3. Plugin enabled state is user-owned configuration in
   `~/.codex/config.toml`.
4. Bundled MCP servers remain user-configurable after installation.
5. Bundled command hooks do not become trusted merely because a plugin was
   installed or enabled. Codex skips new or changed non-managed hook definitions
   until the user reviews and trusts their current hash.

This boundary means `intelligence materialize` should produce and validate a
plugin payload, but should not install it, enable it, edit Codex configuration,
or approve hooks. Those are downstream user or provider operations.

## Marketplace Source Semantics

Codex marketplace entries support local paths, Git repositories, Git
subdirectories, and npm packages. Git-backed sources may select a `ref` or
exact `sha`; local paths are resolved relative to the marketplace root and must
remain inside it. The CLI can add, list, upgrade, and remove configured
marketplace sources.

For this project, these are projection details rather than portable package
identity. The provider-neutral lock graph owns immutable versions and content
digests. A Codex marketplace payload may use an exact Git `sha` where a remote
source selector is needed, but a branch or version range cannot replace the
portable digest evidence.

## Primitive Support Boundary

The official surface supports the following conservative mapping.

| Portable concept | Codex projection status | Evidence or constraint |
|---|---|---|
| Package | Supported as one plugin folder and manifest. | Plugin is the documented installable distribution unit. |
| Skill | Supported as `skills/<name>/SKILL.md` plus optional `scripts/`, `references/`, `assets/`, and `agents/openai.yaml`. | Skill metadata requires `name` and `description`. |
| Hook | Supported as plugin lifecycle configuration and owned executable assets. | Execution still requires separate trust review. |
| External tool dependency | Supported through plugin MCP configuration or a skill's `agents/openai.yaml` dependency declaration. | User policy controls enablement and approvals. |
| App or connector mapping | Supported through `.app.json`. | The app is a provider integration, not portable source. |
| Agent | No documented first-class plugin component. | Custom agents are project or user configuration, not plugin payload. |
| Prompt | No documented first-class plugin component. | `interface.defaultPrompt` is presentation metadata, not a general prompt primitive. |
| Instruction | No documented first-class plugin component. | `AGENTS.md` is project guidance outside plugin packaging. |
| Concept, schema, or docs | No documented runtime plugin component. | May accompany a skill only when that skill consumes it; copying alone is not projection. |

The final portable contract may define lowerings for unsupported kinds, but the
Codex adapter must prove that the target runtime consumes the result with the
same required semantics. Until then, unsupported is an explicit typed outcome.

## Validation Boundary

Projection validation should run before an artifact is eligible for use or
publication. At minimum it must prove:

- the required plugin manifest exists and its identity agrees with the
  provider-neutral package identity;
- every declared component path is `./`-relative, stays inside the plugin root,
  exists, and has the expected file or directory kind;
- every projected skill contains parseable metadata with non-empty `name` and
  `description` values;
- marketplace entries contain their required policy and category fields and
  resolve to the intended plugin;
- hook configuration follows the documented event shape and does not treat
  installation as trust;
- every portable primitive has exactly one recorded supported mapping or one
  explicit unsupported result; and
- generated files are deterministic for the same validated source and carry
  the source package version and digest in projection evidence.

The last two checks are `intelligence` requirements, not Codex manifest fields.
Official Codex documentation does not define a generated-output marker or a
source-to-output receipt. The portable projection contract should add a
deterministic receipt and checksums as provider-neutral publication evidence,
while keeping that receipt outside Codex's semantic component inventory.

## Required Versus Optional Conventions

The distinction prevents current provider presentation choices from becoming
portable source requirements.

| Classification | Contents |
|---|---|
| Required by Codex | Plugin manifest entry point, safe relative component paths, valid skill metadata, valid declared hook/MCP/app files, and valid marketplace entries for marketplace distribution. |
| Required by `intelligence` | Complete primitive coverage accounting, deterministic output, a source identity and digest receipt, checksums, fail-closed unsupported mappings, and hydrated-payload validation. |
| Optional Codex convention | Rich publisher and interface metadata, assets under `assets/`, default hook location, and the example plugin storage directories. |
| Downstream user state | Marketplace registration, installation cache, plugin enabled state, MCP approval policy, and hook trust. The portable materializer must not own or mutate these. |

## Sources

1. [Build plugins](https://developers.openai.com/codex/plugins/build/) —
   plugin layout, manifest fields, marketplace metadata, path rules, install
   cache, MCP configuration, and plugin hook behavior.
2. [Build skills](https://developers.openai.com/codex/build-skills/) — skill
   layout, required metadata, discovery, optional `agents/openai.yaml`, and the
   distinction between skill authoring and plugin distribution.
3. [Hooks](https://developers.openai.com/codex/hooks/) — hook discovery,
   event configuration, plugin packaging, and hash-based trust review.
4. [Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents) —
   custom-agent configuration locations and the evolving authoring boundary.
5. [Custom instructions with AGENTS.md](https://developers.openai.com/codex/agent-configuration/agents-md/)
   — repository guidance discovery, precedence, scope, and runtime loading.
6. [Submit plugins](https://developers.openai.com/codex/submit-plugins/) —
   public listing, identity, test-case, and review requirements that are
   separate from local artifact validity.
