---
name: repo-instruction-topology
description: Map a repository's working boundaries and create or revise scoped agent instruction files such as AGENTS.md. Use when introducing repo guidance, splitting a monolithic instruction file, documenting generated/manual edit boundaries, or aligning local instructions with build, test, codegen, docs, plugin, hook, schema, or deployment surfaces.
---

# Repo Instruction Topology

Use this skill when repository instructions need to match the actual shape of a
codebase. The output is a small hierarchy of scoped instruction files, not a
directory-by-directory commentary layer.

## Operating Contract

- Discover the repo's real working boundaries before editing instructions.
- Keep repo-wide rules at the highest useful ancestor.
- Add child instruction files only when local commands, generated boundaries,
  ownership, language conventions, or validation loops materially differ.
- Keep child files incremental; do not restate parent guidance unless narrowing
  or overriding it.
- Make every instruction concrete enough to verify from files, manifests,
  generated outputs, command help, or runnable checks.
- Preserve the repository's existing instruction format. In this repo, that is
  usually `AGENTS.md`.

## Workflow

1. Inventory the repository.
   Inspect top-level orchestration files, package/workspace manifests, build
   configs, codegen configs, CI workflows, docs configs, plugin manifests,
   hook configs, schema roots, and deployment surfaces.

2. Map working zones.
   Identify source roots, generated outputs, docs, tests, infra, plugins,
   hooks, schemas, templates, and tooling directories. Record the command or
   policy evidence that makes each zone distinct.

3. Choose instruction boundaries.
   Create or update the root instruction file for global rules. Add child files
   only where local deltas change how an agent should edit, validate, or avoid
   generated surfaces.

4. Draft scoped guidance.
   State scope, work commands, edit rules, source-of-truth inputs, generated
   boundaries, and verification steps. Keep the file short and actionable.

5. Verify the topology.
   Check that major working zones have a nearest applicable instruction file,
   no child file duplicates its parent, listed commands or manifests exist, and
   generated/manual boundaries are explicit.

## Reference Routing

- Load [boundary-heuristics.md](references/boundary-heuristics.md) before
  deciding where instruction files should live.
- Load [instruction-file-contract.md](references/instruction-file-contract.md)
  before drafting or reviewing an instruction file.
- Load [source-of-truth-map.md](references/source-of-truth-map.md) when the
  repository has generated outputs, plugin/source graph manifests, docs sites,
  schemas, or symlinked runtime surfaces.

## Completion Criteria

- The boundary map names each proposed instruction file and why it exists.
- Every created or changed instruction file has a clear scope and local delta.
- Generated/manual edit boundaries are explicit.
- Verification commands are listed and run when practical.
- Any intentionally unsplit area is documented with evidence.
