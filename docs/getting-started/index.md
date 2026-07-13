# Getting Started

Use `intelligence` as a non-interactive, exact-snapshot marketplace operator.
No command discovers a moving version or selects a primitive below a package.

## Common Tasks

| Task | Command |
|---|---|
| Check runtime and GitHub readiness | `intelligence doctor --format json` |
| Discover one repository | `intelligence marketplace discover --github OWNER/REPOSITORY` |
| Inspect one immutable snapshot | `intelligence marketplace inspect --github OWNER/REPOSITORY --snapshot SNAPSHOT_ID` |
| Set up from an exact local snapshot | `intelligence setup --local-snapshot snapshots/one --index-sha256 SHA256` |
| Select one whole package | `intelligence marketplace select MARKETPLACE_ID SOURCE --package NAME` |
| Reconstruct exact cached evidence | `intelligence marketplace reconstruct --offline` |
| Validate this repository | `intelligence validate --portable` |

All leaf commands accept `--format human|json`. Mutations accept `--dry-run`;
network-capable consumer operations accept `--offline` and then perform no
network request.

## First Verification

```sh
./gradlew :cli:test installDevelopmentCli verifyKotlinOnlyDevelopmentCli
.local/intelligence/bin/intelligence validate --portable
zensical build --clean
```

Continue with the exact command forms on the [Marketplace](marketplace.md) page.
