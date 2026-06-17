#!/usr/bin/env python3
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"error: {message}")


root = Path(__file__).resolve().parents[1]
formula = root / "Formula" / "intelligence.rb"
readme = root / "README.md"
release_state = root / "release-state.json"
updater = root / "scripts" / "update-formula.py"

require(formula.is_file(), "Formula/intelligence.rb is missing")
require(readme.is_file(), "README.md is missing")
require(release_state.is_file(), "release-state.json is missing")
require(updater.is_file(), "scripts/update-formula.py is missing")

formula_content = formula.read_text(encoding="utf-8")
readme_content = readme.read_text(encoding="utf-8")
state = json.loads(release_state.read_text(encoding="utf-8"))

require('class Intelligence < Formula' in formula_content, "formula class must be Intelligence")
require('desc "Operate portable AI tooling marketplaces"' in formula_content, "formula must describe the CLI marketplace operator")
require(".tar.gz" in formula_content, "formula must install release tarball assets")
require("disable!" in formula_content, "formula template must be disabled until a native release renders real assets")
require('bin.install "intelligence"' in formula_content, "formula must install intelligence")
require('bin.install "intelligence-tui"' in formula_content, "formula must install intelligence-tui")
require("intelligence --version" in formula_content, "formula test must verify intelligence --version")
require(formula_content.count('sha256 "0000000000000000000000000000000000000000000000000000000000000000"') == 4, "formula template must contain four placeholder checksums")
require("brew install amichne/intelligence/intelligence" in readme_content, "README must document direct tap installation")
require("brew tap amichne/intelligence" in readme_content, "README must document manual tap installation")
require(state.get("schema_version") == 1, "release-state.json schema_version must be 1")
require(isinstance(state.get("current_release"), str), "release-state.json current_release must be a string")

subprocess.run(["ruby", "-c", str(formula)], check=True, stdout=subprocess.DEVNULL)

with tempfile.TemporaryDirectory(prefix="intelligence-homebrew-test-") as temp:
    tap_root = Path(temp) / "tap"
    shutil.copytree(root / "Formula", tap_root / "Formula")
    shutil.copy2(readme, tap_root / "README.md")
    shutil.copy2(release_state, tap_root / "release-state.json")

    env = {
        "INTELLIGENCE_TAP_ROOT": str(tap_root),
        "VERSION": "v9.8.7",
        "SHA256_MACOS_X64": "1" * 64,
        "SHA256_MACOS_ARM64": "2" * 64,
        "SHA256_LINUX_X64": "3" * 64,
        "SHA256_LINUX_ARM64": "4" * 64,
    }
    subprocess.run([str(updater)], check=True, env=env)

    updated_formula = (tap_root / "Formula" / "intelligence.rb").read_text(encoding="utf-8")
    updated_readme = (tap_root / "README.md").read_text(encoding="utf-8")
    updated_state = json.loads((tap_root / "release-state.json").read_text(encoding="utf-8"))

    require('ARTIFACT_VERSION = "9.8.7"' in updated_formula, "updater must set the formula version")
    require("disable!" not in updated_formula, "updater must enable rendered release formulas")
    require(updated_state["current_release"] == "v9.8.7", "updater must set release-state current_release")
    require("/v9.8.7/intelligence-v9.8.7-macos-arm64.tar.gz" in updated_readme, "updater must refresh README mirror example")
    for digest in ("1" * 64, "2" * 64, "3" * 64, "4" * 64):
        require(f'sha256 "{digest}"' in updated_formula, f"updater must set checksum {digest}")

    subprocess.run(["ruby", "-c", str(tap_root / "Formula" / "intelligence.rb")], check=True, stdout=subprocess.DEVNULL)

    rendered_urls = re.findall(r'url "#\{cli_release_root\}/#\{release_tag\}/(.*?)"', updated_formula)
    require(len(rendered_urls) == 4, "formula must keep one URL per supported Homebrew platform")

print("OK Homebrew formula")
