# Type-Driven Design Standard

## Scope

This standard applies to generated and hand-authored code. The principles are
language-agnostic: use the strongest local type-system construct available.

For schemas, serialized data, APIs, messages, persisted records, configuration
contracts, and other boundary assertions, preserve the same rule with a named
schema, parser, generated model, or equivalent boundary assertion. Types are the
authority inside the program; boundary assertions are the authority at the edge.

Language-specific examples live beside this file. Kotlin representations are in
`kotlin/`.

## Quick Use

1. Identify the domain concept, not the transport shape.
2. Decide whether the concept has finite variants, constrained primitive values,
   lifecycle states, or attached metadata.
3. Encode those facts in types, constructors, factories, or parsers before
   behavior uses the value.
4. Check that adding a variant or changing an invariant fails loudly at compile
   time or at the boundary assertion.

## Reference Map

- `kotlin/payment-method.kt`: finite variants and exhaustive behavior.
- `kotlin/value-construction.kt`: constructor and factory validation.
- `kotlin/composite-encoding.kt`: one owner for encoding rules.
- `kotlin/config-value.kt`: typed dispatch over primitive tags.
- `kotlin/command-metadata.kt`: metadata attached to variants.
- `kotlin/email-display.kt`: explicit wrapper display representation.

## Conflict Handling

If this standard conflicts with local repo instructions, generated schemas,
compiler limits, or runtime behavior, do not weaken the type model silently.
State the conflict, keep the boundary assertion intact, and choose the narrowest
local exception that preserves invalid-state prevention.

---

## Principle 1: Make illegal states unrepresentable

Enumerate every legal shape of a finite concept with the type system. Every
consumer must handle every variant through compiler-checked exhaustiveness or an
equivalent static check.

Never expose a raw primitive where a named type with constrained construction
would prevent misuse.

**Rule:** If a concept has a finite set of shapes, model it as a sum type. Do not
model it as a string tag plus an untyped payload.

---

## Principle 2: Validate at construction, not at use

An invalid instance must never exist. Push invariant checks into constructors,
factories, parsers, or equivalent creation boundaries. Downstream code should
never need to re-validate a value that already has a domain type.

Layer validation so each level adds one clear invariant and delegates to the
previous level.

**Rule:** The type's constructor or factory is the gatekeeper of validity. If a
caller can create a live invalid instance, the design is wrong.

---

## Principle 3: Give every invariant one owner

Encoding formats, separators, defaults, lifecycle rules, and structural
constraints need exactly one definition site. All consumers delegate to that
site.

**Rule:** If changing a rule requires edits in several disconnected places, and
static tooling does not force every edit, extract the rule into a single owner.

---

## Principle 4: Map domain shapes to behavior with typed variants

Dispatch on typed variants rather than strings, integers, maps, or conventions.
The dispatch mechanism should be the type system, not a shared memory of
possible tag values.

**Rule:** If a branch chooses behavior by inspecting a string or integer tag,
replace the tag with a typed variant that carries its own data.

---

## Principle 5: Keep metadata attached to the type

Descriptions, defaults, display names, parser metadata, help text, and command
paths belong to the type or to a registration point enforced at the type
definition site. Do not keep a parallel catalog joined by string keys.

**Rule:** If adding a new variant requires synchronized edits in disconnected
files with no static enforcement, the metadata is detached.

---

## Principle 6: Define explicit display representation

Wrapper types that participate in logging, serialization, string interpolation,
or user-facing output must define their display representation explicitly. The
debug representation is not the domain representation.

**Rule:** A wrapper used outside a debugger must have a deliberate
string/display implementation.

---

## Anti-patterns to reject

| # | Anti-pattern | Fix |
|---|---|---|
| 1 | Parallel metadata structures joined by string keys | Attach metadata to the type or enforce registration at the definition site |
| 2 | Bag-of-strings intermediate representations | Define typed intermediates with named, validated fields |
| 3 | Scattered defaults | Keep one definition site and derive every other use |
| 4 | Flat variant hierarchies with hidden shared shapes | Extract shared interfaces or subtypes |
| 5 | Validation at use sites | Move the check into the constructor, parser, or factory |
| 6 | String-based dispatch | Replace the tag with a typed variant |

---

## Self-Audit

Before accepting code, verify:

- Can any caller construct an invalid instance?
- Does adding a new variant force every required handler to update?
- Is any string literal used as a domain lookup key in more than one place?
- Does any branch dispatch on a primitive tag that could be a type?
- Are defaults and encoding rules defined exactly once?
- Does every externally visible wrapper define its display representation?
