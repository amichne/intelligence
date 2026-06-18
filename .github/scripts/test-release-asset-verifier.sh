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

compute_sha256() {
  local input_path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$input_path" | awk '{ print $1 }'
    return
  fi
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$input_path" | awk '{ print $1 }'
    return
  fi
  die "Neither sha256sum nor shasum is available"
}

write_asset() {
  local release_dir="$1"
  local tag="$2"
  local target="$3"
  local asset_path="${release_dir}/intelligence-${tag}-${target}.tar.gz"
  local bundle_dir="${scratch_dir}/bundle-${target}"

  rm -rf "$bundle_dir"
  mkdir -p "$bundle_dir"
  printf '#!/usr/bin/env sh\nprintf "intelligence %s\\n"\n' "$target" > "${bundle_dir}/intelligence"
  printf '#!/usr/bin/env sh\nprintf "intelligence-tui %s\\n"\n' "$target" > "${bundle_dir}/intelligence-tui"
  chmod 0755 "${bundle_dir}/intelligence" "${bundle_dir}/intelligence-tui"
  tar -C "$bundle_dir" -czf "$asset_path" intelligence intelligence-tui
}

write_expected_assets() {
  local release_dir="$1"
  local tag="$2"
  local target
  for target in linux-x64 linux-arm64 macos-x64 macos-arm64; do
    write_asset "$release_dir" "$tag" "$target"
  done
}

write_sha256sums() {
  local release_dir="$1"
  : > "${release_dir}/SHA256SUMS"
  local asset_path
  for asset_path in "${release_dir}"/intelligence-*.tar.gz; do
    printf '%s  %s\n' "$(compute_sha256 "$asset_path")" "$(basename -- "$asset_path")" >> "${release_dir}/SHA256SUMS"
  done
}

repo_root="$(resolve_repo_root)"
verifier="${repo_root}/.github/scripts/verify-release-assets.sh"
[[ -x "$verifier" ]] || die "release asset verifier is missing or not executable: $verifier"

scratch_dir="$(mktemp -d "${TMPDIR:-/tmp}/intelligence-release-assets.XXXXXX")"
cleanup() {
  rm -rf "$scratch_dir"
}
trap cleanup EXIT

tag="v9.8.7"
release_dir="${scratch_dir}/release"
mkdir -p "$release_dir"

write_expected_assets "$release_dir" "$tag"
write_sha256sums "$release_dir"
"$verifier" --release-dir "$release_dir" --tag "$tag"

rm -rf "$release_dir"
mkdir -p "$release_dir"
write_expected_assets "$release_dir" "$tag"
rm -f "${release_dir}/intelligence-${tag}-linux-arm64.tar.gz"
write_sha256sums "$release_dir"
if "$verifier" --release-dir "$release_dir" --tag "$tag" >/dev/null 2>"${scratch_dir}/missing.err"; then
  die "release without linux-arm64 unexpectedly verified"
fi
grep -Fq "missing release asset" "${scratch_dir}/missing.err" \
  || die "missing asset failure did not mention missing release asset"

rm -rf "$release_dir"
mkdir -p "$release_dir"
write_expected_assets "$release_dir" "$tag"
write_sha256sums "$release_dir"
printf 'tampered\n' >> "${release_dir}/intelligence-${tag}-linux-x64.tar.gz"
if "$verifier" --release-dir "$release_dir" --tag "$tag" >/dev/null 2>"${scratch_dir}/checksum.err"; then
  die "tampered asset unexpectedly verified"
fi
grep -Fq "checksum mismatch" "${scratch_dir}/checksum.err" \
  || die "tampered asset failure did not mention checksum mismatch"

rm -rf "$release_dir"
mkdir -p "$release_dir"
write_expected_assets "$release_dir" "$tag"
write_sha256sums "$release_dir"
cp "${release_dir}/intelligence-${tag}-linux-x64.tar.gz" "${release_dir}/intelligence-${tag}-debug.tar.gz"
printf '%s  %s\n' \
  "$(compute_sha256 "${release_dir}/intelligence-${tag}-debug.tar.gz")" \
  "intelligence-${tag}-debug.tar.gz" \
  >> "${release_dir}/SHA256SUMS"
if "$verifier" --release-dir "$release_dir" --tag "$tag" >/dev/null 2>"${scratch_dir}/extra.err"; then
  die "extra asset unexpectedly verified"
fi
grep -Fq "unexpected release asset" "${scratch_dir}/extra.err" \
  || die "extra asset failure did not mention unexpected release asset"

printf 'OK release asset verifier\n'
