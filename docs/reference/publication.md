# Publication

Use this checklist when cutting the first stable native CLI release. The release
tag must point at the final published `main` commit, not at a pull request head
or a merge commit that is later followed by generated publication changes.

## Preflight

Run the same local gates the pull request and release workflow depend on.

```sh
./gradlew :cli:test installDevelopmentCli
cargo test --manifest-path tui/Cargo.toml
.local/intelligence/bin/intelligence --version
.local/intelligence/bin/intelligence doctor --format json
.local/intelligence/bin/intelligence marketplace inspect "${PWD}" --provider source --format json
.local/intelligence/bin/intelligence marketplace search kotlin --repository "${PWD}" --provider source --format json
.local/intelligence/bin/intelligence validate --portable
.local/intelligence/bin/intelligence-tui --help
packaging/homebrew/scripts/test-formula.py
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
creates a draft GitHub Release, publishes native assets, renders the Homebrew
tap, and verifies the final published state.

```sh
git fetch origin main --tags
gh workflow run Release --ref main -f release_type=patch
```

Use `release_type=major`, `minor`, or `patch` for stable Homebrew releases. Use
`release_type=beta` for a prerelease that publishes GitHub assets but does not
update the tap. Stable tags use `vMAJOR.MINOR.PATCH`; beta tags append the short
commit SHA.

## Watch

Watch the `Release` workflow until every validation, native matrix, publication,
and final verification job completes successfully.

```sh
gh run list --workflow Release --event workflow_dispatch --limit 10
gh run view <run-id> --json status,conclusion,jobs,url
```

The release workflow must publish these assets:

| Asset | Purpose |
|---|---|
| `intelligence-${release_tag}-linux-x64.tar.gz` | Linux x64 native CLI and TUI. |
| `intelligence-${release_tag}-linux-arm64.tar.gz` | Linux ARM64 native CLI and TUI. |
| `intelligence-${release_tag}-macos-x64.tar.gz` | macOS Intel native CLI and TUI. |
| `intelligence-${release_tag}-macos-arm64.tar.gz` | macOS Apple Silicon native CLI and TUI. |
| `SHA256SUMS` | Checksums consumed by release verification and the Homebrew tap renderer. |

## Verify Release Assets

The release workflow runs the checked-in final verifier. You can rerun it
locally for the published tag when auditing a release.

```sh
release_tag=v0.2.1
.github/scripts/verify-release-state.sh --tag "${release_tag}" --repository amichne/intelligence
```

For manual asset inspection, download the published release and verify checksums
before testing one unpacked archive.

```sh
release_tag=v0.2.1
verify_root="$(mktemp -d)"
gh release download "${release_tag}" \
  --pattern "intelligence-*.tar.gz" \
  --pattern "SHA256SUMS" \
  --dir "${verify_root}"

(cd "${verify_root}" && shasum -a 256 -c SHA256SUMS)
tar -xzf "${verify_root}/intelligence-${release_tag}-macos-arm64.tar.gz" -C "${verify_root}"

env -u JAVA_HOME -u JDK_HOME -u GRAALVM_HOME "${verify_root}/intelligence" --version
env -u JAVA_HOME -u JDK_HOME -u GRAALVM_HOME "${verify_root}/intelligence" doctor --format json
env -u JAVA_HOME -u JDK_HOME -u GRAALVM_HOME "${verify_root}/intelligence" validate --repo "${PWD}" --portable
env -u JAVA_HOME -u JDK_HOME -u GRAALVM_HOME "${verify_root}/intelligence-tui" --help
```

On macOS, confirm the CLI does not link against a JVM runtime.

```sh
otool -L "${verify_root}/intelligence" "${verify_root}/intelligence-tui" \
  | tee "${verify_root}/linkage.txt"
! grep -Eiq 'libjvm|libjli|JavaVirtualMachines|/java/' "${verify_root}/linkage.txt"
```

## Verify Homebrew

The stable release workflow updates `amichne/homebrew-intelligence` after the
GitHub Release assets and checksums exist. Verify the tap from a clean Homebrew
state.

```sh
brew update
brew reinstall amichne/intelligence/intelligence
brew test amichne/intelligence/intelligence
intelligence --version
intelligence doctor
intelligence validate --repo "${PWD}" --portable
```

The checked-in template formula stays disabled until the release workflow
renders a stable version and real checksums into the tap repository.
