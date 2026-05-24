#!/usr/bin/env python3
from __future__ import annotations

import argparse
import glob
import subprocess
import sys
from pathlib import Path


GENERATORS = [
    ["python3", "scripts/render-codex-plugin-adapter.py"],
    ["python3", "scripts/inventory-primitives.py"],
    ["python3", "scripts/analyze-consolidation.py"],
    ["python3", "scripts/analyze-plugin-coverage.py"],
    ["python3", "scripts/analyze-review-completeness.py"],
    ["python3", "scripts/analyze-source-cleanup-gaps.py"],
    ["python3", "scripts/analyze-source-turnoff-readiness.py"],
    ["python3", "scripts/analyze-runtime-activation.py"],
    ["python3", "scripts/preflight-runtime-activation.py"],
    ["python3", "scripts/analyze-runtime-activation-approvals.py"],
    ["python3", "scripts/analyze-source-root-retirement.py"],
    ["python3", "scripts/analyze-primitive-decision-coverage.py"],
    ["python3", "scripts/analyze-toolbox-readiness.py"],
]

CHECKS = [
    ["python3", "scripts/render-codex-plugin-adapter.py", "--check"],
    ["python3", "scripts/inventory-primitives.py", "--check"],
    ["python3", "scripts/analyze-consolidation.py", "--check"],
    ["python3", "scripts/analyze-plugin-coverage.py", "--check"],
    ["python3", "scripts/analyze-review-completeness.py", "--check"],
    ["python3", "scripts/analyze-source-cleanup-gaps.py", "--check"],
    ["python3", "scripts/analyze-source-turnoff-readiness.py", "--check"],
    ["python3", "scripts/analyze-runtime-activation.py", "--check"],
    ["python3", "scripts/preflight-runtime-activation.py", "--check"],
    ["python3", "scripts/analyze-runtime-activation-approvals.py", "--check"],
    ["python3", "scripts/analyze-source-root-retirement.py", "--check"],
    ["python3", "scripts/analyze-primitive-decision-coverage.py", "--check"],
    ["python3", "scripts/analyze-toolbox-readiness.py", "--check"],
    ["node", "--check", "scripts/validate-manifests.mjs"],
    ["node", "scripts/validate-manifests.mjs"],
    [
        "node",
        "skills/manage-json-schemas/scripts/schema-contracts.js",
        "policy-tree",
        "--root",
        "skills/manage-json-schemas/references/schemas",
    ],
    ["git", "diff", "--check"],
]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Refresh and validate the intelligence source graph."
    )
    parser.add_argument(
        "--refresh",
        action="store_true",
        help="Regenerate derived manifests and docs before running checks.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    if args.refresh:
        for command in GENERATORS:
            if run(command, repo_root) != 0:
                return 1

    for command in CHECKS:
        if run(command, repo_root) != 0:
            return 1

    if check_python_syntax(repo_root) != 0:
        return 1
    if check_shell_syntax(repo_root) != 0:
        return 1

    print("source graph check OK")
    return 0


def run(command: list[str], cwd: Path) -> int:
    print(f"+ {quote_command(command)}")
    return subprocess.run(command, cwd=cwd).returncode


def check_python_syntax(repo_root: Path) -> int:
    failures = 0
    files = sorted(Path(path) for path in glob.glob("scripts/*.py"))
    files.extend(
        [
            Path("hooks/kotlin-horizontalization-check.py"),
            Path("hooks/required-skill-read.py"),
        ]
    )
    for path in files:
        if not path.exists():
            continue
        try:
            source = (repo_root / path).read_text(encoding="utf-8")
            compile(source, str(path), "exec")
            print(f"OK python syntax {path}")
        except SyntaxError as error:
            print(f"FAIL python syntax {path}: {error}", file=sys.stderr)
            failures += 1
    return failures


def check_shell_syntax(repo_root: Path) -> int:
    failures = 0
    shell_files = sorted(glob.glob("hooks/*.sh"))
    for path in shell_files:
        result = run(["bash", "-n", path], repo_root)
        if result != 0:
            failures += 1
    return failures


def quote_command(command: list[str]) -> str:
    return " ".join(command)


if __name__ == "__main__":
    raise SystemExit(main())
