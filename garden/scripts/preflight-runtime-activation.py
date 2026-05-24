#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path
from typing import Any


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Inspect the dry-run runtime activation plan against the current filesystem."
    )
    parser.add_argument(
        "--runtime-activation-plan",
        default="garden/manifests/runtime-activation-plan.json",
        help="Generated runtime activation plan.",
    )
    parser.add_argument(
        "--out",
        default="garden/manifests/runtime-activation-preflight.json",
        help="Preflight JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="garden/docs/runtime-activation-preflight.md",
        help="Markdown preflight summary to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    plan = read_json((repo_root / args.runtime_activation_plan).resolve())
    report = build_report(plan, args)
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


def build_report(plan: dict[str, Any], args: argparse.Namespace) -> dict[str, Any]:
    entries = [
        preflight_entry(operation)
        for operation in plan.get("operations", [])
    ]
    entries = sorted(entries, key=lambda item: (item["status"], item["operationType"], item["operationName"]))
    statuses = Counter(entry["status"] for entry in entries)
    return {
        "type": "RUNTIME_ACTIVATION_PREFLIGHT_REPORT",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "RUNTIME_ACTIVATION_PREFLIGHT_INPUTS",
            "runtimeActivationPlan": args.runtime_activation_plan,
        },
        "summary": {
            "type": "RUNTIME_ACTIVATION_PREFLIGHT_SUMMARY",
            "totalEntries": len(entries),
            "alreadyActive": statuses.get("ALREADY_ACTIVE", 0),
            "blocked": statuses.get("BLOCKED", 0),
            "readyForApproval": statuses.get("READY_FOR_APPROVAL", 0),
            "reviewRequired": statuses.get("REVIEW_REQUIRED", 0),
            "runtimeMutationAllowed": False,
        },
        "entries": entries,
    }


def preflight_entry(operation: dict[str, Any]) -> dict[str, Any]:
    source = filesystem_check(operation["sourcePath"])
    target = filesystem_check(operation["targetPath"])
    backup = filesystem_check(operation["backupPath"]) if operation.get("backupPath") else None
    child_collisions = runtime_child_collisions(operation)
    status, reasons = classify(operation, source, target, backup, child_collisions)
    entry = {
        "type": "RUNTIME_ACTIVATION_PREFLIGHT_ENTRY",
        "operationName": operation["name"],
        "operationType": operation["operationType"],
        "status": status,
        "source": source,
        "target": target,
        "reasons": reasons,
    }
    if backup:
        entry["backup"] = backup
    if child_collisions:
        entry["childCollisions"] = child_collisions
    return entry


def classify(
    operation: dict[str, Any],
    source: dict[str, Any],
    target: dict[str, Any],
    backup: dict[str, Any] | None,
    child_collisions: list[dict[str, Any]],
) -> tuple[str, list[str]]:
    operation_type = operation["operationType"]
    strategy = operation.get("runtimeLinkStrategy")
    reasons: list[str] = []

    if operation_type == "IMPORT_MARKETPLACE":
        if not source["exists"]:
            return "BLOCKED", ["Marketplace source does not exist."]
        if codex_marketplace_already_configured(Path(source["path"])):
            return "ALREADY_ACTIVE", ["Marketplace source is already configured in Codex."]
        return "READY_FOR_APPROVAL", ["Marketplace file exists; manual import still requires user action."]

    if not source["exists"]:
        return "BLOCKED", ["Activation source path does not exist."]

    if operation_type == "REPLACE_SOURCE_WITH_SYMLINK":
        if not target["exists"]:
            return "BLOCKED", ["Replacement target does not exist, so there is nothing to back up before linking."]
        if target["kind"] == "SYMLINK" and target.get("resolvedPath") == source.get("resolvedPath"):
            return "ALREADY_ACTIVE", ["Replacement target already resolves to the canonical source."]
        if target["kind"] == "SYMLINK":
            return "REVIEW_REQUIRED", ["Replacement target is already a symlink to a different path."]
        if backup and backup["exists"]:
            return "REVIEW_REQUIRED", ["Backup path already exists and must not be overwritten."]
        return "READY_FOR_APPROVAL", ["Canonical source and current target exist; approval is still required before backup and symlink replacement."]

    if operation_type == "ACTIVATE_RUNTIME_LINK":
        if operation["status"] == "WAITING_REVIEW":
            reasons.append("Runtime link manifest marks this target as needing review.")
        if not target["exists"]:
            if operation["status"] == "WAITING_APPROVAL":
                return "READY_FOR_APPROVAL", ["Runtime target path does not exist; approval packet will create it before linking children."]
            reasons.append("Runtime target path does not exist.")
        elif strategy in {"SYMLINK_FILE", "SYMLINK_TREE"}:
            if target["kind"] == "SYMLINK" and target.get("resolvedPath") == source.get("resolvedPath"):
                return "ALREADY_ACTIVE", ["Runtime target already resolves to the canonical source."]
            reasons.append("Runtime target path already exists for a direct symlink operation.")
        elif target["kind"] == "SYMLINK" and strategy != "SYMLINK_CHILDREN":
            reasons.append("Runtime target path is itself a symlink; inspect its real target before linking.")
        elif target["kind"] == "SYMLINK" and operation["status"] != "WAITING_APPROVAL":
            reasons.append("Runtime target path is itself a symlink; inspect its real target before linking children.")
        elif target["kind"] != "DIRECTORY" and not (strategy == "SYMLINK_CHILDREN" and target["kind"] == "SYMLINK"):
            reasons.append("Runtime target path is not a directory.")
        if strategy == "SYMLINK_CHILDREN" and runtime_children_already_active(operation):
            return "ALREADY_ACTIVE", ["Runtime target already links every source child to the canonical source."]
        if strategy == "SYMLINK_CHILDREN" and child_collisions:
            names = ", ".join(collision["name"] for collision in child_collisions)
            reasons.append(f"Runtime target already has child paths for source children: {names}.")
        if reasons:
            return "REVIEW_REQUIRED", reasons
        if strategy == "SYMLINK_CHILDREN" and target["kind"] == "SYMLINK":
            return "READY_FOR_APPROVAL", [
                "Runtime source exists and target resolves through a symlinked directory; approval is still required before linking children into the resolved target."
            ]
        return "READY_FOR_APPROVAL", ["Runtime source and target directory exist; approval is still required before linking children."]

    return "REVIEW_REQUIRED", ["Operation type needs manual review."]


def runtime_child_collisions(operation: dict[str, Any]) -> list[dict[str, Any]]:
    if operation.get("runtimeLinkStrategy") != "SYMLINK_CHILDREN":
        return []
    source_value = operation["sourcePath"]
    target_value = operation["targetPath"]
    if source_value.startswith("codex://") or target_value.startswith("codex://"):
        return []
    source_path = Path(os.path.expanduser(source_value))
    target_path = Path(os.path.expanduser(target_value))
    if not source_path.is_dir() or not target_path.exists() or not target_path.is_dir():
        return []
    collisions = []
    for source_child in runtime_child_sources(operation, source_path):
        target_child = target_path / source_child.name
        if not os.path.lexists(target_child):
            continue
        target_check = filesystem_check(str(target_child))
        if (
            target_check["kind"] == "SYMLINK"
            and target_check.get("resolvedPath") == str(source_child.resolve(strict=False))
        ):
            continue
        collisions.append(
            {
                "type": "RUNTIME_ACTIVATION_CHILD_COLLISION",
                "name": source_child.name,
                "sourcePath": str(source_child),
                "targetPath": str(target_child),
                "targetKind": target_check["kind"],
            }
        )
    return collisions


def runtime_children_already_active(operation: dict[str, Any]) -> bool:
    source_value = operation["sourcePath"]
    target_value = operation["targetPath"]
    if source_value.startswith("codex://") or target_value.startswith("codex://"):
        return False
    source_path = Path(os.path.expanduser(source_value))
    target_path = Path(os.path.expanduser(target_value))
    if not source_path.is_dir() or not target_path.is_dir():
        return False
    source_children = runtime_child_sources(operation, source_path)
    if not source_children:
        return False
    for source_child in source_children:
        target_child = target_path / source_child.name
        if not target_child.is_symlink():
            return False
        if target_child.resolve(strict=False) != source_child.resolve(strict=False):
            return False
    return True


def runtime_child_sources(operation: dict[str, Any], source_path: Path) -> list[Path]:
    primitive_types = set(operation.get("primitiveTypes", []))
    children = sorted(source_path.iterdir(), key=lambda item: item.name)
    return [child for child in children if is_runtime_child_source(child, primitive_types)]


def is_runtime_child_source(child: Path, primitive_types: set[str]) -> bool:
    if child.name == "AGENTS.md":
        return False
    if primitive_types == {"SKILL"}:
        return child.is_dir() and (child / "SKILL.md").is_file()
    if primitive_types == {"HOOK"}:
        return child.is_file() and child.name.endswith(".hooks.json")
    if primitive_types == {"AGENT"}:
        return child.is_dir() or child.name.endswith(".agent.md")
    return True


def filesystem_check(path_value: str) -> dict[str, Any]:
    if path_value.startswith("codex://"):
        return {
            "type": "FILESYSTEM_PATH_CHECK",
            "path": path_value,
            "exists": True,
            "kind": "VIRTUAL",
        }
    expanded = Path(os.path.expanduser(path_value))
    exists = os.path.lexists(expanded)
    if not exists:
        return {
            "type": "FILESYSTEM_PATH_CHECK",
            "path": str(expanded),
            "exists": False,
            "kind": "MISSING",
        }
    if expanded.is_symlink():
        result = {
            "type": "FILESYSTEM_PATH_CHECK",
            "path": str(expanded),
            "exists": True,
            "kind": "SYMLINK",
        }
        try:
            result["resolvedPath"] = str(expanded.resolve(strict=False))
        except OSError:
            pass
        return result
    if expanded.is_dir():
        kind = "DIRECTORY"
    elif expanded.is_file():
        kind = "FILE"
    else:
        kind = "OTHER"
    result = {
        "type": "FILESYSTEM_PATH_CHECK",
        "path": str(expanded),
        "exists": True,
        "kind": kind,
    }
    try:
        result["resolvedPath"] = str(expanded.resolve(strict=False))
    except OSError:
        pass
    return result


def codex_marketplace_already_configured(marketplace_path: Path) -> bool:
    config_path = Path(os.path.expanduser(os.environ.get("CODEX_HOME", "~/.codex"))) / "config.toml"
    if not config_path.is_file():
        return False
    marketplace_root = marketplace_path.parent.resolve(strict=False)
    try:
        config_text = config_path.read_text(encoding="utf-8")
    except OSError:
        return False
    return f'source = "{marketplace_root}"' in config_text


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Runtime Activation Preflight",
        "",
        "This generated report inspects dry-run activation operations against the current filesystem. It does not authorize runtime mutation.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Total entries | {summary['totalEntries']} |",
        f"| Already active | {summary['alreadyActive']} |",
        f"| Blocked | {summary['blocked']} |",
        f"| Ready for approval | {summary['readyForApproval']} |",
        f"| Review required | {summary['reviewRequired']} |",
        f"| Runtime mutation allowed | `{str(summary['runtimeMutationAllowed']).lower()}` |",
        "",
        "## Entries",
        "",
        "| Status | Type | Name | Source | Target | Reasons |",
        "|---|---|---|---|---|---|",
    ]
    for entry in report["entries"]:
        reasons = "<br>".join(entry["reasons"])
        lines.append(
            f"| `{entry['status']}` | `{entry['operationType']}` | `{entry['operationName']}` | "
            f"`{entry['source']['path']}` | `{entry['target']['path']}` | {reasons} |"
        )
    lines.extend([
        "",
        "## Path Details",
        "",
        "| Name | Source Kind | Target Kind | Backup Kind |",
        "|---|---|---|---|",
    ])
    for entry in report["entries"]:
        backup = entry.get("backup", {"kind": "-"})
        lines.append(
            f"| `{entry['operationName']}` | `{entry['source']['kind']}` | "
            f"`{entry['target']['kind']}` | `{backup['kind']}` |"
        )
    child_collision_entries = [
        (entry["operationName"], collision)
        for entry in report["entries"]
        for collision in entry.get("childCollisions", [])
    ]
    if child_collision_entries:
        lines.extend([
            "",
            "## Child Collisions",
            "",
            "| Name | Child | Target Kind | Target |",
            "|---|---|---|---|",
        ])
        for operation_name, collision in child_collision_entries:
            lines.append(
                f"| `{operation_name}` | `{collision['name']}` | "
                f"`{collision['targetKind']}` | `{collision['targetPath']}` |"
            )
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
