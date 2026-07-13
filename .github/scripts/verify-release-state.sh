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
  python3 "${repo_root}/packaging/homebrew/scripts/verify-rendered-formula.py" \
    --tag "$tag" \
    --sha256s "${release_dir}/SHA256SUMS" \
    --tap-root "$tap_dir"
fi

printf 'Verified published release state for %s\n' "$tag"
