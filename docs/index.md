# Intelligence

Intelligence is a source graph and marketplace for reusable AI tooling
primitives: skills, agents, hooks, instruction concepts, workflow profiles,
schemas, and referential plugin families.

It exists to turn scattered local agent behavior into durable source. A
primitive remains useful on its own, a plugin composes primitives by reference,
and generated evidence records what is promoted, reviewed, published, or still
blocked.

```mermaid
flowchart LR
  primitive[Independent primitives]
  plugin[Referential plugins]
  marketplace[Curated marketplace]
  profile[Workflow profiles]
  runtime[Target runtimes]
  evidence[Garden evidence]
  schemas[Schema gates]

  primitive --> plugin --> marketplace --> profile --> runtime
  schemas --> primitive
  schemas --> plugin
  schemas --> marketplace
  primitive --> evidence
  evidence --> profile
```

## Start Here

Use the repository directly when you want to inspect, compose, publish, or
validate AI tooling. The fastest useful checks are local and executable.

```sh
npm ci
bin/intelligence validate
zensical build --clean
```

The first command installs pinned validation dependencies. The second runs the
repository's manifest and source graph gates. The third builds this
documentation site with Zensical.

## What You Can Do

The repository supports several related workflows.

| Job | Entry Point | Result |
|---|---|---|
| Understand the toolbox | [What is available](available/index.md) | A map of plugin families, primitives, hooks, and schemas. |
| Add Intelligence to another repo | [Workflow profiles](getting-started/workflow-profiles.md) | A checked-in profile and dry-run install path. |
| Import curated plugins | [Marketplace](getting-started/marketplace.md) | Provider-native marketplace payloads from the generated branch. |
| Create a new building block | [Author a primitive](getting-started/author-a-primitive.md) | A scaffolded skill, agent, hook, concept, or plugin reference. |
| Keep the source graph honest | [Validation](how-it-works/validation.md) | Schema-backed and generator-backed checks. |

## Why Care

This repository is useful when AI tooling has outgrown one-off prompts and
untracked local files. It gives those tools names, owners, schemas, review
evidence, and installation paths.

It is less useful if you only need a single prompt or a disposable local
experiment. The repository pays off when the same behavior should survive
across repositories, runtimes, machines, or release cycles.
