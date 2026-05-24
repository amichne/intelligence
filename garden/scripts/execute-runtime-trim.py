#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shlex
import sys
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Execute or preview explicit runtime activation approval packets."
    )
    parser.add_argument(
        "--approvals",
        default="garden/manifests/runtime-activation-approvals.json",
        help="Runtime activation approval queue.",
    )
    parser.add_argument(
        "--packet",
        action="append",
        default=[],
        help="Approval packet name to execute. Repeat for multiple packets.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Mutate the filesystem. Requires --packet and --confirm-runtime-mutation.",
    )
    parser.add_argument(
        "--confirm-runtime-mutation",
        action="store_true",
        help="Required with --apply to confirm packet-scoped runtime mutation.",
    )
    parser.add_argument(
        "--out",
        default="garden/manifests/runtime-trim-execution.json",
        help="Execution receipt JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="garden/docs/runtime-trim-execution.md",
        help="Execution receipt Markdown to write.",
    )
    args = parser.parse_args()

    if args.apply and not args.packet:
        print("--apply requires at least one --packet", file=sys.stderr)
        return 2
    if args.apply and not args.confirm_runtime_mutation:
        print("--apply requires --confirm-runtime-mutation", file=sys.stderr)
        return 2

    repo_root = Path.cwd().resolve()
    approvals_path = (repo_root / args.approvals).resolve()
    approvals = read_json(approvals_path)
    packets = select_packets(approvals, args.packet, args.apply)

    entries: list[dict[str, Any]] = []
    failed = False
    for packet in packets:
        entry = run_packet(packet, apply=args.apply)
        entries.append(entry)
        failed = failed or entry["actionState"] == "FAILED"

    report = build_report(args, entries)
    write_report(repo_root, args, report)
    return 1 if failed else 0


def select_packets(
    approvals: dict[str, Any],
    requested_names: list[str],
    apply: bool,
) -> list[dict[str, Any]]:
    packets = approvals.get("packets", [])
    by_name = {packet["name"]: packet for packet in packets}
    if requested_names:
        selected = []
        for name in requested_names:
            packet = by_name.get(name)
            if packet is None:
                raise SystemExit(f"unknown approval packet: {name}")
            selected.append(packet)
        return selected
    if apply:
        return []
    return [
        packet
        for packet in packets
        if packet.get("approvalState") == "READY_FOR_APPROVAL"
    ]


def run_packet(packet: dict[str, Any], apply: bool) -> dict[str, Any]:
    if packet["operationType"] == "IMPORT_MARKETPLACE":
        return skipped_entry(
            packet,
            "Marketplace imports are manual; open the import URL outside this executor.",
            [action("MANUAL", "Open marketplace import manually", f"open {quote(packet['targetPath'])}")],
        )

    if packet["approvalState"] == "ALREADY_ACTIVE":
        return skipped_entry(
            packet,
            "Approval packet is already active.",
            [action("SKIP_ACTIVE", "Packet already resolves to the canonical target", "true")],
        )

    if packet["approvalState"] != "READY_FOR_APPROVAL":
        return failed_entry(
            packet,
            f"Packet state is {packet['approvalState']}; only READY_FOR_APPROVAL packets can execute.",
            [action("VERIFY_SOURCE", "Reject non-ready packet", "false")],
        )

    if packet["operationType"] == "REPLACE_SOURCE_WITH_SYMLINK":
        return replace_source_with_symlink(packet, apply)
    if packet["operationType"] == "ACTIVATE_RUNTIME_LINK":
        return activate_runtime_link(packet, apply)
    return failed_entry(
        packet,
        f"Unsupported operation type: {packet['operationType']}",
        [action("VERIFY_SOURCE", "Reject unsupported operation", "false")],
    )


def replace_source_with_symlink(packet: dict[str, Any], apply: bool) -> dict[str, Any]:
    source = Path(packet["sourcePath"])
    target = Path(packet["targetPath"])
    backup = Path(packet["backupPath"])
    actions = [
        action("VERIFY_SOURCE", "Verify canonical source exists", f"test -e {quote(source)}"),
        action("VERIFY_SOURCE", "Verify current target exists", f"test -e {quote(target)}"),
        action("PREPARE_TARGET", "Prepare backup directory", f"mkdir -p {quote(backup.parent)}"),
        action("BACKUP_TARGET", "Back up current target", f"mv {quote(target)} {quote(backup)}"),
        action("CREATE_SYMLINK", "Link target to canonical source", f"ln -s {quote(source)} {quote(target)}"),
    ]
    problems = replacement_problems(source, target, backup)
    if problems:
        return failed_entry(packet, "; ".join(problems), actions)
    if not apply:
        return dry_run_entry(packet, actions, ["Ready to back up current target and replace it with a symlink."])

    try:
        backup.parent.mkdir(parents=True, exist_ok=True)
        target.rename(backup)
        os.symlink(source, target)
        if target.resolve(strict=False) != source.resolve(strict=False):
            raise RuntimeError("created symlink does not resolve to the canonical source")
    except Exception as exc:
        rollback_replacement(target, backup)
        actions.append(action("ROLLBACK", "Rollback target from backup after failed link", f"mv {quote(backup)} {quote(target)}"))
        return failed_entry(packet, str(exc), actions)
    return applied_entry(packet, actions, ["Backed up current target and linked it to the canonical source."])


def replacement_problems(source: Path, target: Path, backup: Path) -> list[str]:
    problems = []
    if not os.path.exists(source):
        problems.append("canonical source does not exist")
    if not os.path.lexists(target):
        problems.append("target path does not exist")
    if target.is_symlink():
        problems.append("target path is already a symlink")
    if os.path.lexists(backup):
        problems.append("backup path already exists")
    return problems


def rollback_replacement(target: Path, backup: Path) -> None:
    try:
        if os.path.lexists(target):
            target.unlink()
        if os.path.lexists(backup):
            backup.rename(target)
    except OSError:
        pass


def activate_runtime_link(packet: dict[str, Any], apply: bool) -> dict[str, Any]:
    strategy = packet.get("runtimeLinkStrategy")
    if strategy == "SYMLINK_CHILDREN":
        return activate_child_links(packet, apply)
    if strategy in {"SYMLINK_FILE", "SYMLINK_TREE"}:
        return activate_direct_link(packet, apply)
    return failed_entry(
        packet,
        f"Unsupported runtime link strategy: {strategy}",
        [action("VERIFY_SOURCE", "Reject unsupported runtime link strategy", "false")],
    )


def activate_child_links(packet: dict[str, Any], apply: bool) -> dict[str, Any]:
    source = Path(packet["sourcePath"])
    target = Path(packet["targetPath"])
    actions = [
        action("VERIFY_SOURCE", "Verify source directory exists", f"test -d {quote(source)}"),
        action("PREPARE_TARGET", "Prepare target directory", f"mkdir -p {quote(target)}"),
    ]
    if not source.is_dir():
        return failed_entry(packet, "source directory does not exist", actions)
    if os.path.lexists(target) and not target.is_dir():
        return failed_entry(packet, "target path exists but is not a directory", actions)

    create_children = []
    active_children = []
    collisions = []
    for child in runtime_child_sources(packet, source):
        target_child = target / child.name
        if not os.path.lexists(target_child):
            create_children.append((child, target_child))
            actions.append(
                action("CREATE_SYMLINK", f"Link child {child.name}", f"ln -s {quote(child)} {quote(target_child)}")
            )
            continue
        if target_child.is_symlink() and target_child.resolve(strict=False) == child.resolve(strict=False):
            active_children.append(child.name)
            actions.append(action("SKIP_ACTIVE", f"Child {child.name} is already linked", "true"))
            continue
        collisions.append(target_child)
    if collisions:
        names = ", ".join(str(path) for path in collisions)
        return failed_entry(packet, f"target child collision: {names}", actions)
    if not apply:
        return dry_run_entry(packet, actions, ["Ready to create missing child symlinks."])

    try:
        target.mkdir(parents=True, exist_ok=True)
        for child, target_child in create_children:
            os.symlink(child, target_child)
    except Exception as exc:
        return failed_entry(packet, str(exc), actions)
    if create_children:
        return applied_entry(packet, actions, ["Created missing child symlinks."])
    return skipped_entry(packet, "All children were already active.", actions)


def runtime_child_sources(packet: dict[str, Any], source: Path) -> list[Path]:
    primitive_types = set(packet.get("primitiveTypes", []))
    children = sorted(source.iterdir(), key=lambda item: item.name)
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


def activate_direct_link(packet: dict[str, Any], apply: bool) -> dict[str, Any]:
    source = Path(packet["sourcePath"])
    target = Path(packet["targetPath"])
    actions = [
        action("VERIFY_SOURCE", "Verify source path exists", f"test -e {quote(source)}"),
        action("PREPARE_TARGET", "Prepare target parent", f"mkdir -p {quote(target.parent)}"),
        action("CREATE_SYMLINK", "Link source to target", f"ln -s {quote(source)} {quote(target)}"),
    ]
    if not os.path.exists(source):
        return failed_entry(packet, "source path does not exist", actions)
    if os.path.lexists(target):
        if target.is_symlink() and target.resolve(strict=False) == source.resolve(strict=False):
            return skipped_entry(packet, "Target already resolves to source.", actions)
        return failed_entry(packet, "target path already exists", actions)
    if not apply:
        return dry_run_entry(packet, actions, ["Ready to create direct symlink."])

    try:
        target.parent.mkdir(parents=True, exist_ok=True)
        os.symlink(source, target)
    except Exception as exc:
        return failed_entry(packet, str(exc), actions)
    return applied_entry(packet, actions, ["Created direct symlink."])


def dry_run_entry(packet: dict[str, Any], actions: list[dict[str, str]], reasons: list[str]) -> dict[str, Any]:
    return base_entry(packet, "DRY_RUN", actions, reasons)


def applied_entry(packet: dict[str, Any], actions: list[dict[str, str]], reasons: list[str]) -> dict[str, Any]:
    return base_entry(packet, "APPLIED", actions, reasons)


def skipped_entry(packet: dict[str, Any], reason: str, actions: list[dict[str, str]]) -> dict[str, Any]:
    return base_entry(packet, "SKIPPED", actions, [reason])


def failed_entry(packet: dict[str, Any], reason: str, actions: list[dict[str, str]]) -> dict[str, Any]:
    return base_entry(packet, "FAILED", actions, [reason])


def base_entry(
    packet: dict[str, Any],
    action_state: str,
    actions: list[dict[str, str]],
    reasons: list[str],
) -> dict[str, Any]:
    entry = {
        "type": "RUNTIME_TRIM_EXECUTION_ENTRY",
        "packetName": packet["name"],
        "operationType": packet["operationType"],
        "approvalState": packet["approvalState"],
        "actionState": action_state,
        "riskLevel": packet["riskLevel"],
        "sourcePath": packet["sourcePath"],
        "targetPath": packet["targetPath"],
        "actions": actions,
        "reasons": reasons,
    }
    if packet.get("backupPath"):
        entry["backupPath"] = packet["backupPath"]
    if packet.get("runtimeLinkStrategy"):
        entry["runtimeLinkStrategy"] = packet["runtimeLinkStrategy"]
    if packet.get("primitiveTypes"):
        entry["primitiveTypes"] = packet["primitiveTypes"]
    return entry


def action(effect: str, description: str, command_preview: str) -> dict[str, str]:
    return {
        "type": "RUNTIME_TRIM_EXECUTION_ACTION",
        "effect": effect,
        "description": description,
        "commandPreview": command_preview,
    }


def build_report(args: argparse.Namespace, entries: list[dict[str, Any]]) -> dict[str, Any]:
    counts = Counter(entry["actionState"] for entry in entries)
    return {
        "type": "RUNTIME_TRIM_EXECUTION_REPORT",
        "schemaVersion": 1,
        "generatedAt": datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "generatedFrom": {
            "type": "RUNTIME_TRIM_EXECUTION_INPUTS",
            "runtimeActivationApprovals": args.approvals,
        },
        "summary": {
            "type": "RUNTIME_TRIM_EXECUTION_SUMMARY",
            "mode": "APPLY" if args.apply else "DRY_RUN",
            "requestedPackets": len(args.packet),
            "selectedPackets": len(entries),
            "dryRunEntries": counts.get("DRY_RUN", 0),
            "appliedEntries": counts.get("APPLIED", 0),
            "skippedEntries": counts.get("SKIPPED", 0),
            "failedEntries": counts.get("FAILED", 0),
            "runtimeMutationAttempted": bool(args.apply),
        },
        "entries": entries,
    }


def write_report(repo_root: Path, args: argparse.Namespace, report: dict[str, Any]) -> None:
    out_path = (repo_root / args.out).resolve()
    doc_path = (repo_root / args.doc).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    doc_path.parent.mkdir(parents=True, exist_ok=True)
    doc_path.write_text(render_doc(report), encoding="utf-8")
    print(f"wrote {out_path}")
    print(f"wrote {doc_path}")


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Runtime Trim Execution",
        "",
        "This receipt records an explicit packet-scoped trim or activation attempt.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Mode | `{summary['mode']}` |",
        f"| Requested packets | {summary['requestedPackets']} |",
        f"| Selected packets | {summary['selectedPackets']} |",
        f"| Dry-run entries | {summary['dryRunEntries']} |",
        f"| Applied entries | {summary['appliedEntries']} |",
        f"| Skipped entries | {summary['skippedEntries']} |",
        f"| Failed entries | {summary['failedEntries']} |",
        f"| Runtime mutation attempted | `{str(summary['runtimeMutationAttempted']).lower()}` |",
        "",
        "## Entries",
        "",
        "| State | Packet | Type | Source | Target | Reasons |",
        "|---|---|---|---|---|---|",
    ]
    for entry in report["entries"]:
        reasons = "<br>".join(entry["reasons"])
        lines.append(
            f"| `{entry['actionState']}` | `{entry['packetName']}` | `{entry['operationType']}` | "
            f"`{entry['sourcePath']}` | `{entry['targetPath']}` | {reasons} |"
        )
    lines.extend(["", "## Actions", ""])
    for entry in report["entries"]:
        lines.extend([f"### `{entry['packetName']}`", ""])
        if entry.get("primitiveTypes"):
            lines.append(f"Primitive types: `{', '.join(entry['primitiveTypes'])}`")
            lines.append("")
        for item in entry["actions"]:
            lines.append(f"1. {item['description']}: `{item['commandPreview']}`")
        lines.append("")
    return "\n".join(lines)


def quote(value: str | Path) -> str:
    return shlex.quote(str(value))


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


if __name__ == "__main__":
    raise SystemExit(main())
