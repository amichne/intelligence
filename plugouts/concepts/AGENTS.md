# Concept Plugout Instructions

## Scope

This file applies to every file in this directory.

This directory is a portable concept inventory. Each concept lives in its own
folder and can be copied, installed, linked, or quoted into other projects
without importing this source repository, its tooling, or its history.

`AGENTS.md` is part of that inventory and must obey the same rules it imposes.

## Non-Negotiable Standard

Every concept folder in this directory must be fully atomic and 100% portable.

- Atomic means the folder explains one concept completely enough to stand alone.
- Portable means the folder does not depend on absolute paths, local machines,
  repository-private tools, hidden memory, organization-specific process, or
  sibling files to be understood.

Do not accept a change here if a concept folder needs context outside itself or
mandatory companion reading from another concept before it can be used.

## Folder Contract

- `core.md` is the normative, Markdown-only rule body for the concept.
- Language or format directories such as `kotlin/`, `json/`, `typescript/`, or
  `rust/` contain source-code representations of the principle.
- Source blobs must be named by subconcept, not by vague example numbers.
- Top-level concept Markdown files are compatibility links only. Do not add new
  concept prose at the root of this directory.

## Agent Reference Contract

Organize every `core.md` as a reference source an agent can use without hidden
context:

- Start with `Scope`: what the concept applies to and what authority it has.
- Add `Quick Use`: the shortest decision procedure for applying the concept.
- Keep the normative rules in stable sections with explicit names.
- Add `Reference Map`: local source blobs and what each one demonstrates.
- Add `Conflict Handling`: what to do when this concept conflicts with local
  repo instructions, generated contracts, runtime behavior, or language limits.
- Add `Anti-patterns` and `Self-Audit` sections for fast verification.
- Avoid speculation. If the concept does not decide a case, say which narrower
  evidence or local rule should decide it.

This mirrors the useful parts of the local `openai-docs` skill: identify the
source of truth, fetch only the relevant section, keep guidance narrow, and call
out conflicts instead of guessing.

## Concept Contract

- Keep one concept per folder.
- Give each concept a stable title, explicit scope, concrete rules, examples,
  anti-patterns, and a self-audit or acceptance checklist when useful.
- Define required terms inline before relying on them.
- Keep source-code examples out of `core.md`; link to the relevant language or
  format file instead.
- Use companion links only as optional related material. Do not make another
  concept mandatory reading for understanding the current concept.
- If two concepts must always be read together, merge them or repeat the shared
  premise in each `core.md`.
- If a downstream project needs narrower policy, write that adapter outside this
  directory. Do not bake one project's constraints into the shared concept.

## Portability Rules

- Use relative references only when referring to files in this same concept
  folder or to sibling concept folders.
- Do not use absolute paths, local file URLs, machine-specific home directories,
  IDE state, environment-specific credentials, or uncommitted local setup.
- Do not require a package manager, language server, plugin, CI system, editor,
  or hosted service unless the concept is explicitly about that tool.
- Keep examples language-agnostic, or provide parallel idioms when a single
  language example could make the concept look language-specific.
- Treat schemas, serialized examples, commands, and source blobs as
  illustrative unless `core.md` explicitly declares them normative.
- Avoid importing repo names, project codenames, or implementation details
  unless they are part of the concept itself.

## Authoring Rules

- Prefer short declarative rules over long rationale.
- State normative requirements with `must`. Use `should` only when a real
  exception exists.
- Separate universal principle from local execution. The principle belongs here;
  project-specific commands belong in the consuming project.
- Put enforcement at boundaries: types, schemas, generated contracts,
  constructors, tests, linters, or review checklists.
- Remove duplication instead of cross-referencing it. If a rule appears in
  multiple files, decide whether it is a shared premise or two distinct
  applications.

## Edit Rules

- Edit source concept files directly; no generated files are currently declared
  in this directory.
- When adding a concept folder, make it independently copyable before linking it
  from anywhere else.
- When renaming a concept, update same-directory references in the same change.
- When weakening a rule, state the new boundary and what enforcement remains.

## Verify

Before finishing a change in this directory:

- Read each changed `core.md` by itself. It must make sense with no outside
  context.
- Confirm changed `core.md` files do not contain fenced source-code blocks; move
  source representations into a language or format subdirectory.
- Check that every required concept, term, and invariant is defined in the
  concept folder that uses it.
- Check that cross-file references are optional, local, and relative.
- Search for non-portable references such as absolute paths, local file URLs,
  local usernames, hidden tooling, and source-repository assumptions.
- Confirm that each changed folder still expresses one concept, not a bundle of
  unrelated guidance.
