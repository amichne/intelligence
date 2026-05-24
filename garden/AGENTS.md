# Garden Instructions

## Scope

This file applies to `garden/`.

## Rules

- Treat this directory as the isolated spring-cleaning workspace for source
  inventory, consolidation, duplicate review, runtime activation planning, and
  cleanup evidence.
- Keep garden scripts, manifests, schemas, and generated reports internally
  consistent; do not make public plugin or marketplace consumers depend on
  garden paths.
- Run `python3 garden/scripts/check-source-graph.py --refresh` after changing
  garden generators or generated reports.
- Run `python3 garden/scripts/check-source-graph.py` before using garden
  evidence to justify cleanup, runtime activation, or source-root retirement.
- Run `node scripts/validate-manifests.mjs` after changing garden JSON schemas
  or manifests.
