# Kotlin Horizontalization Heuristic

Use this at the end of a Kotlin turn to decide whether package/file layout is
becoming too flat. The heuristic is a review gate, not a substitute for
semantic judgment.

## Terms

- **Package directory**: a directory under `src/main/kotlin`,
  `src/test/kotlin`, or an equivalent Kotlin source root.
- **Direct Kotlin file count**: `*.kt` files immediately in a directory, not
  recursive descendants.
- **Primary top-level member**: a top-level class, interface, object, enum,
  value class, data class, abstract class, or sealed root.
- **Prefix cluster**: three or more peer files whose file names or primary type
  names share the same leading PascalCase token, such as `StandaloneRunner`,
  `StandaloneEnvironment`, and `StandalonePlan`.

## Directory Limits

Treat package depth as a visible API design choice.

| Condition | Result |
| --- | --- |
| 1 to 3 direct Kotlin files | Usually pass |
| 4 to 5 direct Kotlin files | Pass only when the package owns one semantic unit |
| 6 to 7 direct Kotlin files | Concern; require a named reason to stay flat |
| 8 or more direct Kotlin files | Fail unless generated or compatibility-owned |
| Module or feature root with more than 5 direct Kotlin files | Fail unless documented |

The root rule is stricter because root files become the visible vocabulary of a
module. A root should expose only the few concepts a reader must understand
first; implementation detail belongs under named subpackages.

## Prefix Cluster Rule

For each package directory:

1. Split direct Kotlin file stems and primary type names into PascalCase tokens.
2. Ignore generic tokens that only describe implementation roles:
   `Default`, `Base`, `Abstract`, `Internal`, `Impl`, `Test`, `Spec`.
3. Group files by their first meaningful token and first two meaningful tokens.
4. If a group has 3 or more files, mark it as an extraction candidate.
5. If the group also represents 50 percent or more of the directory, block the
   turn until the package shape is justified or changed.

Example:

```text
standalone/
  StandaloneAnalysisSession.kt
  StandaloneDeclarationScanner.kt
  StandaloneReferenceScanner.kt
  StandaloneIndexEnvironment.kt
  StandaloneLifecycle.kt
```

This should usually become:

```text
standalone/
  AnalysisSession.kt
  lifecycle/
    Lifecycle.kt
  scanning/
    DeclarationScanner.kt
    ReferenceScanner.kt
  index/
    Environment.kt
```

The desired result is not deeper nesting for its own sake. The desired result is
fewer redundant prefixes, smaller package vocabularies, and types whose names
make sense at the call site.

## File Ownership Rule

Default to one primary top-level member per file.

Allowed exceptions:

- A sealed root with its closed variants when they are one semantic unit.
- A small interface with library-owned implementations when callers experience
  them as one abstraction.
- A deliberately named package function file, such as `Parsing.kt`, when the
  functions are one vocabulary and no owning type would clarify the design.
- Generated files and compatibility shims when the reason is documented.

Flag a file when it contains multiple unrelated primary members, multiple
public owners, or a mix of domain model and transport/adapter code.

## Hook Decision

At turn end, inspect changed Kotlin files plus their package directories and
ancestors. Emit:

- `pass`: no measured concern.
- `concern`: direct file count is 6 to 7, a prefix cluster exists, or a file has
  multiple primary members with a plausible exception.
- `fail`: root file count exceeds 5, direct file count is 8 or more, a prefix
  cluster owns at least half the directory, or a file has unrelated primary
  public members.

The hook should fail only on `fail`. It should print concerns so the reviewing
agent can decide whether to reorganize now or record a deliberate exception.

