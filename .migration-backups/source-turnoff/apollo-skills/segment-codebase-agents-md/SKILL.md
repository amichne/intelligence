---
name: segment-codebase-agents-md
description: Explore a repository, decide where hierarchical `AGENTS.md` files should live, and draft or update them so local instructions align with real codebase boundaries. Use when Codex needs to introduce or refactor repo guidance, split a monolithic root `AGENTS.md`, add scoped instructions for a polyglot or multi-module repo, map generated/manual boundaries, or rationalize agent instructions for Gradle multi-module builds, workspace monorepos, API contract trees, custom scaffolds, or mixed-language codebases.
---

# Segment Codebase AGENTS.md

## Overview

Use this skill to uplift a repository from ad hoc instructions to a coherent
`AGENTS.md` topology. Discover the real working boundaries first, then create
the minimum set of files that captures materially different guidance.

Read `references/boundary-heuristics.md` before choosing file locations. Read
`references/agents-file-contract.md` before drafting any `AGENTS.md`.

## Outcome Contract

- Produce a boundary map with candidate paths, rationale, and scope.
- Create or update the root `AGENTS.md` and only the subtree files whose local
  instructions differ materially from their nearest parent.
- Keep repo-wide rules at the highest useful ancestor.
- Keep child `AGENTS.md` files incremental. Do not restate parent guidance
  unless narrowing or overriding it.
- Make every instruction concrete enough to verify from files, manifests, or
  runnable commands.

## Delegation Gate

This skill is designed to use sub-agents for parallel discovery. If the current
request explicitly permits delegation or parallel agent work, use sub-agents for
independent discovery passes. If it does not, follow the same workflow locally
and note that the delegated path was unavailable.

## Workflow

### 1. Inventory the repository

- Inspect the top-level orchestration files first:
  `settings.gradle*`, `build.gradle*`, `pom.xml`, `package.json`,
  `pnpm-workspace.yaml`, `turbo.json`, `nx.json`, `Cargo.toml`, `go.work`,
  `Makefile`, `Taskfile.yml`, Bazel files, CI configs, and codegen configs.
- Map the major execution surfaces:
  apps, libraries, services, contracts, generated outputs, infrastructure,
  docs, templates, and tooling.
- Record which directories have distinct build, test, lint, codegen, release,
  or deployment loops.
- Distinguish source-of-truth directories from generated or vendored outputs.

### 2. Run discovery passes

When delegation is allowed, use fresh sub-agents with minimal task-local
context. Use roles like these:

1. Topology mapper: identify modules, workspaces, packages, and execution
   surfaces.
2. Toolchain analyst: identify build, test, lint, runtime, and release loops.
3. Boundary analyst: identify generated code, contracts, scaffolds, templates,
   and "do not hand-edit" zones.
4. Verifier: check the proposed `AGENTS.md` placement map for redundancy,
   missing coverage, or mismatched scope.

Use prompts that ask for evidence, not agreement. Example shapes:

- "Explore this repository and summarize its natural working boundaries, local
  commands, and directories whose instructions differ materially."
- "Inspect this repository for generated/manual boundaries, contract sources,
  and directories that should not receive direct edits."
- "Review this proposed `AGENTS.md` placement map and identify gaps,
  over-splitting, or missing edit policies."

Merge only findings backed by file paths, manifests, or concrete commands.

### 3. Choose `AGENTS.md` boundaries

Create an `AGENTS.md` only when at least one of these is true:

- The subtree has different build, test, lint, or release commands.
- The subtree uses a different language, framework, or toolchain convention.
- The subtree contains generated code, codegen contracts, or templates with
  non-obvious edit rules.
- The subtree has a different deployment surface, risk profile, or validation
  loop.
- The subtree has a different public interface or ownership model that changes
  how edits should be made.

Do not create an `AGENTS.md` just because a directory exists. Prefer one file
per meaningful working zone, not one file per package or folder.

### 4. Handle common repo shapes

- For Gradle multi-module builds, anchor on `settings.gradle*`, included builds,
  convention plugins, codegen tasks, and module clusters rather than package
  layout alone.
- For JS or TS workspaces, anchor on workspace boundaries, deployable apps,
  shared packages, and tooling roots.
- For API contract repos, separate source-of-truth contracts from generated
  clients or server stubs, then isolate handwritten adapters if they have
  different edit rules.
- For infra trees, isolate Terraform, Helm, Kubernetes, CI, or deployment
  directories when their verification loops differ from application code.
- For custom scaffolds or templates, place guidance where regeneration policy,
  template ownership, or downstream customization rules become local.

### 5. Draft the hierarchy

- Write the root `AGENTS.md` for repository-wide rules, global commands,
  cross-cutting safety constraints, and shared validation posture.
- Write child `AGENTS.md` files only for local deltas:
  local commands, language-specific conventions, codegen policy, ownership
  boundaries, or subtree-specific verification.
- Keep generated/manual boundaries explicit. State where edits belong, where
  regeneration belongs, and what must not be hand-edited.
- For mixed-language repositories, place `AGENTS.md` files at workflow
  boundaries rather than forcing one language's conventions onto another.
- If a subtree contains both generated and handwritten code, place guidance at
  the smallest path that can explain the safe extension path clearly.

### 6. Write for uplift, not clutter

- State assumptions explicitly when the repo does not prove them.
- Prefer commands contributors can run immediately.
- Prefer verification steps over abstract advice.
- Preserve the repository's actual behavior unless the user asked to change it.
- Update conflicting existing `AGENTS.md` text with evidence instead of layering
  contradictory instructions.
- Keep files short. Move shared content upward instead of duplicating it.

### 7. Verify the result

- Check that every major working surface has a nearest applicable
  `AGENTS.md`.
- Check that no child duplicates parent text unless it narrows or overrides it.
- Check that every generated boundary has an explicit edit policy.
- Check that every major build or test surface has local verification steps.
- Check that every listed command or manifest actually exists.
- If the repo is large, verify the highest-value boundaries first and note what
  remains intentionally unsplit.

## Decision Rules

- Prefer fewer files with clear scope over many shallow files.
- Prefer boundaries that align with how contributors build, test, and verify
  work.
- Prefer source-of-truth boundaries over storage accidents.
- Prefer evidence from manifests, tasks, CI, and codegen config over directory
  names.
- If sibling trees share the same commands and constraints, keep them under a
  shared ancestor.
- If a subtree differs only by package layout or naming, do not split it.

## Delivery

If the user asked for a plan first, present the boundary map before editing.
Otherwise, implement directly after enough evidence is gathered. Always close
with:

1. The `AGENTS.md` files created or updated.
2. The rationale for each boundary.
3. The verification commands run, or the evidence used when commands were not
   run.
