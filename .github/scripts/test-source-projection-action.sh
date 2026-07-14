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

output_value() {
  local file_path="$1"
  local name="$2"
  sed -n "s/^${name}=//p" "$file_path" | tail -1
}

repo_root="$(resolve_repo_root)"
action_manifest="${repo_root}/action.yml"
action_runner="${repo_root}/.github/actions/project/run.sh"
development_cli="${repo_root}/.local/intelligence/bin/intelligence"

[[ -f "$action_manifest" ]] || die "Source projection action manifest is missing: ${action_manifest}"
[[ -f "$action_runner" ]] || die "Source projection action runner is missing: ${action_runner}"
[[ -x "$action_runner" ]] || die "Source projection action runner must be executable: ${action_runner}"
[[ -x "$development_cli" ]] || die "Development CLI is missing; run ./gradlew installDevelopmentCli"

ruby -e 'require "yaml"; YAML.safe_load(File.read(ARGV.fetch(0)), aliases: true)' "$action_manifest"
require_contains "$action_manifest" "using: composite" "Action must use the auditable composite runtime"
require_contains "$action_manifest" "uses: actions/setup-java@v5" "Action must provision the released JVM runtime"
require_contains "$action_manifest" "uses: actions/setup-python@v6" "Action must provision the release verifier runtime"
require_contains "$action_manifest" "harness:" "Action must expose the target harness"
require_contains "$action_manifest" "source:" "Action must expose the provider-neutral source"
require_contains "$action_manifest" "output:" "Action must expose the generated output path"
require_contains "$action_manifest" "version:" "Action must expose release selection"
require_contains "$action_manifest" "projection-path:" "Action must return the normalized projection path"
require_contains "$action_manifest" "files:" "Action must return the generated file count"

proof_root="$(mktemp -d "${TMPDIR:-/tmp}/intelligence-action-contract.XXXXXX")"
trap 'rm -rf "$proof_root"' EXIT

run_projection() {
  local harness="$1"
  local output="$2"
  local github_output="$3"

  GITHUB_ACTION_PATH="$repo_root" \
  GITHUB_WORKSPACE="$repo_root" \
  GITHUB_OUTPUT="$github_output" \
  RUNNER_TEMP="$proof_root/runner-temp" \
  INPUT_SOURCE="$repo_root" \
  INPUT_HARNESS="$harness" \
  INPUT_OUTPUT="$output" \
  INPUT_VERSION="latest" \
  INTELLIGENCE_ACTION_CLI="$development_cli" \
    "$action_runner"
}

mkdir -p "$proof_root/runner-temp"

codex_output="$proof_root/codex"
codex_github_output="$proof_root/codex.outputs"
run_projection "codex" "$codex_output" "$codex_github_output"
[[ -f "$codex_output/.agents/plugins/marketplace.json" ]] || die "Codex marketplace projection is missing"
codex_output="$(cd -- "$codex_output" && pwd -P)"
require_contains "$codex_github_output" "projection-path=${codex_output}" "Action must report the Codex output path"
require_contains "$codex_github_output" "version=development" "Development seam must report its version authority"
codex_files="$(output_value "$codex_github_output" files)"
[[ "$codex_files" =~ ^[0-9]+$ ]] || die "Action file count must be numeric: ${codex_files}"
[[ "$codex_files" -eq "$(find "$codex_output" -type f | wc -l | tr -d '[:space:]')" ]] || die "Action file count must match the Codex projection"

copilot_output="$proof_root/github-copilot"
copilot_github_output="$proof_root/github-copilot.outputs"
run_projection "github-copilot" "$copilot_output" "$copilot_github_output"
[[ -f "$copilot_output/.github/plugin/marketplace.json" ]] || die "GitHub Copilot marketplace projection is missing"
copilot_output="$(cd -- "$copilot_output" && pwd -P)"
require_contains "$copilot_github_output" "projection-path=${copilot_output}" "Action must report the Copilot output path"

default_github_output="$proof_root/default.outputs"
run_projection "codex" "" "$default_github_output"
default_output="$(output_value "$default_github_output" projection-path)"
runner_temp="$(cd -- "$proof_root/runner-temp" && pwd -P)"
[[ "$default_output" == "${runner_temp}"/intelligence-projection.*/payload ]] || die "Default output must be a fresh directory under RUNNER_TEMP: ${default_output}"
[[ -f "$default_output/.agents/plugins/marketplace.json" ]] || die "Default projection output is missing"

invalid_log="$proof_root/invalid-harness.log"
if run_projection "cursor" "$proof_root/cursor" "$proof_root/cursor.outputs" >"$invalid_log" 2>&1; then
  die "Action accepted an unsupported harness"
fi
require_contains "$invalid_log" "harness must be exactly codex or github-copilot" "Action must explain the harness contract"

invalid_version_log="$proof_root/invalid-version.log"
if GITHUB_ACTION_PATH="$repo_root" \
  GITHUB_WORKSPACE="$repo_root" \
  GITHUB_OUTPUT="$proof_root/invalid-version.outputs" \
  RUNNER_TEMP="$proof_root/runner-temp" \
  INPUT_SOURCE="$repo_root" \
  INPUT_HARNESS="codex" \
  INPUT_OUTPUT="$proof_root/invalid-version" \
  INPUT_VERSION="main" \
    "$action_runner" >"$invalid_version_log" 2>&1
then
  die "Action accepted a non-release version"
fi
require_contains "$invalid_version_log" "version must be latest or an exact vX.Y.Z tag" "Action must explain the version contract"

printf 'OK source projection action contract\n'
