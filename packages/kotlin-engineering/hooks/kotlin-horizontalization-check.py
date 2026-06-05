#!/usr/bin/env python3
"""Detect Kotlin package horizontalization in changed package directories."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


SOURCE_ROOT_PARTS = {
    ("src", "main", "kotlin"),
    ("src", "test", "kotlin"),
    ("src", "integrationTest", "kotlin"),
}

GENERIC_PREFIX_TOKENS = {
    "Abstract",
    "Base",
    "Default",
    "Impl",
    "Internal",
    "Spec",
    "Test",
}

DECLARATION_RE = re.compile(
    r"^(?:public|private|internal|protected|open|final|\s)*"
    r"\b(sealed\s+(?:class|interface)|data\s+class|enum\s+class|"
    r"value\s+class|annotation\s+class|abstract\s+class|class|interface|object)"
    r"\s+([A-Z][A-Za-z0-9_]*)\b"
)


@dataclass(frozen=True)
class Finding:
    severity: str
    path: str
    message: str
    evidence: dict[str, object]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check changed Kotlin package directories for flat layout."
    )
    parser.add_argument("--repo", default=".", help="Repository root.")
    parser.add_argument(
        "--changed-files-file",
        help="Newline-delimited repo-relative changed paths.",
    )
    parser.add_argument(
        "--changed-file",
        action="append",
        default=[],
        help="Repo-relative changed path. May be repeated.",
    )
    parser.add_argument(
        "--format",
        choices=("text", "json"),
        default="text",
        help="Output format.",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Check every Kotlin source file under known Kotlin source roots.",
    )
    return parser.parse_args()


def git_changed_files(repo: Path) -> list[str]:
    commands = [
        ["git", "-C", str(repo), "diff", "--name-only", "--diff-filter=ACMRTUXB", "--", "*.kt"],
        ["git", "-C", str(repo), "diff", "--cached", "--name-only", "--diff-filter=ACMRTUXB", "--", "*.kt"],
        ["git", "-C", str(repo), "ls-files", "--others", "--exclude-standard", "--", "*.kt"],
    ]
    values: list[str] = []
    for command in commands:
        try:
            result = subprocess.run(
                command,
                check=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
            )
        except FileNotFoundError:
            continue
        if result.returncode == 0:
            values.extend(line.strip() for line in result.stdout.splitlines() if line.strip())
    return values


def all_kotlin_source_files(repo: Path) -> list[Path]:
    files: list[Path] = []
    for source_root in SOURCE_ROOT_PARTS:
        for root in repo.rglob(str(Path(*source_root))):
            if root.is_dir():
                files.extend(
                    path
                    for path in root.rglob("*.kt")
                    if path.is_file() and not path.name.endswith(".generated.kt")
                )
    return sorted(set(files))


def read_changed_files(args: argparse.Namespace, repo: Path) -> list[Path]:
    values = list(args.changed_file)
    if args.changed_files_file:
        state_file = (repo / args.changed_files_file).resolve()
        if state_file.exists():
            values.extend(
                line.strip()
                for line in state_file.read_text(encoding="utf-8").splitlines()
                if line.strip()
            )
    if args.all:
        return all_kotlin_source_files(repo)
    if not values:
        values.extend(git_changed_files(repo))

    files: list[Path] = []
    for value in values:
        path = Path(value)
        if not path.is_absolute():
            path = repo / path
        path = path.resolve()
        if path.suffix == ".kt" and path.exists():
            files.append(path)
    return sorted(set(files))


def is_source_root(path: Path) -> bool:
    parts = path.parts
    return any(tuple(parts[-len(root) :]) == root for root in SOURCE_ROOT_PARTS)


def nearest_source_root(path: Path) -> Path | None:
    for candidate in [path, *path.parents]:
        if is_source_root(candidate):
            return candidate
    return None


def impacted_directories(files: list[Path]) -> set[Path]:
    directories: set[Path] = set()
    for file_path in files:
        source_root = nearest_source_root(file_path.parent)
        current = file_path.parent
        while source_root is not None and source_root in [current, *current.parents]:
            directories.add(current)
            if current == source_root:
                break
            current = current.parent
    return directories


def kotlin_files(directory: Path) -> list[Path]:
    return sorted(
        file_path
        for file_path in directory.glob("*.kt")
        if file_path.is_file() and not file_path.name.endswith(".generated.kt")
    )


def split_pascal_case(value: str) -> list[str]:
    return re.findall(r"[A-Z]+(?=[A-Z][a-z]|[0-9]|\b)|[A-Z]?[a-z]+|[0-9]+", value)


def meaningful_tokens(value: str) -> list[str]:
    return [
        token
        for token in split_pascal_case(value)
        if token and token not in GENERIC_PREFIX_TOKENS
    ]


def primary_declarations(file_path: Path) -> list[tuple[str, str]]:
    declarations: list[tuple[str, str]] = []
    for line in file_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        match = DECLARATION_RE.match(line)
        if match:
            declarations.append((match.group(1).replace("  ", " "), match.group(2)))
    return declarations


def is_module_or_feature_root(directory: Path) -> bool:
    parent = directory.parent
    if is_source_root(parent):
        return True
    siblings_with_kotlin = [
        sibling
        for sibling in parent.iterdir()
        if sibling.is_dir() and any(sibling.glob("*.kt"))
    ]
    return len(siblings_with_kotlin) >= 2 and len(kotlin_files(parent)) == 0


def file_count_finding(repo: Path, directory: Path, files: list[Path]) -> Finding | None:
    count = len(files)
    relative = directory.relative_to(repo).as_posix()
    if count >= 8:
        return Finding(
            "fail",
            relative,
            "package directory has 8 or more direct Kotlin files",
            {"directKotlinFiles": count},
        )
    if is_module_or_feature_root(directory) and count > 5:
        return Finding(
            "fail",
            relative,
            "module or feature root exposes more than 5 direct Kotlin files",
            {"directKotlinFiles": count},
        )
    if count >= 6:
        return Finding(
            "concern",
            relative,
            "package directory has 6 or 7 direct Kotlin files",
            {"directKotlinFiles": count},
        )
    return None


def prefix_findings(repo: Path, directory: Path, files: list[Path]) -> list[Finding]:
    groups: dict[str, set[str]] = defaultdict(set)
    for file_path in files:
        names = [file_path.stem]
        names.extend(name for _, name in primary_declarations(file_path))
        for name in names:
            tokens = meaningful_tokens(name)
            if not tokens:
                continue
            groups[tokens[0]].add(file_path.name)
            if len(tokens) >= 2:
                groups[" ".join(tokens[:2])].add(file_path.name)

    findings: list[Finding] = []
    relative = directory.relative_to(repo).as_posix()
    for prefix, grouped_files in sorted(groups.items()):
        if len(grouped_files) < 3:
            continue
        ratio = len(grouped_files) / max(len(files), 1)
        severity = "fail" if ratio >= 0.5 and len(files) >= 6 else "concern"
        findings.append(
            Finding(
                severity,
                relative,
                f"prefix cluster '{prefix}' appears in {len(grouped_files)} peer files",
                {
                    "prefix": prefix,
                    "files": sorted(grouped_files),
                    "directoryShare": round(ratio, 2),
                },
            )
        )
    return findings


def file_member_findings(repo: Path, files: list[Path]) -> list[Finding]:
    findings: list[Finding] = []
    for file_path in files:
        declarations = primary_declarations(file_path)
        if len(declarations) <= 1:
            continue
        has_sealed_root = any(kind.startswith("sealed") for kind, _ in declarations)
        severity = "concern" if has_sealed_root else "fail"
        findings.append(
            Finding(
                severity,
                file_path.relative_to(repo).as_posix(),
                "file has multiple primary top-level declarations",
                {
                    "declarations": [
                        {"kind": kind, "name": name} for kind, name in declarations
                    ],
                    "sealedHierarchyExceptionPossible": has_sealed_root,
                },
            )
        )
    return findings


def collect_findings(repo: Path, changed_files: list[Path]) -> list[Finding]:
    findings: list[Finding] = []
    checked_directories = impacted_directories(changed_files)
    for directory in sorted(checked_directories):
        direct_files = kotlin_files(directory)
        if not direct_files:
            continue
        count_finding = file_count_finding(repo, directory, direct_files)
        if count_finding:
            findings.append(count_finding)
        findings.extend(prefix_findings(repo, directory, direct_files))
    findings.extend(file_member_findings(repo, changed_files))
    return findings


def write_text(findings: list[Finding]) -> None:
    if not findings:
        print("kotlin-horizontalization: pass")
        return
    for finding in findings:
        print(f"kotlin-horizontalization: {finding.severity}: {finding.path}")
        print(f"  {finding.message}")
        print(f"  evidence: {json.dumps(finding.evidence, sort_keys=True)}")


def write_json(findings: list[Finding]) -> None:
    print(
        json.dumps(
            {
                "ok": not any(finding.severity == "fail" for finding in findings),
                "findings": [
                    {
                        "severity": finding.severity,
                        "path": finding.path,
                        "message": finding.message,
                        "evidence": finding.evidence,
                    }
                    for finding in findings
                ],
            },
            indent=2,
            sort_keys=True,
        )
    )


def main() -> int:
    args = parse_args()
    repo = Path(args.repo).resolve()
    changed_files = read_changed_files(args, repo)
    findings = collect_findings(repo, changed_files)

    if args.format == "json":
        write_json(findings)
    else:
        write_text(findings)

    return 1 if any(finding.severity == "fail" for finding in findings) else 0


if __name__ == "__main__":
    sys.exit(main())
