# Runtime Targets

Use this reference when choosing an activation strategy.

## Preferred Order

1. Marketplace import.
   Use this when a runtime can install referential plugins from an APM
   marketplace release artifact.

2. Child symlinks.
   Use this when a runtime expects individual skill, agent, or hook files under
   an existing root.

3. File symlink.
   Use this for single adapter configs or single agent files.

4. Tree symlink.
   Use this only when the whole target directory is known to be runtime-owned
   and not a mixed user/vendor directory.

## Known Local Targets

- Marketplace import should use the APM release artifact, not
  `source/adaptable.marketplace.json` on `main`.
- Codex and Agents skill folders may already be symlinked or managed by another
  local repo; inspect before writing.
- Claude agent folders may contain broken historical symlinks; cleanup-ledger
  review should precede mutation.
- Provider-specific hook adapters should be exposed separately from
  provider-neutral hook metadata.

## Rule

Edit canonical primitives here. Treat runtime paths as projections whose only
job is to make the canonical source available to a host.
