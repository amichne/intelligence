#!/usr/bin/env bash
set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

resolve_repo_root() {
  local script_dir
  script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
  cd -- "${script_dir}/../.." && pwd
}

require_contains() {
  local file_path="$1"
  local expected="$2"
  local description="$3"
  grep -Fq -- "$expected" "$file_path" || die "${description}: missing '${expected}' in ${file_path}"
}

require_not_contains() {
  local file_path="$1"
  local unexpected="$2"
  local description="$3"
  ! grep -Fq -- "$unexpected" "$file_path" || die "${description}: found '${unexpected}' in ${file_path}"
}

require_order() {
  local file_path="$1"
  local earlier="$2"
  local later="$3"
  local description="$4"
  local earlier_line
  local later_line
  earlier_line="$(grep -nF -- "$earlier" "$file_path" | head -1 | cut -d: -f1)"
  later_line="$(grep -nF -- "$later" "$file_path" | head -1 | cut -d: -f1)"
  [[ -n "$earlier_line" ]] || die "${description}: missing earlier marker '${earlier}'"
  [[ -n "$later_line" ]] || die "${description}: missing later marker '${later}'"
  [[ "$earlier_line" -lt "$later_line" ]] || die "${description}: '${earlier}' must appear before '${later}'"
}

repo_root="$(resolve_repo_root)"
distribution_workflow="${repo_root}/.github/workflows/distribute-intelligence.yml"
release_workflow="${repo_root}/.github/workflows/release.yml"
release_asset_verifier="${repo_root}/.github/scripts/verify-release-assets.sh"
release_state_verifier="${repo_root}/.github/scripts/verify-release-state.sh"
homebrew_test="${repo_root}/packaging/homebrew/scripts/test-formula.py"

for path in \
  "$distribution_workflow" \
  "$release_workflow" \
  "$release_asset_verifier" \
  "$release_state_verifier" \
  "$homebrew_test"
do
  [[ -f "$path" ]] || die "Required release file is missing: $path"
done

require_contains "$distribution_workflow" "Validate CLI source" "Distribution workflow must keep the PR/main validation gate"
require_contains "$distribution_workflow" ".github/scripts/test-release-asset-verifier.sh" "Distribution workflow must test the release asset verifier"
require_contains "$distribution_workflow" ".github/scripts/test-release-workflow-contract.sh" "Distribution workflow must test the release workflow contract"
require_not_contains "$distribution_workflow" 'tags:' "Distribution workflow must not own release tags"
require_not_contains "$distribution_workflow" "workflow_dispatch:" "Distribution workflow must not own manual releases"
require_not_contains "$distribution_workflow" "publish-release:" "Distribution workflow must not publish GitHub releases"
require_not_contains "$distribution_workflow" "Render and push Homebrew tap" "Distribution workflow must not publish the Homebrew tap"

require_contains "$release_workflow" "release_type:" "Release workflow must expose the KAST-style release type input"
require_contains "$release_workflow" "Bump version" "Release workflow must compute and push release tags"
require_contains "$release_workflow" "Release workflow_dispatch must run from main" "Release workflow must protect stable publications from branch heads"
require_contains "$release_workflow" "Ensure draft release exists" "Release workflow must stage assets into a draft release"
require_contains "$release_workflow" "Generate and verify SHA256SUMS" "Release workflow must verify checksums before publication"
require_contains "$release_workflow" ".github/scripts/verify-release-assets.sh" "Release workflow must reuse the asset verifier"
require_contains "$release_workflow" "Publish draft release with CI annotation" "Release workflow must publish the draft after assets are present"
require_contains "$release_workflow" "Render and push Homebrew tap" "Release workflow must render and push the Homebrew tap"
require_contains "$release_workflow" "gh repo clone amichne/homebrew-intelligence" "Release workflow must push the generated tap mirror"
require_contains "$release_workflow" "git -C homebrew-tap remote set-url origin" "Release workflow must authenticate the tap clone before pushing"
require_contains "$release_workflow" "git -C homebrew-tap add -A" "Release workflow must stage all tap changes including deletions"
require_contains "$release_workflow" "Verify published release state" "Release workflow must have final published-state verification"
require_contains "$release_workflow" ".github/scripts/verify-release-state.sh" "Release workflow must call the final release verifier"
require_contains "$release_workflow" "needs.publish-release.result" "Final verification must read the publication result"
require_contains "$release_workflow" "Publish release finished with result" "Final verification must fail when publication did not complete"
require_order "$release_workflow" "Generate and verify SHA256SUMS" "Upload release assets" "Release must verify assets before upload"
require_order "$release_workflow" "Upload release assets" "Publish draft release with CI annotation" "Release must upload assets before publishing"
require_order "$release_workflow" "Publish draft release with CI annotation" "Render and push Homebrew tap" "Release must publish GitHub assets before Homebrew"
require_order "$release_workflow" "Render and push Homebrew tap" "verify-release-state:" "Final verification must run after Homebrew publication"

require_contains "$release_asset_verifier" "linux-x64" "Release asset verifier must require Linux x64"
require_contains "$release_asset_verifier" "linux-arm64" "Release asset verifier must require Linux ARM64"
require_contains "$release_asset_verifier" "macos-x64" "Release asset verifier must require macOS x64"
require_contains "$release_asset_verifier" "macos-arm64" "Release asset verifier must require macOS ARM64"
require_contains "$release_asset_verifier" "intelligence-tui" "Release asset verifier must require the TUI sidecar"
require_contains "$release_state_verifier" "gh release download" "Release state verifier must download release assets"
require_contains "$release_state_verifier" ".github/scripts/verify-release-assets.sh" "Release state verifier must reuse the asset verifier"
require_contains "$release_state_verifier" "releases/latest" "Release state verifier must prove stable releases are latest"
require_contains "$release_state_verifier" "homebrew-intelligence" "Release state verifier must prove stable Homebrew state"

printf 'OK release workflow contract\n'
