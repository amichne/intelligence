# Glossary

The repository uses a small set of terms consistently. Use these meanings when
editing docs, manifests, or primitives.

| Term | Meaning |
|---|---|
| Primitive | Independent building block that can compose a plugin: skill, agent, hook, instruction, or concept. |
| Skill | Agent-facing workflow instructions rooted at `source/skills/<name>/SKILL.md`. |
| Agent profile | Focused reviewer or operator profile under `source/agents/`. |
| Hook | Provider-neutral hook metadata plus implementation and adapter configs. |
| Concept | Portable instruction or principle document under `concepts/`. |
| Plugin | Referential composition manifest that assembles existing primitives. |
| Marketplace | Curated distribution catalog for generally useful plugin families and primitives. |
| Workflow profile | Target-repository contract selecting marketplaces, plugins, hooks, and validation commands. |
| Source graph | The authored map of primitives, plugin composition, schemas, and marketplace exposure. |
| Hydrated marketplace | Generated provider-native marketplace output built from the provider-neutral source catalog. |
| Adapter projection | Deterministic transformation from a validated `source/` model to a provider-native output surface. |
| Source model | The schema-governed graph in `source/` that is the shared authority for skills, agents, hooks, concepts, plugins, and marketplace curation. |
