---
name: "repository-onboarding"
description: "Set up a repository for marketplace-driven AI tooling. Use when onboarding a new repo, refreshing a checked-in marketplace reference, installing core marketplace plugins, or documenting which configured marketplaces should be available by default."
---

# Repository Onboarding

Use this skill to make a repository ready to consume skills, principles, hooks,
and plugins from remote marketplaces without vendoring installed plugin payloads
or local cache copies.

## Operating Contract

- Prefer remote marketplace sources over copying plugin payloads into the repo.
- Default to every marketplace already configured in the active runtime unless
  the user or repository policy narrows the set.
- Keep setup evidence in a checked-in reference file. Use
  `.agents/marketplaces.md` unless the repo already has a documented equivalent.
- Treat installed plugin directories and local caches as observations, not
  source.
- Keep private cleanup and migration utilities out of newly onboarded repo
  references.
- If the repo needs JSON, TOML, YAML, or another structured setup file, name the
  owning schema, parser, generator, or validation command before committing it.

## Workflow

1. Read repository policy.
   Inspect root instructions, existing agent-tooling setup, docs, plugin
   manifests, and hook configuration before adding setup files.

2. Discover configured marketplaces.
   Use runtime configuration, marketplace list commands, or existing checked-in
   setup docs. Record each marketplace source, provider entrypoint, branch or
   ref when available, and whether the source is remote or local-only.

3. Choose the default setup scope.
   Include the full configured marketplace set by default. Narrow the set only
   when the user, repo instructions, or security policy gives a concrete reason.

4. Write the checked-in reference.
   Create or update the repo's marketplace reference with the configured
   marketplaces, expected core plugins, local repo notes, refresh commands, and
   validation steps. Use
   [checked-in-marketplace-reference.md](references/checked-in-marketplace-reference.md)
   for the default structure.

5. Apply runtime setup.
   Install or enable plugins through the runtime marketplace mechanism. Do not
   copy generated marketplace payloads, installed plugin folders, or cache
   directories into source.

6. Verify.
   Confirm the reference file is tracked, remote marketplace sources resolve,
   expected plugins are installed or documented as pending, and no local cache
   paths became source-of-truth references.

## Completion Criteria

- The repo has a checked-in marketplace reference.
- The reference covers every configured marketplace unless an exclusion is
  explicitly justified.
- Core plugins and hooks are enabled from marketplace sources, not vendored
  payloads.
- Structured setup data has an owning schema, parser, generator, or validation
  command.
- Private cleanup and migration utilities are absent.
