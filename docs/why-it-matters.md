# Why It Matters

Agent tooling tends to spread: a skill in one runtime, a hook in another, a
review note in a repository, and a half-updated marketplace payload somewhere
else. That makes it hard to know which copy is current, what is safe to install,
and what can be deleted.

Intelligence addresses that problem by treating reusable AI behavior as source
code with a source graph.

## The Repository Contract

The core contract is conservative: inventory first, promote after review, and
do not replace or delete scattered originals until the replacement and rollback
path are recorded.

| Problem | Repository Answer |
|---|---|
| Scattered skills and instructions | Canonical primitives under `skills/`, `agents/`, `hooks/`, and `concepts/`. |
| Plugin payloads becoming the only copy | Plugins compose primitives by reference instead of owning them. |
| Unclear marketplace surface | `marketplace.json` curates generally useful, project-agnostic plugin families. |
| Runtime mutation risk | Runtime activation is modeled as a dry-run plan with approval packets. |
| Structured data drift | JSON manifests are covered by schemas and `node scripts/validate-manifests.mjs`. |
| Stale cleanup decisions | `garden/manifests/*` records generated evidence and durable review decisions. |

## When This Helps

Use this repository when repeated agent behavior should become durable:
repository onboarding, Kotlin review, schema governance, documentation
workflows, TDD loops, GitHub CI triage, or runtime hook wiring.

It also helps when you need to explain why a primitive is available, why a
plugin exposes it, what validation protects it, and what evidence supports a
cleanup or activation decision.

## When To Be Careful

This is a source-of-truth repository, not a dumping ground. New primitives
should have a clear reusable job, a schema or parser boundary when they persist
structured data, and a validation path that future agents can run.

!!! note "Marketplace boundary"
    The public marketplace surface is intentionally curated to generally
    useful, project-agnostic primitives and plugin families. Repo-local
    cleanup, runtime-linking, and source-graph maintenance utilities stay local
    unless explicitly published.
