# Plugin Families

The canonical personal plugin list is authored in
`amichne/slopsentral/source/adaptable.marketplace.json`.

A projection reads each marketplace plugin entry, resolves its
`source/plugins/<name>/plugin.json`, hydrates the composed primitives, and
writes one target-native plugin directory. Plugin availability is therefore a
property of the source marketplace, not a separate Intelligence registry.

Use `slopsentral` for reusable workflow authoring and this repository for
projector or schema changes.
