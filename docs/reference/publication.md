# Publication

Use this checklist when cutting a stable Kotlin/JVM CLI release. The release
tag must point at the final published `main` commit, not at a pull request head
or a merge commit followed by generated publication changes.

## Preflight

Run the same local gates the pull request and release workflow depend on.

```sh
./gradlew check
.local/intelligence/bin/intelligence --version
.local/intelligence/bin/intelligence doctor --format json
.local/intelligence/bin/intelligence marketplace inspect "${PWD}" --provider source --format json
.local/intelligence/bin/intelligence marketplace search kotlin --repository "${PWD}" --provider source --format json
.local/intelligence/bin/intelligence validate --portable
packaging/homebrew/scripts/test-formula.py
.github/scripts/test-release-asset-verifier.sh
.github/scripts/test-release-workflow-contract.sh
zensical build --clean
git diff --check
```

Before merging, confirm the pull request checks are complete and successful.

```sh
gh pr view <number> --json mergeStateStatus,statusCheckRollup,url
```

## Start Release

Merge the pull request, wait for the `main` distribution workflow to finish,
then run the release workflow from `main`. The workflow computes the next tag,
creates a draft GitHub Release, publishes the JVM distribution, renders the
Homebrew tap, and verifies the final published state.

```sh
git fetch origin main --tags
gh workflow run Release --ref main -f release_type=patch
```

Use `release_type=major`, `minor`, or `patch` for stable Homebrew releases. Use
`release_type=beta` for a prerelease that publishes GitHub assets but does not
update the tap. Stable tags use `vMAJOR.MINOR.PATCH`; beta tags append the short
commit SHA.

## Watch

Watch the `Release` workflow until validation, JVM distribution, publication,
and final verification all complete successfully.

```sh
gh run list --workflow Release --event workflow_dispatch --limit 10
gh run view <run-id> --json status,conclusion,jobs,url
```

The release workflow publishes these assets:

| Asset | Purpose |
|---|---|
| `intelligence-${release_tag}.tar.gz` | Platform-neutral Gradle application distribution with shell and Windows launchers plus runtime JARs. |
| `SHA256SUMS` | Checksums consumed by release verification and the Homebrew tap renderer. |

The archive verifier rejects unexpected runtime files, unsafe archive members,
native libraries, and Python or Rust payloads. Gradle writes archive entries in
a reproducible order without preserving source timestamps.

## Verify Release Assets

The release workflow runs the checked-in final verifier. You can rerun it
locally for the published tag when auditing a release.

```sh
release_tag=v0.2.1
.github/scripts/verify-release-state.sh --tag "${release_tag}" --repository amichne/intelligence
```

For manual inspection, download the published release and verify checksums
before testing the unpacked Gradle distribution with Java 21 or newer.

```sh
release_tag=v0.2.1
verify_root="$(mktemp -d)"
gh release download "${release_tag}" \
  --pattern "intelligence-*.tar.gz" \
  --pattern "SHA256SUMS" \
  --dir "${verify_root}"

(cd "${verify_root}" && shasum -a 256 -c SHA256SUMS)
tar -xzf "${verify_root}/intelligence-${release_tag}.tar.gz" -C "${verify_root}"

"${verify_root}/intelligence/bin/intelligence" --version
"${verify_root}/intelligence/bin/intelligence" doctor --format json
"${verify_root}/intelligence/bin/intelligence" validate --repo "${PWD}" --portable
```

## Verify Homebrew

The stable release workflow updates `amichne/homebrew-intelligence` after the
GitHub Release asset and checksum exist. The formula declares `openjdk@21`,
installs the Gradle distribution under `libexec`, and exposes only the
`intelligence` launcher.

```sh
brew update
brew reinstall amichne/intelligence/intelligence
brew test amichne/intelligence/intelligence
intelligence --version
intelligence doctor
intelligence validate --repo "${PWD}" --portable
```

The checked-in template formula stays disabled until the release workflow
renders a stable version and real checksum into the tap repository.
