# Schema-Driven Design Standard

## Scope

This standard applies to boundary contracts, serialized data, configuration,
messages, persisted records, tool inputs, API payloads, and any intermediate
representation that crosses a module, process, storage, or human-authored file
boundary.

Use this as the companion to `instructions/type-safety.md`: types are the authority inside the
program; schemas are the authority at the boundary. The goal is the same in both
places: move as much certainty as possible into statically inspectable structure
so illegal states cannot be represented.

---

## Principle 1: Boundary assertions are the first authority

Every external value enters through an explicit boundary assertion. The assertion
must define what is accepted, what is rejected, and what typed value is produced.

Do not treat parser code, prose, examples, fixture names, downstream conditionals,
or "known producer behavior" as the contract. The schema or boundary type is the
contract; all other code conforms to it.

**Rule:** Before a value becomes domain data, it must pass through a named schema,
parser, constructor, or factory whose accepted shape is statically inspectable.

---

## Principle 2: Assert aggressively at the edge

Boundary assertions should be narrow by default:

- require all fields that the domain requires;
- reject unknown fields unless extension is intentional;
- use finite variants for finite concepts;
- encode string formats, numeric bounds, collection cardinality, and nullability;
- distinguish absence from emptiness, emptiness from blankness, and blankness from
  invalid syntax when the domain cares.

Weak schemas create downstream uncertainty. A permissive boundary is a promise
that every consumer must re-check the same facts later.

**Rule:** If downstream code needs to ask whether a boundary value is shaped
correctly, the boundary assertion is incomplete.

---

## Principle 3: Make illegal states unrepresentable in data

Use schema structure to eliminate invalid states before runtime logic sees them.
For finite families, model each legal variant as a closed object and compose the
family with a discriminated union. For primitive domains, wrap raw values in
named constrained definitions instead of passing bare strings, numbers, booleans,
or maps.

```json
{
  "$defs": {
    "TenantId": {
      "type": "string",
      "minLength": 1,
      "pattern": "^[a-z0-9][a-z0-9-]*$"
    },
    "InvoiceId": {
      "type": "string",
      "minLength": 1
    }
  },
  "oneOf": [
    {
      "type": "object",
      "properties": {
        "type": { "type": "string", "enum": ["INVOICE_CREATED"] },
        "tenantId": { "$ref": "#/$defs/TenantId" },
        "invoiceId": { "$ref": "#/$defs/InvoiceId" }
      },
      "required": ["type", "tenantId", "invoiceId"],
      "additionalProperties": false
    },
    {
      "type": "object",
      "properties": {
        "type": { "type": "string", "enum": ["INVOICE_CANCELLED"] },
        "tenantId": { "$ref": "#/$defs/TenantId" },
        "invoiceId": { "$ref": "#/$defs/InvoiceId" },
        "reason": { "type": "string", "minLength": 1 }
      },
      "required": ["type", "tenantId", "invoiceId", "reason"],
      "additionalProperties": false
    }
  ]
}
```

**Rule:** A schema that permits a meaningless combination is not a source of
truth; it is an untyped transport envelope.

---

## Principle 4: Static analysis should see the model

Prefer data structures that tools can inspect over imperative checks hidden in
handlers. A reviewer, generator, linter, migration tool, or test harness should
be able to answer core questions from the model:

- Which variants exist?
- Which fields are required?
- Which fields are nullable?
- Which values are bounded, formatted, or enumerated?
- Which objects are closed?
- Which schema owns each reusable domain concept?
- Which changes are compatible or breaking?

Runtime validation is necessary, but it is not enough. The contract must also be
analyzable without executing business logic.

**Rule:** If a meaningful invariant exists only inside an `if` statement in a
handler, move it into a schema, type, constructor, or generated contract artifact.

---

## Principle 5: Parse into typed structure, never validated blobs

Validation must produce a stronger representation. After a boundary assertion,
the program should hold domain values, sealed variants, branded primitives, value
classes, generated typed records, or an equivalent language-native model.

Avoid designs where validation returns the same untyped JSON object, dictionary,
or string-keyed map and trusts later code to remember what was proven.

**Rule:** The result of boundary validation should make repeated validation
unnecessary and make invalid states difficult or impossible to express.

---

## Principle 6: The schema owns boundary metadata

Descriptions, examples, defaults, deprecation status, discriminator values,
formats, and compatibility notes belong with the schema or the generated type.
Do not keep a parallel catalog connected by string keys unless registration is
enforced at the definition site.

**Rule:** If adding or renaming a field requires editing a schema, parser,
documentation table, fixture registry, and handler by hand, the model is too
detached. Pull the metadata back to the authoritative boundary definition.

---

## Principle 7: Evolution is part of the model

A schema-driven system must make change shape visible:

- adding an optional field is usually compatible;
- adding a union variant is compatible only when consumers have exhaustive or
  fallback behavior by contract;
- changing a discriminator value is breaking;
- making an optional field required is breaking;
- widening or narrowing a constraint is a domain decision, not a formatting edit;
- changing meaning without changing identity is breaking even when validation
  still passes.

**Rule:** Version, migrate, or create a new variant when the meaning changes. Do
not preserve a schema name while silently changing the state space it represents.

---

## Anti-patterns to reject

| # | Anti-pattern | Fix |
|---|---|---|
| 1 | Permissive object schemas with unbounded `additionalProperties` | Close the object or model a typed extension point |
| 2 | `type: string` for domain identifiers, statuses, or modes | Define named constrained primitives or enums |
| 3 | `Map<String, Any>` / dictionary payloads after validation | Parse into generated or hand-authored typed structures |
| 4 | Prose-only invariants | Move the invariant into schema, constructor, or type structure |
| 5 | One schema that accepts every lifecycle state | Split legal states into variants with required fields per state |
| 6 | Fixture-derived schemas left as final contracts | Replace incidental sample shape with explicit domain constraints |
| 7 | Runtime-only assertions in handlers | Promote the assertion to the boundary model |

---

## Self-audit checklist

Before accepting a schema or boundary model, verify:

- [ ] Can the schema represent a state the domain says is impossible?
- [ ] Are all required domain facts required by the boundary assertion?
- [ ] Are all finite concepts modeled as enums or discriminated variants?
- [ ] Are objects closed unless extension is intentional and documented?
- [ ] Are raw primitives replaced with named constrained definitions where misuse
      is possible?
- [ ] Does validation produce a typed representation instead of a validated blob?
- [ ] Can static tools inspect the variants, required fields, nullability, and
      compatibility impact?
- [ ] Do invalid fixtures exist for missing required fields, wrong discriminators,
      extra properties, malformed primitives, and impossible state combinations?
