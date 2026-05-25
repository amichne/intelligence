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
| Workflow profile | Target-repository contract selecting marketplaces, plugins, hooks, runtime links, and validation commands. |
| Source graph | The authored and generated map of primitives, provenance, plugin coverage, review decisions, and runtime activation plans. |
| Garden | Repository area for inventory, generated evidence, review ledgers, scripts, and local schemas. |
| Runtime activation | Approval-gated projection of canonical primitives into runtime paths. |
| Hydrated marketplace | Generated provider-native marketplace output built from the provider-neutral source catalog. |
