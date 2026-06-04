# Kotlin Correctness Plugin Instructions

## Scope

This generated adapter applies to the `kotlin-correctness` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.

## Runtime Boundary

The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.

## Plugin Intent

Kotlin type modeling, package cohesion, Gradle proof loops, and review agents for correctness-oriented code.

## Operating Rules

- Treat this file as an adapter, not a new source of truth.
- Use bundled skills for step-by-step workflows.
- Apply bundled instructions as normative guidance when their scope matches the task.
- Treat bundled agent profiles as review criteria or focused review passes.
- Keep hook behavior in bundled hook files and runtime adapter configs.
- When guidance conflicts with the target repository's nearest `AGENTS.md`, follow the target repository unless the user explicitly chooses this plugin's rule.

## Instruction Primitives

- `schema-driven-design`: `instructions/schema-driven-design.md` (source: `source/concepts/schema-driven-design/core.md`)
- `type-safety`: `instructions/type-safety.md` (source: `source/concepts/type-safety/core.md`)

## Agent Profile Primitives

- `kotlin-boundary-contract-reviewer`: `agents/kotlin-boundary-contract-reviewer.agent.md` (source: `source/agents/kotlin-review/kotlin-boundary-contract-reviewer.agent.md`)
- `kotlin-package-cohesion-reviewer`: `agents/kotlin-package-cohesion-reviewer.agent.md` (source: `source/agents/kotlin-review/kotlin-package-cohesion-reviewer.agent.md`)
- `kotlin-review-captain`: `agents/kotlin-review-captain.agent.md` (source: `source/agents/kotlin-review/kotlin-review-captain.agent.md`)
- `kotlin-type-safety-reviewer`: `agents/kotlin-type-safety-reviewer.agent.md` (source: `source/agents/kotlin-review/kotlin-type-safety-reviewer.agent.md`)
- `schema-type-enforcer`: `agents/schema-type-enforcer.agent.md` (source: `source/agents/schema-type-enforcer.agent.md`)

## Skill Primitives

- `kotlin-gradle-validation`: `skills/kotlin-gradle-validation` (source: `source/skills/kotlin-gradle-validation`)
- `kotlin-review`: `skills/kotlin-review` (source: `source/skills/kotlin-review`)
- `kotlin-standards`: `skills/kotlin-standards` (source: `source/skills/kotlin-standards`)

## Hook Primitives

- `gradle-check-green`: `hooks/gradle-check-green.hooks.json` (source: `source/hooks/gradle-check-green.hook.json`)
- `kotlin-horizontalization-check`: `hooks/kotlin-horizontalization-check.hooks.json` (source: `source/hooks/kotlin-horizontalization-check.hook.json`)
