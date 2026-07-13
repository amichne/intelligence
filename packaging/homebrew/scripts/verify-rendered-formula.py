#!/usr/bin/env python3
import argparse
import json
import re
from pathlib import Path


STABLE_TAG_RE = re.compile(r"^v(?P<version>\d+\.\d+\.\d+)$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def parse_checksums(path: Path) -> dict[str, str]:
    require(path.is_file(), f"SHA256SUMS not found: {path}")
    checksums: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if not raw_line.strip():
            continue
        parts = raw_line.split()
        require(len(parts) == 2, f"invalid SHA256SUMS line: {raw_line}")
        digest, asset_name = parts
        require(SHA256_RE.fullmatch(digest) is not None, f"invalid SHA-256 for {asset_name}")
        require(asset_name not in checksums, f"duplicate checksum entry for {asset_name}")
        checksums[asset_name] = digest
    return checksums


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify a rendered Intelligence Homebrew formula.")
    parser.add_argument("--tag", required=True)
    parser.add_argument("--sha256s", required=True, type=Path)
    parser.add_argument("--tap-root", required=True, type=Path)
    args = parser.parse_args()

    tag_match = STABLE_TAG_RE.fullmatch(args.tag)
    require(tag_match is not None, f"stable release tag must be vMAJOR.MINOR.PATCH: {args.tag}")
    version = tag_match.group("version")

    formula_path = args.tap_root / "Formula" / "intelligence.rb"
    state_path = args.tap_root / "release-state.json"
    require(formula_path.is_file(), f"Formula/intelligence.rb not found under {args.tap_root}")
    require(state_path.is_file(), f"release-state.json not found under {args.tap_root}")

    state = json.loads(state_path.read_text(encoding="utf-8"))
    require(state.get("schema_version") == 1, "homebrew release-state.json schema_version must be 1")
    require(
        state.get("current_release") == args.tag,
        f"homebrew release-state.json current_release is {state.get('current_release')!r}, expected {args.tag}",
    )

    formula = formula_path.read_text(encoding="utf-8")
    require(f'ARTIFACT_VERSION = "{version}"' in formula, "Formula/intelligence.rb does not name the release version")
    require("disable!" not in formula, "Formula/intelligence.rb is still disabled")
    require(
        'url "#{cli_release_root}/#{release_tag}/intelligence-#{release_tag}.tar.gz"' in formula,
        "Formula/intelligence.rb does not name the platform-neutral JVM release URL",
    )
    require(formula.count("version ARTIFACT_VERSION") == 1, "Formula/intelligence.rb must declare the artifact version once")

    formula_checksums = re.findall(r'^\s*sha256 "([0-9a-f]{64})"\s*$', formula, re.MULTILINE)
    require(len(formula_checksums) == 1, f"Formula/intelligence.rb has {len(formula_checksums)} sha256 entries, expected 1")

    asset_name = f"intelligence-{args.tag}.tar.gz"
    expected_sha = parse_checksums(args.sha256s).get(asset_name)
    require(expected_sha is not None, f"SHA256SUMS is missing {asset_name}")
    require(
        formula_checksums[0] == expected_sha,
        f"Formula/intelligence.rb sha256 for {asset_name} is {formula_checksums[0]}, expected {expected_sha}",
    )

    print(f"Verified rendered Homebrew formula for {args.tag}")


if __name__ == "__main__":
    main()
