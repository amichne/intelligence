# Package Content

Skills and their supporting files are authored in `amichne/slopsentral`, but
V1 does not expose standalone primitive selection. Every declared skill in a
selected package is included; scripts, instructions, and other supporting
assets remain private package content.

```sh
intelligence marketplace inspect \
  --github amichne/slopsentral \
  --snapshot SNAPSHOT_ID \
  --format json
```

Use this repository for CLI and schema changes. Use `slopsentral` for reusable
package source authoring.
