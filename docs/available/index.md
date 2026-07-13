# What Is Available

V1 discovers immutable snapshots and exposes whole packages. A candidate is
untrusted until exact inspection verifies its release evidence.

```sh
intelligence marketplace discover --github amichne/slopsentral
intelligence marketplace inspect \
  --github amichne/slopsentral \
  --snapshot SNAPSHOT_ID \
  --format json
```

The inspection result lists package identities and digests. It does not expose
the package's supporting assets or offer per-skill selection.
