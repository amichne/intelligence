---
type: Research Note
title: GitHub Copilot projection contract research
description: Official GitHub Copilot packaging and runtime boundaries for a provider-neutral package projection.
resource: https://github.com/amichne/intelligence/issues/31
tags: [github-copilot, projection, plugins, skills, agents, hooks, marketplace]
timestamp: 2026-07-12T21:45:00-04:00
---

# GitHub Copilot Projection Contract Research

This note identifies the current official GitHub Copilot artifacts that a
portable package projection can target. It separates Copilot's installable
plugin contract from repository customizations and IDE-only files so the
portable source model does not inherit provider-specific paths or support
claims.

## Finding

GitHub Copilot now has a first-class plugin and marketplace contract shared by
Copilot CLI and, for declarative installation, Copilot cloud agent. A plugin is
a directory with a required root `plugin.json`. It can package custom agents,
skills, hooks, commands, extensions, MCP server configuration, and LSP server
configuration. A marketplace is a `marketplace.json` catalog, canonically
stored at `.github/plugin/marketplace.json` in a repository.

This plugin surface is the appropriate projection target for portable
packages. Repository-wide instructions, path-specific instructions,
`AGENTS.md`, and `.github/prompts/*.prompt.md` are separate repository or IDE
customizations. They are not documented plugin components and do not have the
same runtime coverage. Projection must not copy them into a plugin and claim
that Copilot consumes them.

## Required Plugin And Marketplace Artifacts

These requirements come from the current official GitHub documentation.

| Surface | Required contract |
|---|---|
| Plugin root | Contains a discoverable `plugin.json`; the canonical projection uses the plugin root rather than an alternate compatibility location. |
| Plugin identity | `name` is required, kebab-case, limited to letters, numbers, and hyphens, and no longer than 64 characters. |
| Marketplace root | Contains `.github/plugin/marketplace.json` for repository distribution. |
| Marketplace identity | Top-level `name`, `owner`, and `plugins` are required; the name is kebab-case and no longer than 64 characters. |
| Marketplace entry | Each entry requires a kebab-case `name` and a `source` path or source object. |
| Validation mode | Marketplace entry `strict` defaults to `true`; portable output must preserve strict validation rather than opting into relaxed legacy handling. |

Plugin metadata such as description, semantic version, author, repository,
license, keywords, category, and tags is optional to Copilot. The portable
contract nevertheless requires the independently versioned package identity,
so its Copilot manifest and marketplace entry should always emit the exact
package version.

## Documented Plugin Components

The manifest supports explicit component paths and conventional defaults.

| Component | Manifest field | Default or canonical payload |
|---|---|---|
| Custom agents | `agents` | `agents/*.agent.md` |
| Agent skills | `skills` | `skills/<skill-name>/SKILL.md` |
| Commands | `commands` | One or more command directories; Copilot CLI commands are simplified Markdown alternatives to skills. |
| Hooks | `hooks` | `hooks.json` or `hooks/hooks.json`, or an inline hooks object. |
| Extensions | `extensions` | One or more extension directories or an object with paths and exclusivity. |
| MCP servers | `mcpServers` | `.mcp.json`, `.github/mcp.json`, or an inline server map. |
| LSP servers | `lspServers` | `lsp.json`, `.github/lsp.json`, `lsp-config/servers.json`, or inline definitions. |

The plugin manifest accepts explicit paths so generated output does not need to
depend on discovery defaults. The projection should emit one canonical layout
and explicit component fields. That makes the generated tree deterministic and
keeps validation independent of alternative compatibility paths.

## Component Validation Boundaries

Each projected component remains responsible for its own typed format.

### Skills

A skill is a directory containing a file named `SKILL.md`. Its YAML frontmatter
requires `name` and `description`. Names use letters, numbers, and hyphens and
are limited to 64 characters; descriptions are limited to 1,024 characters.
Optional fields include argument hints, allowed tools, user invocation, and
model-invocation policy. Scripts and resources live inside the skill directory
and are made available with the skill.

### Custom agents

An agent is a Markdown file whose filename determines its identifier. Both
`.agent.md` and `.md` are supported by Copilot CLI; the canonical plugin layout
uses `.agent.md`. YAML frontmatter requires `description` and may define name,
model, tools, MCP servers, user invocation, and model-invocation policy. The
Markdown body is the agent prompt and is limited to 30,000 characters.

### Hooks

Hook configuration is JSON with version `1`. Hooks execute external commands
at lifecycle events. Copilot CLI runs hooks on the developer's machine, while
cloud agent runs a subset in an ephemeral, non-interactive Linux sandbox with
restricted networking. Cross-surface hooks must use behavior available in both
environments and cannot assume PowerShell, persistent files, arbitrary network
access, prompts, or a user approval dialog in cloud jobs.

### MCP And LSP

MCP and LSP declarations start external processes or connect to services, so
they are executable capability boundaries rather than passive metadata. LSP
definitions require a command form and a file-extension-to-language map. MCP
definitions remain subject to user, repository, and enterprise policy.
Generated declarations must not embed secrets; cloud-agent secrets and
variables are configured downstream.

## Runtime Consumption Boundary

Materialization and provider installation are separate operations.

1. Copilot CLI registers or reads a marketplace and resolves a plugin source.
2. Installed marketplace plugins are copied beneath
   `~/.copilot/installed-plugins/<marketplace>/<plugin>/`; direct installs use
   the `_direct` area.
3. The CLI reads the installed copy, not the mutable authoring directory. A
   changed local plugin must be installed again before the changes are loaded.
4. Copilot CLI can install, update, enable, disable, list, and uninstall
   plugins imperatively.
5. Copilot CLI and cloud agent can install plugins declaratively from
   `.github/copilot/settings.json` through `enabledPlugins` and
   `extraKnownMarketplaces`.
6. Project and personal components can shadow plugin agents or skills with the
   same identity. MCP server collisions follow a different, last-wins order.

The portable CLI should produce and validate the plugin and marketplace
payloads, but should not edit `.github/copilot/settings.json`, register a
marketplace, install a plugin, enable it, approve tools, or configure secrets.
Those are consumer-owned runtime operations and may also be constrained by
enterprise policy.

## Primitive Support Boundary

The official installable surface supports the following conservative mapping.

| Portable concept | Copilot plugin projection status | Evidence or constraint |
|---|---|---|
| Package | Supported as one plugin directory and `plugin.json`. | Plugin is the documented installable distribution unit. |
| Marketplace | Supported as `.github/plugin/marketplace.json`. | Catalog entries point to versioned plugin sources. |
| Skill | Supported as `skills/<name>/SKILL.md` with owned scripts and resources. | Skill metadata and identity are validated independently. |
| Agent | Supported as `agents/<name>.agent.md`. | Agent description is required; tools and model policy are provider details. |
| Hook | Supported as versioned hook JSON plus owned executables. | Cross-surface execution constraints must be validated. |
| Prompt | Conditionally supported as a Copilot CLI command only when its semantics are an explicit slash-command workflow. | IDE prompt files are preview-only and unavailable in GitHub.com or Copilot CLI. |
| Instruction | No documented first-class plugin component. | Repository instructions are automatically loaded repository state, not installable plugin content. |
| Concept, schema, or docs | No standalone runtime plugin component. | May accompany and be referenced by a skill or agent; copying alone is not projection. |
| External tool dependency | Supported through MCP or LSP configuration when the portable package declares an equivalent capability. | Runtime policy, secrets, and approvals remain downstream. |

The final portable contract may define semantics-preserving lowerings for
conditional or unsupported kinds. Until a lowering proves equivalent runtime
consumption, the adapter must return an explicit unsupported result rather than
silently omitting or reclassifying a primitive.

## Repository Customizations Are Not Package Payload

Copilot also reads repository-wide `.github/copilot-instructions.md`,
path-specific `.github/instructions/**/*.instructions.md`, and `AGENTS.md`.
Support varies across GitHub.com, Copilot cloud agent, code review, Copilot CLI,
and IDEs. Prompt files under `.github/prompts/*.prompt.md` are limited to IDE
surfaces and are currently preview features.

These files can be valid authoring targets for a repository owner, but they are
not a portable plugin projection. Generating them into a consumer repository
would vendor provider output and mutate repository guidance, both outside this
rewrite's ownership boundary.

## Projection Validation Boundary

A hydrated Copilot payload is eligible for use or publication only when
validation proves:

- the canonical plugin manifest exists and contains the exact portable package
  name and semantic version;
- every declared component path resolves inside the plugin root and contains
  only the expected file kind;
- every agent, skill, hook, MCP, and LSP artifact passes its own structural and
  semantic contract;
- hook commands are compatible with every declared Copilot target surface;
- the marketplace manifest uses strict validation and every package entry
  resolves to the intended generated plugin;
- component identities are unique within the payload and collision behavior is
  reported rather than hidden;
- every portable primitive has exactly one recorded supported mapping or one
  explicit unsupported result; and
- output is deterministic for the same validated source and carries a source
  version, digest, projection receipt, and checksums.

The final two requirements belong to `intelligence`, not the Copilot manifest.
GitHub's plugin contract does not define a generated-output marker or a
reversible source-to-output receipt, so portable publication evidence must add
them without presenting them as Copilot runtime components.

## Required Versus Optional Conventions

This distinction prevents Copilot implementation choices from leaking into the
provider-neutral model.

| Classification | Contents |
|---|---|
| Required by Copilot | Valid plugin and marketplace manifests, valid declared component formats, strict marketplace validation, and source paths that resolve to the intended plugin. |
| Required by `intelligence` | Exact package version and digest evidence, complete primitive coverage accounting, deterministic output, a reversible projection receipt, checksums, fail-closed unsupported mappings, and hydrated-payload validation. |
| Optional Copilot convention | Alternate manifest locations, omitted default component paths, descriptive metadata, compatibility directories, commands, extensions, and LSP support. |
| Downstream consumer state | Marketplace registration, `enabledPlugins`, local installation cache, enablement, permissions, secrets, MCP policy, and enterprise allowlists. The materializer must not mutate these. |

## Sources

1. [GitHub Copilot CLI plugin reference](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference)
   — manifest schemas, marketplace schemas, file locations, strict validation,
   and component precedence.
2. [Creating a plugin for GitHub Copilot CLI](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/plugins-creating)
   — canonical plugin structure, local installation, component checks, and
   cache refresh behavior.
3. [About GitHub Copilot plugins](https://docs.github.com/en/copilot/concepts/agents/about-plugins)
   — plugin scope, marketplace installation, CLI and cloud-agent enablement,
   and provider-owned settings.
4. [Copilot customization cheat sheet](https://docs.github.com/en/copilot/reference/customization-cheat-sheet)
   — customization locations, trigger semantics, and surface support.
5. [Adding agent skills](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-skills)
   — skill layout, metadata, scripts, discovery, and trust warnings.
6. [Custom agents configuration](https://docs.github.com/en/copilot/reference/custom-agents-configuration)
   — agent frontmatter, tools, model policy, MCP configuration, and prompt
   constraints.
7. [GitHub Copilot hooks reference](https://docs.github.com/en/copilot/reference/hooks-reference)
   — hook schema, discovery, event behavior, and CLI/cloud execution
   differences.
8. [Copilot CLI configuration directory](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-config-dir-reference)
   — installed plugin state, repository settings, override order, and managed
   policy boundaries.
9. [Custom instructions support](https://docs.github.com/en/copilot/reference/custom-instructions-support)
   — instruction-file support differences across Copilot surfaces.
