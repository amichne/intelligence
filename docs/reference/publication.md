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

## Tag

Merge the pull request, wait for any required `main` publication workflow to
finish, then tag the final published `main` SHA.

```sh
git fetch origin main --tags
git switch main
git pull --ff-only origin main

release_tag=v0.2.1
git tag --annotate "${release_tag}" -m "intelligence ${release_tag}"
git push origin "${release_tag}"
```

Stable tags must use `vMAJOR.MINOR.PATCH`. The release workflow accepts other
`v*` tags, but only stable semver tags update the Homebrew tap.

## Watch

Watch the tag-triggered `Distribute Intelligence CLI` workflow until every
native matrix job and the release job complete successfully.

```sh
gh run list --workflow "Distribute Intelligence CLI" --event push --limit 10
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

Download the published release and verify checksums before testing one unpacked
archive.

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
