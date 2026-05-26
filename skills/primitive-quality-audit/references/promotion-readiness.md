# Promotion Readiness

Use the narrowest decision that the evidence supports.

## Decisions

- `PROMOTE_READY`: the primitive is independent, validated, provenance-backed,
  and ready to become or remain canonical.
- `SYNTHESIZE_FIRST`: useful behavior exists in multiple sources, but the
  canonical primitive should be rewritten as a local synthesis before promotion.
- `REWRITE_FIRST`: the source is valuable but too provider-specific, too broad,
  too coupled, or too close to first-party wording to promote as-is.
- `DEFER`: the primitive may matter later, but current evidence is not enough
  to justify promotion work.
- `ACTIVATE_READY`: the canonical primitive is ready to expose to a runtime, and
  the runtime-link plan records collision policy and approval requirements.
- `CLEANUP_READY`: the canonical replacement, activation path, verification,
  and rollback plan are all recorded, so cleanup can be proposed for explicit
  approval.
- `IGNORE`: the source is duplicate, obsolete, out of scope, or already covered
  by a better canonical primitive.

## Promotion Gate

Use `PROMOTE_READY` only when all of these are true:

- The canonical path exists under `skills/`, `agents/`, `hooks/`, or
  `concepts/`.
- The primitive has a clear owner, trigger, and success contract.
- Long details are routed through references or scripts instead of crowding the
  entry file.
- `garden/manifests/promotions.json` records source and supporting provenance.
- Any changed structured data validates through its schema path.
- First-party source handling is explicit when relevant.
- A referential plugin can compose the primitive without becoming its source of
  truth.

## Activation Gate

Use `ACTIVATE_READY` only when all of these are true:

- The primitive is already canonical or promotion-ready.
- `garden/manifests/runtime-links.json` records source path, target path, strategy,
  collision policy, and approval requirement.
- Existing target contents have been reviewed for collisions.
- The plan can be reversed or skipped without losing source data.

## Cleanup Gate

Use `CLEANUP_READY` only when all of these are true:

- Replacement behavior has been verified against the old source.
- `garden/manifests/cleanup-ledger.json` names the source path, canonical path,
  decision, verification, and rollback path.
- Duplicate-name and duplicate-digest evidence has been inspected.
- The user has explicitly asked to perform cleanup, not just plan it.
