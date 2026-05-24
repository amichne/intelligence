#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shlex
import sys
from collections import Counter
from pathlib import Path
from typing import Any


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build a dry-run runtime activation plan from readiness and link manifests."
    )
    parser.add_argument(
        "--source-turnoff-readiness",
        default="garden/manifests/source-turnoff-readiness.json",
        help="Generated source turnoff readiness report.",
    )
    parser.add_argument(
        "--cleanup-ledger",
        default="garden/manifests/cleanup-ledger.json",
        help="Cleanup ledger manifest.",
    )
    parser.add_argument(
        "--runtime-links",
        default="garden/manifests/runtime-links.json",
        help="Runtime link plan manifest.",
    )
    parser.add_argument(
        "--source-roots",
        default="garden/manifests/source-roots.json",
        help="Source roots manifest.",
    )
    parser.add_argument(
        "--out",
        default="garden/manifests/runtime-activation-plan.json",
        help="Runtime activation plan JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="garden/docs/runtime-activation-plan.md",
        help="Runtime activation plan Markdown to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    readiness = read_json((repo_root / args.source_turnoff_readiness).resolve())
    cleanup_ledger = read_json((repo_root / args.cleanup_ledger).resolve())
    runtime_links = read_json((repo_root / args.runtime_links).resolve())
    source_roots = read_json((repo_root / args.source_roots).resolve())
    report = build_report(readiness, cleanup_ledger, runtime_links, source_roots, repo_root, args)
    report_text = json.dumps(report, indent=2, sort_keys=True) + "\n"
    doc_text = render_doc(report)

    out_path = (repo_root / args.out).resolve()
    doc_path = (repo_root / args.doc).resolve()
    if args.check:
        return check_file(out_path, report_text) or check_file(doc_path, doc_text)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(report_text, encoding="utf-8")
    doc_path.parent.mkdir(parents=True, exist_ok=True)
    doc_path.write_text(doc_text, encoding="utf-8")
    print(f"wrote {out_path}")
    print(f"wrote {doc_path}")
    return 0


def build_report(
    readiness: dict[str, Any],
    cleanup_ledger: dict[str, Any],
    runtime_links: dict[str, Any],
    source_roots: dict[str, Any],
    repo_root: Path,
    args: argparse.Namespace,
) -> dict[str, Any]:
    operations = replacement_operations(cleanup_ledger, source_roots, repo_root)
    operations.extend(runtime_link_operations(runtime_links, repo_root))
    operations = sorted(operations, key=lambda item: (item["operationType"], item["name"]))
    statuses = Counter(operation["status"] for operation in operations)
    return {
        "type": "RUNTIME_ACTIVATION_PLAN",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "RUNTIME_ACTIVATION_INPUTS",
            "sourceTurnoffReadiness": args.source_turnoff_readiness,
            "cleanupLedger": args.cleanup_ledger,
            "runtimeLinks": args.runtime_links,
            "sourceRoots": args.source_roots,
        },
        "summary": {
            "type": "RUNTIME_ACTIVATION_SUMMARY",
            "mode": "DRY_RUN_ONLY",
            "totalOperations": len(operations),
            "waitingApproval": statuses.get("WAITING_APPROVAL", 0),
            "waitingReview": statuses.get("WAITING_REVIEW", 0),
            "readyForManualImport": statuses.get("READY_FOR_MANUAL_IMPORT", 0),
            "runtimeMutationAllowed": readiness["summary"]["canMutateRuntimeWithoutApproval"],
        },
        "operations": operations,
    }


def replacement_operations(
    cleanup_ledger: dict[str, Any],
    source_roots: dict[str, Any],
    repo_root: Path,
) -> list[dict[str, Any]]:
    roots = source_root_map(source_roots, repo_root)
    operations = []
    for entry in cleanup_ledger.get("entries", []):
        if entry.get("status") != "PROPOSED" or entry.get("decision") != "REPLACE_WITH_SYMLINK":
            continue
        source_base = roots[entry["sourceRoot"]]
        observed = source_base / entry["sourcePath"]
        canonical = repo_root / entry["canonicalPath"]
        backup = repo_root / ".migration-backups" / "source-turnoff" / entry["sourceRoot"] / entry["sourcePath"]
        operations.append(
            {
                "type": "RUNTIME_ACTIVATION_OPERATION",
                "name": slug(f"replace-{entry['sourceRoot']}-{entry['sourcePath']}"),
                "operationType": "REPLACE_SOURCE_WITH_SYMLINK",
                "status": "WAITING_APPROVAL",
                "sourcePath": str(canonical),
                "targetPath": str(observed),
                "backupPath": str(backup),
                "requiresApproval": True,
                "evidence": {
                    "type": "RUNTIME_ACTIVATION_EVIDENCE",
                    "manifest": "garden/manifests/cleanup-ledger.json",
                    "status": entry["status"],
                    "reviewEvidenceSha256": review_evidence_sha256(entry["reviewEvidence"]),
                },
                "steps": [
                    step("Verify canonical source exists", f"test -e {quote(canonical)}"),
                    step("Verify current source exists before backup", f"test -e {quote(observed)}"),
                    step("Prepare backup directory", f"mkdir -p {quote(backup.parent)}"),
                    step("Back up current source path", f"mv {quote(observed)} {quote(backup)}"),
                    step("Link source path to canonical primitive", f"ln -s {quote(canonical)} {quote(observed)}"),
                    step("Verify symlink target", f"readlink {quote(observed)}"),
                ],
            }
        )
    return operations


def review_evidence_sha256(evidence: dict[str, Any]) -> str:
    if evidence["type"] == "SOURCE_REVIEW_EVIDENCE":
        return evidence["sourceSha256"]
    return evidence["targetSha256"]


def runtime_link_operations(runtime_links: dict[str, Any], repo_root: Path) -> list[dict[str, Any]]:
    operations = []
    for entry in runtime_links.get("entries", []):
        operation_type = "IMPORT_MARKETPLACE" if entry["strategy"] == "MARKETPLACE_IMPORT" else "ACTIVATE_RUNTIME_LINK"
        if entry["strategy"] == "MARKETPLACE_IMPORT" and entry["status"] == "READY":
            status = "READY_FOR_MANUAL_IMPORT"
        elif entry["status"] == "PLANNED":
            status = "WAITING_APPROVAL"
        else:
            status = "WAITING_REVIEW"
        source = resolve_repo_path(repo_root, entry["sourcePath"])
        target = entry["targetPath"] if entry["targetPath"].startswith("codex://") else str(expand_path(entry["targetPath"], repo_root))
        operations.append(
            {
                "type": "RUNTIME_ACTIVATION_OPERATION",
                "name": slug(entry["name"]),
                "operationType": operation_type,
                "status": status,
                "sourcePath": str(source),
                "targetPath": target,
                "requiresApproval": entry["requiresApproval"],
                "runtimeLinkStrategy": entry["strategy"],
                "primitiveTypes": entry["primitiveTypes"],
                "collisionPolicy": entry["collisionPolicy"],
                "evidence": {
                    "type": "RUNTIME_ACTIVATION_EVIDENCE",
                    "manifest": "garden/manifests/runtime-links.json",
                    "status": entry["status"],
                },
                "steps": runtime_link_steps(entry, source, target),
            }
        )
    return operations


def runtime_link_steps(entry: dict[str, Any], source: Path, target: str) -> list[dict[str, str]]:
    if entry["strategy"] == "MARKETPLACE_IMPORT":
        return [
            step("Verify marketplace catalog exists", f"test -e {quote(source)}"),
            step("Open marketplace import URL manually after approval", f"open {quote(target)}"),
        ]
    if entry["strategy"] == "SYMLINK_CHILDREN":
        child_preview = child_link_preview_command(entry, source, target)
        return [
            step("Verify source directory exists", f"test -d {quote(source)}"),
            step("Review target directory before linking children", f"ls -la {quote(target)} 2>/dev/null || true"),
            step("Prepare target directory after approval if needed", f"mkdir -p {quote(target)}"),
            step("Link eligible primitive children only after approval", child_preview),
        ]
    return [
        step("Verify source path exists", f"test -e {quote(source)}"),
        step("Link source to target only after approval", f"ln -s {quote(source)} {quote(target)}"),
    ]


def source_root_map(source_roots: dict[str, Any], repo_root: Path) -> dict[str, Path]:
    result = {}
    for item in source_roots.get("scanRoots", []):
        result[item["name"]] = expand_path(item["path"], repo_root)
    return result


def expand_path(path_value: str, repo_root: Path) -> Path:
    expanded = os.path.expanduser(path_value)
    path = Path(expanded)
    if path.is_absolute():
        return path
    return (repo_root / path).resolve()


def resolve_repo_path(repo_root: Path, path_value: str) -> Path:
    if path_value.startswith("codex://"):
        return Path(path_value)
    return expand_path(path_value, repo_root)


def step(description: str, command_preview: str) -> dict[str, str]:
    return {
        "type": "RUNTIME_ACTIVATION_STEP",
        "description": description,
        "commandPreview": command_preview,
    }


def child_link_preview_command(entry: dict[str, Any], source: Path, target: str) -> str:
    primitive_types = set(entry.get("primitiveTypes", []))
    if primitive_types == {"SKILL"}:
        return (
            f"find {quote(source)} -mindepth 1 -maxdepth 1 -type d "
            f"-exec sh -c 'test -f \"$1/SKILL.md\" && echo ln -s \"$1\" \"$2/$(basename \"$1\")\"' sh '{{}}' {quote(target)} ';'"
        )
    if primitive_types == {"HOOK"}:
        return (
            f"find {quote(source)} -mindepth 1 -maxdepth 1 -type f -name '*.hooks.json' "
            f"-exec sh -c 'echo ln -s \"$1\" \"$2/$(basename \"$1\")\"' sh '{{}}' {quote(target)} ';'"
        )
    if primitive_types == {"AGENT"}:
        return (
            f"find {quote(source)} -mindepth 1 -maxdepth 1 "
            f"\\( -type d -o -name '*.agent.md' \\) ! -name AGENTS.md "
            f"-exec sh -c 'echo ln -s \"$1\" \"$2/$(basename \"$1\")\"' sh '{{}}' {quote(target)} ';'"
        )
    return (
        f"find {quote(source)} -mindepth 1 -maxdepth 1 ! -name AGENTS.md "
        f"-exec sh -c 'echo ln -s \"$1\" \"$2/$(basename \"$1\")\"' sh '{{}}' {quote(target)} ';'"
    )


def slug(value: str) -> str:
    chars = []
    for char in value.lower():
        if char.isalnum():
            chars.append(char)
        elif chars and chars[-1] != "-":
            chars.append("-")
    return "".join(chars).strip("-")


def quote(value: str | Path) -> str:
    return shlex.quote(str(value))


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Runtime Activation Plan",
        "",
        "This generated plan is dry-run evidence. It lists future activation operations but does not authorize runtime mutation.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Mode | `{summary['mode']}` |",
        f"| Total operations | {summary['totalOperations']} |",
        f"| Waiting approval | {summary['waitingApproval']} |",
        f"| Waiting review | {summary['waitingReview']} |",
        f"| Ready for manual import | {summary['readyForManualImport']} |",
        f"| Runtime mutation allowed | `{str(summary['runtimeMutationAllowed']).lower()}` |",
        "",
        "## Operations",
        "",
        "| Status | Type | Name | Source | Target |",
        "|---|---|---|---|---|",
    ]
    for operation in report["operations"]:
        lines.append(
            f"| `{operation['status']}` | `{operation['operationType']}` | `{operation['name']}` | "
            f"`{operation['sourcePath']}` | `{operation['targetPath']}` |"
        )
    lines.extend([
        "",
        "## Step Preview",
        "",
    ])
    for operation in report["operations"]:
        lines.extend([
            f"### `{operation['name']}`",
            "",
            f"- Status: `{operation['status']}`",
            f"- Evidence: `{operation['evidence']['manifest']}`",
            "",
        ])
        for item in operation["steps"]:
            lines.append(f"1. {item['description']}: `{item['commandPreview']}`")
        if operation.get("primitiveTypes"):
            lines.append("")
            lines.append(f"Primitive types: `{', '.join(operation['primitiveTypes'])}`")
        lines.append("")
    return "\n".join(lines)


def check_file(path: Path, expected: str) -> int:
    if not path.exists():
        print(f"missing generated file: {path}", file=sys.stderr)
        return 1
    actual = path.read_text(encoding="utf-8")
    if actual != expected:
        print(f"generated file is stale: {path}", file=sys.stderr)
        return 1
    return 0


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


if __name__ == "__main__":
    raise SystemExit(main())
