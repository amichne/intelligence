#!/usr/bin/env bash
set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat >&2 <<'USAGE'
Usage: .github/scripts/verify-release-assets.sh --release-dir <dir> --tag <vX.Y.Z>

Verify a downloaded Intelligence release directory. The directory must contain
the four native CLI/TUI archives and SHA256SUMS.
USAGE
}

release_dir=""
tag=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-dir)
      [[ $# -ge 2 ]] || die "Missing value for --release-dir"
      release_dir="$2"; shift 2 ;;
    --release-dir=*)
      release_dir="${1#--release-dir=}"; shift ;;
    --tag)
      [[ $# -ge 2 ]] || die "Missing value for --tag"
      tag="$2"; shift 2 ;;
    --tag=*)
      tag="${1#--tag=}"; shift ;;
    --help|-h)
      usage; exit 0 ;;
    *)
      usage; die "Unknown argument: $1" ;;
  esac
done

[[ -n "$release_dir" ]] || { usage; die "--release-dir is required"; }
[[ -n "$tag" ]] || { usage; die "--tag is required"; }
[[ "$tag" == v* ]] || die "--tag must start with v: $tag"
[[ -d "$release_dir" ]] || die "Release directory not found: $release_dir"
[[ -f "${release_dir}/SHA256SUMS" ]] || die "SHA256SUMS not found in $release_dir"

python3 - "$release_dir" "$tag" <<'PY'
import hashlib
import tarfile
import sys
from pathlib import Path

release_dir = Path(sys.argv[1])
tag = sys.argv[2]

targets = [
    "linux-x64",
    "linux-arm64",
    "macos-x64",
    "macos-arm64",
]
expected_assets = {f"intelligence-{tag}-{target}.tar.gz" for target in targets}


def fail(message: str) -> None:
    raise SystemExit(message)


actual_assets = {
    path.name
    for path in release_dir.iterdir()
    if path.is_file() and path.name.startswith("intelligence-") and path.name.endswith(".tar.gz")
}

unexpected_assets = sorted(actual_assets - expected_assets)
if unexpected_assets:
    fail(f"unexpected release asset: {unexpected_assets}")

missing_assets = sorted(expected_assets - actual_assets)
if missing_assets:
    fail(f"missing release asset: {missing_assets}")

sha_entries: dict[str, str] = {}
for raw_line in (release_dir / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line:
        continue
    parts = line.split()
    if len(parts) != 2:
        fail(f"invalid SHA256SUMS line: {raw_line}")
    digest, asset_name = parts
    if asset_name in sha_entries:
        fail(f"duplicate checksum entry for {asset_name}")
    sha_entries[asset_name] = digest

unexpected_checksums = sorted(set(sha_entries) - expected_assets)
if unexpected_checksums:
    fail(f"unexpected checksum entry: {unexpected_checksums}")

for asset_name in sorted(expected_assets):
    asset_path = release_dir / asset_name
    expected_digest = sha_entries.get(asset_name)
    if expected_digest is None:
        fail(f"missing checksum entry for {asset_name}")
    actual_digest = hashlib.sha256(asset_path.read_bytes()).hexdigest()
    if actual_digest != expected_digest:
        fail(f"checksum mismatch for {asset_name}: expected {expected_digest}, got {actual_digest}")

    with tarfile.open(asset_path, "r:gz") as archive:
        names = set(archive.getnames())
    missing_members = sorted({"intelligence", "intelligence-tui"} - names)
    if missing_members:
        fail(f"{asset_name} is missing archive member: {missing_members}")

print(f"Verified Intelligence release assets for {tag} in {release_dir}")
PY
