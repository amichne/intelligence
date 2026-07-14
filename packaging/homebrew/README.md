# homebrew-intelligence

Homebrew tap for [amichne-intelligence](https://github.com/amichne/intelligence).

## Install

Install directly from the tap:

```bash
brew install amichne/intelligence/intelligence
intelligence --version
intelligence project --help
```

Homebrew will add the tap automatically. To add the tap first and then install
by short token:

```bash
brew tap amichne/intelligence
brew install intelligence
```

`intelligence` installs the platform-neutral Kotlin/JVM command-line
distribution from `amichne/intelligence`. The formula uses Homebrew's OpenJDK 21
dependency to run the Gradle-generated launcher.

## Enterprise Mirrors

The formula defaults to GitHub release assets, but the release host is resolved
at install time. To use the same tap against an internal artifact mirror, mirror
the release tree under one root and set:

```bash
export HOMEBREW_INTELLIGENCE_ARTIFACT_ROOT="https://artifactory.example.com/artifactory/intelligence-releases"
brew install amichne/intelligence/intelligence
```

The shared mirror root must expose the same repository-shaped paths:

```text
${HOMEBREW_INTELLIGENCE_ARTIFACT_ROOT}/intelligence/releases/download/v0.0.0/intelligence-v0.0.0.tar.gz
```

If your artifact layout points directly at the release directories, set the
component-specific root instead:

```bash
export HOMEBREW_INTELLIGENCE_CLI_RELEASE_ROOT="https://artifactory.example.com/artifactory/intelligence-cli"
```

That root should point at the directory that contains each `vX.Y.Z` release
directory. Checksums remain pinned in the tap, so mirrored artifacts must be
byte-for-byte copies of the published release assets.

The tap tracks the current published release in `release-state.json`. The
formula and release state are rendered atomically by the
`amichne/intelligence` release workflow after the JVM CLI asset is
published from the same tag.
