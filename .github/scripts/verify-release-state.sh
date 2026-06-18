#!/usr/bin/env bash
set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat >&2 <<'USAGE'
Usage: .github/scripts/verify-release-state.sh --tag <vX.Y.Z> [options]

Verify that an Intelligence release is fully published:
  - GitHub release exists, is not draft, and has the expected stable/prerelease state.
  - Release archives and SHA256SUMS pass local asset verification.
  - Stable releases are the GitHub latest release and are reflected in Homebrew.

Options:
  --repository <owner/repo>           GitHub repository. Defaults to amichne/intelligence.
  --homebrew-repo <owner/repo>        Homebrew tap repository. Defaults to amichne/homebrew-intelligence.
  --work-dir <dir>                    Directory for downloaded assets and tap clone. Defaults to a temp dir.
USAGE
}

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
tag=""
repository="amichne/intelligence"
homebrew_repo="amichne/homebrew-intelligence"
work_dir=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      [[ $# -ge 2 ]] || die "Missing value for --tag"
      tag="$2"; shift 2 ;;
    --tag=*)
      tag="${1#--tag=}"; shift ;;
    --repository)
      [[ $# -ge 2 ]] || die "Missing value for --repository"
      repository="$2"; shift 2 ;;
    --repository=*)
      repository="${1#--repository=}"; shift ;;
    --homebrew-repo)
      [[ $# -ge 2 ]] || die "Missing value for --homebrew-repo"
      homebrew_repo="$2"; shift 2 ;;
    --homebrew-repo=*)
      homebrew_repo="${1#--homebrew-repo=}"; shift ;;
    --work-dir)
      [[ $# -ge 2 ]] || die "Missing value for --work-dir"
      work_dir="$2"; shift 2 ;;
    --work-dir=*)
      work_dir="${1#--work-dir=}"; shift ;;
    --help|-h)
      usage; exit 0 ;;
    *)
      usage; die "Unknown argument: $1" ;;
  esac
done

[[ -n "$tag" ]] || { usage; die "--tag is required"; }
[[ "$tag" == v* ]] || die "--tag must start with v: $tag"
[[ "$repository" == */* ]] || die "--repository must look like owner/repo"
[[ "$homebrew_repo" == */* ]] || die "--homebrew-repo must look like owner/repo"
command -v gh >/dev/null 2>&1 || die "gh is required"

stable_release=false
if [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  stable_release=true
fi

cleanup_dir=""
if [[ -z "$work_dir" ]]; then
  cleanup_dir="$(mktemp -d "${TMPDIR:-/tmp}/intelligence-release-state.XXXXXX")"
  work_dir="$cleanup_dir"
else
  mkdir -p "$work_dir"
fi

cleanup() {
  if [[ -n "$cleanup_dir" ]]; then
    rm -rf "$cleanup_dir"
  fi
}
trap cleanup EXIT

require_false() {
  local value="$1"
  local message="$2"
  [[ "$value" == "false" ]] || die "$message"
}

require_true() {
  local value="$1"
  local message="$2"
  [[ "$value" == "true" ]] || die "$message"
}

is_draft="$(gh release view "$tag" --repo "$repository" --json isDraft --jq .isDraft)"
is_prerelease="$(gh release view "$tag" --repo "$repository" --json isPrerelease --jq .isPrerelease)"
require_false "$is_draft" "GitHub release ${tag} is still a draft"
if [[ "$stable_release" == "true" ]]; then
  require_false "$is_prerelease" "Stable release ${tag} is marked prerelease"
  latest_tag="$(gh api "repos/${repository}/releases/latest" --jq .tag_name)"
  [[ "$latest_tag" == "$tag" ]] || die "Stable release ${tag} is not latest; latest is ${latest_tag}"
else
  require_true "$is_prerelease" "Prerelease ${tag} is not marked prerelease"
fi

release_dir="${work_dir}/release-assets"
rm -rf "$release_dir"
mkdir -p "$release_dir"
gh release download "$tag" --repo "$repository" --dir "$release_dir" --pattern 'intelligence-*.tar.gz'
gh release download "$tag" --repo "$repository" --dir "$release_dir" --pattern 'SHA256SUMS'
"${repo_root}/.github/scripts/verify-release-assets.sh" --release-dir "$release_dir" --tag "$tag"

if [[ "$stable_release" == "true" ]]; then
  tap_dir="${work_dir}/homebrew-tap"
  rm -rf "$tap_dir"
  gh repo clone "$homebrew_repo" "$tap_dir" -- --depth 1 >/dev/null
  ruby -c "${tap_dir}/Formula/intelligence.rb" >/dev/null
  python3 - "$tag" "${release_dir}/SHA256SUMS" "$tap_dir" <<'PY'
import json
import re
import sys
from pathlib import Path

tag = sys.argv[1]
checksum_path = Path(sys.argv[2])
tap_dir = Path(sys.argv[3])
version = tag.removeprefix("v")


def fail(message: str) -> None:
    raise SystemExit(message)


state = json.loads((tap_dir / "release-state.json").read_text(encoding="utf-8"))
if state.get("current_release") != tag:
    fail(f"homebrew release-state.json current_release is {state.get('current_release')!r}, expected {tag!r}")

formula = (tap_dir / "Formula" / "intelligence.rb").read_text(encoding="utf-8")
if f'ARTIFACT_VERSION = "{version}"' not in formula:
    fail("Formula/intelligence.rb does not name the release version")
if "disable!" in formula:
    fail("Formula/intelligence.rb is still disabled")

checksums: dict[str, str] = {}
for raw_line in checksum_path.read_text(encoding="utf-8").splitlines():
    parts = raw_line.split()
    if len(parts) == 2:
        checksums[parts[1]] = parts[0]

pattern = re.compile(
    r'url "#\{cli_release_root\}/#\{release_tag\}/'
    r'intelligence-#\{release_tag\}-(?P<target>[^"]+)\.tar\.gz"\s+'
    r'sha256 "(?P<sha>[0-9a-f]{64})"',
    re.MULTILINE,
)
formula_entries = {match.group("target"): match.group("sha") for match in pattern.finditer(formula)}
expected_targets = {"linux-arm64", "linux-x64", "macos-arm64", "macos-x64"}
if set(formula_entries) != expected_targets:
    fail(f"Formula/intelligence.rb references targets {sorted(formula_entries)}, expected {sorted(expected_targets)}")

for target in expected_targets:
    asset_name = f"intelligence-{tag}-{target}.tar.gz"
    expected_sha = checksums.get(asset_name)
    if expected_sha is None:
        fail(f"SHA256SUMS is missing {asset_name}")
    actual_sha = formula_entries[target]
    if actual_sha != expected_sha:
        fail(f"Formula/intelligence.rb sha256 for {asset_name} is {actual_sha}, expected {expected_sha}")
PY
fi

printf 'Verified published release state for %s\n' "$tag"
