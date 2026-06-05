# Boundary Heuristics

Use this reference to decide whether a subtree needs its own instruction file.

## Strong Split Signals

- Distinct build, test, lint, codegen, docs, release, or deployment commands.
- Distinct language, framework, package manager, or runtime conventions.
- Generated code or generated documentation that should not be hand-edited.
- Contract sources that generate clients, servers, docs, schemas, or examples.
- Plugin, hook, skill, agent, or marketplace trees with local APM-package
  rules.
- Infrastructure directories with plan/apply or policy validation loops.
- Templates or scaffolds where source templates and generated instances have
  different edit policies.
- High-blast-radius directories with stricter validation or approval gates.

## Weak Split Signals

- Folder depth with no command or policy difference.
- Package layout inside one module when the same commands and edit rules apply.
- Empty directories.
- Cosmetic naming differences.
- Symmetry without evidence that instructions differ.

## Parent And Child Rules

- Put shared rules at the highest ancestor that owns them.
- Put local deltas at the nearest subtree that needs them.
- Do not create both a parent and child file when the child only repeats the
  parent.
- Add a child when a contributor working only inside that subtree would need
  different commands, constraints, or edit policies.

## Common Repo Shapes

For Gradle or Maven repos, anchor on root build files, included builds,
convention plugins, generated sources, and module clusters.

For JavaScript or TypeScript workspaces, anchor on workspace manifests,
deployable apps, shared packages, and tooling roots.

For contract repos, separate source contracts from generated clients, stubs,
documentation, and handwritten adapters.

For plugin/APM-package repos, separate primitive roots from composed plugin
manifests and generated marketplace artifacts.
