# amichne-apm

`amichne-apm` is an APM-native marketplace for reusable AI tooling packages.
Each package owns the primitives it ships in `.apm/`; the root `apm.yml` owns
marketplace exposure.

```mermaid
flowchart LR
  primitive[Package primitives in .apm/]
  package[packages/*/apm.yml]
  marketplace[root apm.yml marketplace]
  output[APM marketplace JSON]
  runtime[Consumer runtimes]

  primitive --> package --> marketplace --> output --> runtime
```

## Start Here

Use APM directly.

```sh
apm pack --marketplace=all --dry-run --check-versions --json
apm audit --ci --no-policy
```

Build the docs site when documentation changes.

```sh
zensical build --clean
```

## What You Can Do

| Job | Entry Point | Result |
|---|---|---|
| Inspect packages | [What is available](available/index.md) | A map of package families and primitives. |
| Consume the marketplace | [Marketplace](getting-started/marketplace.md) | APM marketplace installation commands. |
| Author a primitive | [Author a primitive](getting-started/author-a-primitive.md) | A package-owned `.apm/` primitive. |
| Validate publishing | [Validation](how-it-works/validation.md) | APM checks before release. |
