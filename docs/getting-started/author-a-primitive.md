# Author A Primitive

A primitive is an independent building block that can be useful without a
plugin. Skills, agents, hooks, instructions, and concept documents are
primitives in this repository.

## Scaffold

Primitive scaffolding is being moved into the Kotlin CLI. Until that command is
ported, create the primitive under `source/` from the local templates and add
plugin or marketplace references deliberately.

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
./gradlew installDevelopmentCli
.local/intelligence/bin/intelligence validate
```

When a primitive comes from another source, keep provenance in the primitive or
plugin documentation that is safe for the public repository.
