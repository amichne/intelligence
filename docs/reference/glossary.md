# Glossary

| Term | Meaning |
|---|---|
| Marketplace | An adaptable plugin catalog, authored in `source/adaptable.marketplace.json` for publisher repos or recorded in `.intelligence/adaptable.marketplace.json` for install-only consumers. |
| Plugin | An installable workflow family defined by a marketplace repository, such as `source/plugins/<name>/plugin.json` in `slopsentral`. |
| Primitive | A skill, agent, instruction, prompt, concept, or hook composed into plugins. |
| Skill | Agent-facing workflow directory with `SKILL.md`. |
| Agent | Delegated persona or specialist profile owned by a marketplace source repository. |
| Instruction | Long-lived behavior guidance owned by a marketplace source repository or plugin-composed instruction entries. |
| Hook | Runtime callback metadata, adapter JSON, and executable support files owned by a marketplace source repository. |
| Generated output | Provider marketplace JSON produced by the Kotlin CLI. |
