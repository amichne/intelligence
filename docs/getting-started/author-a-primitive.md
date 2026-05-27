# Author A Primitive

A primitive is an independent building block that can be useful without a
plugin. Skills, agents, hooks, instructions, and concept documents are
primitives in this repository.

## Scaffold

Use the repository CLI to create a primitive from the local templates.

```sh
bin/intelligence primitive new skill example-skill \
  --plugin primitive-systems-authoring \
  --marketplace
```

The command can add the primitive to a referential plugin or marketplace entry
when that is part of the intended distribution path.

## Keep It Independent

A plugin should compose a primitive that already exists. It should not become
the only copy of that primitive's behavior.

Check the primitive against these questions before publishing it:

| Question | Why It Matters |
|---|---|
| What reader or agent job does it serve? | Prevents vague reusable content. |
| Can it stand without the plugin? | Preserves primitive independence. |
| Does it persist structured data? | Triggers schema or parser ownership. |
| What validates it? | Keeps future changes executable. |
| Should it be public? | Maintains the curated marketplace boundary. |

## Validate

Run the repository gate after changing primitives, plugin manifests, schemas,
hooks, marketplace entries, or workflow profiles.

```sh
bin/intelligence validate
```

When a primitive comes from another source, keep provenance in the primitive or
plugin documentation that is safe for the public repository.
