# Glossary

The repository uses a small set of terms consistently. Use these meanings when
editing docs, manifests, or primitives.

| Term | Meaning |
|---|---|
| Primitive | Independent building block that can compose a plugin: skill, agent, hook, instruction, or concept. |
| Skill | Agent-facing workflow instructions rooted at `skills/<name>/SKILL.md`. |
| Agent profile | Focused reviewer or operator profile under `agents/`. |
| Hook | Provider-neutral hook metadata plus implementation and adapter configs. |
| Concept | Portable instruction or principle document under `concepts/`. |
| Plugin | Referential composition manifest that assembles existing primitives. |
| Marketplace | Curated distribution catalog for generally useful plugin families and primitives. |
| Adapter projection | Generated provider-native output derived from source primitives, such as a plugin `AGENTS.md` file for runtimes without native agent or instruction support. |
| Workflow profile | Target-repository contract selecting marketplaces, plugins, hooks, and validation commands. |
| Source graph | The authored map of primitives, plugin composition, schemas, and marketplace exposure. |
| Hydrated marketplace | Generated provider-native marketplace output built from the provider-neutral source catalog. |
