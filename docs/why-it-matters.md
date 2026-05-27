# Why It Matters

Agent tooling tends to spread: a skill in one runtime, a hook in another, a
review note in a repository, and a half-updated marketplace payload somewhere
else. That makes it hard to know which copy is current, what is safe to install,
and what can be deleted.

Intelligence addresses that problem by treating reusable AI behavior as source
code with explicit composition and validation.

## The Repository Contract

The core contract is conservative: primitives stay independent, plugins compose
by reference, and publication is limited to project-agnostic behavior.

| Problem | Repository Answer |
|---|---|
| Scattered skills and instructions | Canonical primitives under `source/skills/`, `source/agents/`, `source/hooks/`, and `source/concepts/`. |
| Plugin payloads becoming the only copy | Plugins compose primitives by reference instead of owning them. |
| Unclear marketplace surface | `source/adaptable.marketplace.json` curates generally useful, project-agnostic plugin families. |
| Structured data drift | JSON manifests are covered by schemas and `node scripts/validate-manifests.mjs`. |

## When This Helps

Use this repository when repeated agent behavior should become durable:
repository onboarding, Kotlin review, schema governance, documentation
workflows, TDD loops, GitHub CI triage, or hook wiring.

It also helps when you need to explain why a primitive is available, why a
plugin exposes it, and what validation protects it.

## When To Be Careful

This is a source-of-truth repository, not a dumping ground. New primitives
should have a clear reusable job, a schema or parser boundary when they persist
structured data, and a validation path that future agents can run.

!!! note "Marketplace boundary"
    The public marketplace surface is intentionally curated to generally
    useful, project-agnostic primitives and plugin families. Private cleanup
    and migration material is not part of this public repository.
