#!/usr/bin/env bash
set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

download_root=""
runtime_root=""

cleanup() {
  local exit_code="$?"
  if [[ -n "$download_root" ]]; then
    rm -rf -- "$download_root"
  fi
  if [[ -n "$runtime_root" ]]; then
    rm -rf -- "$runtime_root"
  fi
  exit "$exit_code"
}

trap cleanup EXIT

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "$value" ]] || die "${name} is required"
  [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || die "${name} must not contain line breaks"
}

runner_path() {
  local path="$1"
  if [[ "${RUNNER_OS:-}" == "Windows" ]] && command -v cygpath >/dev/null 2>&1; then
    cygpath --unix "$path"
  else
    printf '%s\n' "$path"
  fi
}

external_path() {
  local path="$1"
  if [[ "${RUNNER_OS:-}" == "Windows" ]] && command -v cygpath >/dev/null 2>&1; then
    cygpath --mixed "$path"
  else
    printf '%s\n' "$path"
  fi
}

absolute_source_path() {
  local requested="$1"
  local candidate
  if [[ "$requested" == /* || "$requested" =~ ^[A-Za-z]:[\\/].* ]]; then
    candidate="$(runner_path "$requested")"
  else
    candidate="${workspace_root}/${requested}"
  fi
  [[ -d "$candidate" ]] || die "source directory does not exist: ${candidate}"
  cd -- "$candidate" && pwd -P
}

absolute_output_path() {
  local requested="$1"
  local candidate
  local parent
  local name

  if [[ -z "$requested" ]]; then
    mkdir -p -- "$runner_temp_root"
    candidate="$(mktemp -d "${runner_temp_root}/intelligence-projection.XXXXXX")/payload"
  elif [[ "$requested" == /* || "$requested" =~ ^[A-Za-z]:[\\/].* ]]; then
    candidate="$(runner_path "$requested")"
  else
    candidate="${workspace_root}/${requested}"
  fi

  parent="$(dirname -- "$candidate")"
  name="$(basename -- "$candidate")"
  mkdir -p -- "$parent"
  parent="$(cd -- "$parent" && pwd -P)"
  printf '%s/%s\n' "$parent" "$name"
}

resolve_latest_version() {
  local release_url
  local resolved
  release_url="$(curl \
    --fail \
    --silent \
    --show-error \
    --retry 3 \
    --location \
    --output /dev/null \
    --write-out '%{url_effective}' \
    https://github.com/amichne/intelligence/releases/latest)"
  resolved="${release_url##*/}"
  [[ "$resolved" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "latest release did not resolve to vX.Y.Z: ${release_url}"
  printf '%s\n' "$resolved"
}

download_cli() {
  local version="$1"
  local destination="$2"
  local asset="intelligence-${version}.tar.gz"
  local base_url="https://github.com/amichne/intelligence/releases/download/${version}"
  download_root="$(mktemp -d "${runner_temp_root}/intelligence-download.XXXXXX")"
  curl --fail --location --retry 3 --silent --show-error \
    --output "${download_root}/${asset}" \
    "${base_url}/${asset}"
  curl --fail --location --retry 3 --silent --show-error \
    --output "${download_root}/SHA256SUMS" \
    "${base_url}/SHA256SUMS"

  "${action_root}/.github/scripts/verify-release-assets.sh" \
    --release-dir "$download_root" \
    --tag "$version"

  mkdir -p -- "$destination"
  tar -xzf "${download_root}/${asset}" -C "$destination"
  rm -rf -- "$download_root"
  download_root=""
}

source_input="${INPUT_SOURCE:-}"
harness="${INPUT_HARNESS:-}"
output_input="${INPUT_OUTPUT:-}"
version_input="${INPUT_VERSION:-}"

require_value "source" "$source_input"
require_value "harness" "$harness"
require_value "version" "$version_input"
[[ "$output_input" != *$'\n'* && "$output_input" != *$'\r'* ]] || die "output must not contain line breaks"

case "$harness" in
  codex|github-copilot) ;;
  *) die "harness must be exactly codex or github-copilot: ${harness}" ;;
esac

require_value "GITHUB_ACTION_PATH" "${GITHUB_ACTION_PATH:-}"
require_value "GITHUB_WORKSPACE" "${GITHUB_WORKSPACE:-}"
require_value "GITHUB_OUTPUT" "${GITHUB_OUTPUT:-}"
require_value "RUNNER_TEMP" "${RUNNER_TEMP:-}"

if [[ "$version_input" != "latest" && ! "$version_input" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  die "version must be latest or an exact vX.Y.Z tag: ${version_input}"
fi

action_root="$(runner_path "$GITHUB_ACTION_PATH")"
workspace_root="$(runner_path "$GITHUB_WORKSPACE")"
runner_temp_root="$(runner_path "$RUNNER_TEMP")"
github_output_path="$(runner_path "$GITHUB_OUTPUT")"

source_path="$(absolute_source_path "$source_input")"
output_path="$(absolute_output_path "$output_input")"

if [[ -n "${INTELLIGENCE_ACTION_CLI:-}" ]]; then
  cli="${INTELLIGENCE_ACTION_CLI}"
  resolved_version="development"
  [[ -x "$cli" ]] || die "INTELLIGENCE_ACTION_CLI is not executable: ${cli}"
else
  if [[ "$version_input" == "latest" ]]; then
    resolved_version="$(resolve_latest_version)"
  else
    resolved_version="$version_input"
  fi
  [[ "$resolved_version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "version must be latest or an exact vX.Y.Z tag: ${resolved_version}"

  runtime_root="$(mktemp -d "${runner_temp_root}/intelligence-runtime.XXXXXX")"
  download_cli "$resolved_version" "$runtime_root"
  cli="${runtime_root}/intelligence/bin/intelligence"
  [[ -f "$cli" ]] || die "release launcher is missing: ${cli}"
  chmod +x -- "$cli"
  [[ -x "$cli" ]] || die "release launcher is not executable: ${cli}"
fi

"$cli" project \
  --source "$source_path" \
  --harness "$harness" \
  --out "$output_path"

[[ -d "$output_path" ]] || die "projector did not create the output directory: ${output_path}"
file_count="$(find "$output_path" -type f | wc -l | tr -d '[:space:]')"
[[ "$file_count" =~ ^[0-9]+$ ]] || die "projected file count is not numeric: ${file_count}"
reported_output_path="$(external_path "$output_path")"

{
  printf 'projection-path=%s\n' "$reported_output_path"
  printf 'files=%s\n' "$file_count"
  printf 'version=%s\n' "$resolved_version"
} >> "$github_output_path"
