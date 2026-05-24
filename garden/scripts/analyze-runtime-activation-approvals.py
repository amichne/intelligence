#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build review packets for dry-run runtime activation approval."
    )
    parser.add_argument(
        "--source-turnoff-readiness",
        default="garden/manifests/source-turnoff-readiness.json",
        help="Generated source turnoff readiness report.",
    )
    parser.add_argument(
        "--runtime-activation-plan",
        default="garden/manifests/runtime-activation-plan.json",
        help="Generated runtime activation plan.",
    )
    parser.add_argument(
        "--runtime-activation-preflight",
        default="garden/manifests/runtime-activation-preflight.json",
        help="Generated runtime activation preflight report.",
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
        "--out",
        default="garden/manifests/runtime-activation-approvals.json",
        help="Runtime activation approval queue JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="garden/docs/runtime-activation-approvals.md",
        help="Markdown approval queue summary to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    readiness = read_json((repo_root / args.source_turnoff_readiness).resolve())
    plan = read_json((repo_root / args.runtime_activation_plan).resolve())
    preflight = read_json((repo_root / args.runtime_activation_preflight).resolve())
    cleanup_ledger = read_json((repo_root / args.cleanup_ledger).resolve())
    runtime_links = read_json((repo_root / args.runtime_links).resolve())
    report = build_report(readiness, plan, preflight, cleanup_ledger, runtime_links, args)
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
    plan: dict[str, Any],
    preflight: dict[str, Any],
    cleanup_ledger: dict[str, Any],
    runtime_links: dict[str, Any],
    args: argparse.Namespace,
) -> dict[str, Any]:
    preflight_by_name = {entry["operationName"]: entry for entry in preflight.get("entries", [])}
    cleanup_by_operation = cleanup_operation_map(cleanup_ledger)
    runtime_link_by_operation = runtime_link_operation_map(runtime_links)
    replacement_dependencies = replacement_dependency_map(plan)
    packets = []
    for operation in plan.get("operations", []):
        name = operation["name"]
        if name not in preflight_by_name:
            raise ValueError(f"runtime activation operation has no preflight entry: {name}")
        packets.append(
            approval_packet(
                operation,
                preflight_by_name[name],
                cleanup_by_operation.get(name),
                runtime_link_by_operation.get(name),
                replacement_dependencies,
                args,
            )
        )
    packets = sorted(packets, key=lambda item: (item["approvalState"], item["operationType"], item["name"]))
    states = Counter(packet["approvalState"] for packet in packets)
    return {
        "type": "RUNTIME_ACTIVATION_APPROVAL_QUEUE",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "RUNTIME_ACTIVATION_APPROVAL_INPUTS",
            "sourceTurnoffReadiness": args.source_turnoff_readiness,
            "runtimeActivationPlan": args.runtime_activation_plan,
            "runtimeActivationPreflight": args.runtime_activation_preflight,
            "cleanupLedger": args.cleanup_ledger,
            "runtimeLinks": args.runtime_links,
        },
        "summary": {
            "type": "RUNTIME_ACTIVATION_APPROVAL_SUMMARY",
            "sourceTurnoffStatus": readiness["summary"]["readinessStatus"],
            "totalPackets": len(packets),
            "readyForApproval": states.get("READY_FOR_APPROVAL", 0),
            "readyForManualImport": states.get("READY_FOR_MANUAL_IMPORT", 0),
            "reviewRequired": states.get("REVIEW_REQUIRED", 0),
            "blocked": states.get("BLOCKED", 0),
            "alreadyActive": states.get("ALREADY_ACTIVE", 0),
            "runtimeMutationAllowed": readiness["summary"]["canMutateRuntimeWithoutApproval"],
            "approvalRequired": any(packet["requiresApproval"] for packet in packets),
            "nextAction": next_action(states),
        },
        "packets": packets,
    }


def approval_packet(
    operation: dict[str, Any],
    preflight: dict[str, Any],
    cleanup_entry: dict[str, Any] | None,
    runtime_link: dict[str, Any] | None,
    replacement_dependencies: dict[str, dict[str, str]],
    args: argparse.Namespace,
) -> dict[str, Any]:
    state = approval_state(operation, preflight)
    packet = {
        "type": "RUNTIME_ACTIVATION_APPROVAL_PACKET",
        "name": operation["name"],
        "operationType": operation["operationType"],
        "approvalState": state,
        "planStatus": operation["status"],
        "preflightStatus": preflight["status"],
        "riskLevel": risk_level(operation),
        "requiresApproval": operation["requiresApproval"],
        "sourcePath": operation["sourcePath"],
        "targetPath": operation["targetPath"],
        "sourceKind": preflight["source"]["kind"],
        "targetKind": preflight["target"]["kind"],
        "evidence": {
            "type": "RUNTIME_ACTIVATION_APPROVAL_EVIDENCE",
            "manifest": operation["evidence"]["manifest"],
            "preflightReport": args.runtime_activation_preflight,
            "status": operation["evidence"]["status"],
        },
        "preflightReasons": preflight["reasons"],
        "approvalBoundary": approval_boundary(operation),
        "rollback": rollback(operation, cleanup_entry),
        "stepPreviews": [
            {
                "type": "RUNTIME_ACTIVATION_APPROVAL_STEP",
                "description": step["description"],
                "commandPreview": step["commandPreview"],
            }
            for step in operation["steps"]
        ],
    }
    if operation.get("backupPath"):
        packet["backupPath"] = operation["backupPath"]
    if preflight.get("backup"):
        packet["backupKind"] = preflight["backup"]["kind"]
    if operation.get("runtimeLinkStrategy"):
        packet["runtimeLinkStrategy"] = operation["runtimeLinkStrategy"]
    if operation.get("primitiveTypes"):
        packet["primitiveTypes"] = operation["primitiveTypes"]
    if preflight.get("childCollisions"):
        packet["childCollisions"] = preflight["childCollisions"]
        dependencies = child_collision_dependencies(preflight["childCollisions"], replacement_dependencies)
        if dependencies:
            packet["dependencyPackets"] = dependencies
    if operation["evidence"].get("reviewEvidenceSha256"):
        packet["evidence"]["reviewEvidenceSha256"] = operation["evidence"]["reviewEvidenceSha256"]
    if runtime_link:
        packet["collisionPolicy"] = runtime_link["collisionPolicy"]
        packet["notes"] = runtime_link["notes"]
    if cleanup_entry:
        packet["notes"] = cleanup_entry["verification"]
    return packet


def cleanup_operation_map(cleanup_ledger: dict[str, Any]) -> dict[str, dict[str, Any]]:
    result = {}
    for entry in cleanup_ledger.get("entries", []):
        if entry.get("decision") != "REPLACE_WITH_SYMLINK":
            continue
        result[slug(f"replace-{entry['sourceRoot']}-{entry['sourcePath']}")] = entry
    return result


def runtime_link_operation_map(runtime_links: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {
        slug(entry["name"]): entry
        for entry in runtime_links.get("entries", [])
    }


def replacement_dependency_map(plan: dict[str, Any]) -> dict[str, dict[str, str]]:
    result = {}
    for operation in plan.get("operations", []):
        if operation.get("operationType") != "REPLACE_SOURCE_WITH_SYMLINK":
            continue
        target_path = operation["targetPath"]
        dependency = {
            "type": "RUNTIME_ACTIVATION_DEPENDENCY_PACKET",
            "name": operation["name"],
            "targetPath": target_path,
            "reason": "Approve and execute this replacement before retrying the child-link activation that collides with the same target path.",
        }
        for key in target_path_keys(target_path):
            result[key] = dependency
    return result


def child_collision_dependencies(
    child_collisions: list[dict[str, Any]],
    replacement_dependencies: dict[str, dict[str, str]],
) -> list[dict[str, str]]:
    dependencies = []
    seen = set()
    for collision in child_collisions:
        for key in target_path_keys(collision["targetPath"]):
            dependency = replacement_dependencies.get(key)
            if not dependency or dependency["name"] in seen:
                continue
            dependencies.append(dependency)
            seen.add(dependency["name"])
            break
    return sorted(dependencies, key=lambda item: item["name"])


def target_path_keys(path_value: str) -> list[str]:
    path = Path(path_value)
    keys = [str(path)]
    try:
        keys.append(str(path.resolve(strict=False)))
    except OSError:
        pass
    return list(dict.fromkeys(keys))


def approval_state(operation: dict[str, Any], preflight: dict[str, Any]) -> str:
    preflight_status = preflight["status"]
    if preflight_status == "BLOCKED":
        return "BLOCKED"
    if preflight_status == "ALREADY_ACTIVE":
        return "ALREADY_ACTIVE"
    if operation["operationType"] == "IMPORT_MARKETPLACE" and preflight_status == "READY_FOR_APPROVAL":
        return "READY_FOR_MANUAL_IMPORT"
    if preflight_status == "READY_FOR_APPROVAL":
        return "READY_FOR_APPROVAL"
    return "REVIEW_REQUIRED"


def risk_level(operation: dict[str, Any]) -> str:
    if operation["operationType"] == "REPLACE_SOURCE_WITH_SYMLINK":
        return "HIGH"
    if operation["operationType"] == "ACTIVATE_RUNTIME_LINK":
        return "MEDIUM"
    return "LOW"


def approval_boundary(operation: dict[str, Any]) -> str:
    if operation["operationType"] == "REPLACE_SOURCE_WITH_SYMLINK":
        return "Requires an explicit user approval naming this replacement or approving its cleanup-ledger entry; PROPOSED status alone never authorizes writes."
    if operation["operationType"] == "IMPORT_MARKETPLACE":
        return "Requires explicit user action in the Codex app or an explicit instruction to open the import URL; this repo only records the import target."
    return "Requires explicit user approval after reviewing target contents, collision policy, and preflight reasons."


def rollback(operation: dict[str, Any], cleanup_entry: dict[str, Any] | None) -> str:
    if cleanup_entry:
        return cleanup_entry["rollback"]
    if operation["operationType"] == "IMPORT_MARKETPLACE":
        return "Remove or disable the imported marketplace/plugin entry through the runtime UI if activation is no longer wanted."
    return "Remove links created during activation and restore any backed-up runtime paths before rerunning preflight."


def next_action(states: Counter[str]) -> str:
    if states.get("BLOCKED", 0) > 0:
        return "Resolve BLOCKED approval packets before approving any runtime activation batch."
    if states.get("READY_FOR_APPROVAL", 0) > 0:
        return "Approve READY_FOR_APPROVAL packets by name only when the backup and symlink previews are acceptable; REVIEW_REQUIRED packets still need inspection."
    if states.get("READY_FOR_MANUAL_IMPORT", 0) > 0:
        return "Use the manual import packet only after deciding to expose the marketplace in the runtime."
    if states.get("REVIEW_REQUIRED", 0) > 0:
        return "Inspect REVIEW_REQUIRED runtime targets and update runtime-links.json only after the collision policy is clear."
    return "No activation approval action is pending."


def slug(value: str) -> str:
    chars = []
    for char in value.lower():
        if char.isalnum():
            chars.append(char)
        elif chars and chars[-1] != "-":
            chars.append("-")
    return "".join(chars).strip("-")


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Runtime Activation Approvals",
        "",
        "This generated queue packages dry-run activation operations for review. It does not authorize runtime mutation.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Source turnoff status | `{summary['sourceTurnoffStatus']}` |",
        f"| Total packets | {summary['totalPackets']} |",
        f"| Ready for approval | {summary['readyForApproval']} |",
        f"| Ready for manual import | {summary['readyForManualImport']} |",
        f"| Review required | {summary['reviewRequired']} |",
        f"| Blocked | {summary['blocked']} |",
        f"| Already active | {summary['alreadyActive']} |",
        f"| Runtime mutation allowed | `{str(summary['runtimeMutationAllowed']).lower()}` |",
        f"| Approval required | `{str(summary['approvalRequired']).lower()}` |",
        "",
        f"Next action: {summary['nextAction']}",
        "",
        "## Packets",
        "",
        "| State | Risk | Type | Name | Source | Target |",
        "|---|---|---|---|---|---|",
    ]
    for packet in report["packets"]:
        lines.append(
            f"| `{packet['approvalState']}` | `{packet['riskLevel']}` | `{packet['operationType']}` | "
            f"`{packet['name']}` | `{packet['sourcePath']}` | `{packet['targetPath']}` |"
        )
    lines.extend([
        "",
        "## Packet Details",
        "",
    ])
    for packet in report["packets"]:
        lines.extend([
            f"### `{packet['name']}`",
            "",
            f"- State: `{packet['approvalState']}`",
            f"- Plan status: `{packet['planStatus']}`",
            f"- Preflight status: `{packet['preflightStatus']}`",
            f"- Source kind: `{packet['sourceKind']}`",
            f"- Target kind: `{packet['targetKind']}`",
        ])
        if packet.get("backupPath"):
            lines.append(f"- Backup path: `{packet['backupPath']}`")
        if packet.get("backupKind"):
            lines.append(f"- Backup kind: `{packet['backupKind']}`")
        if packet.get("collisionPolicy"):
            lines.append(f"- Collision policy: `{packet['collisionPolicy']}`")
        if packet.get("runtimeLinkStrategy"):
            lines.append(f"- Runtime link strategy: `{packet['runtimeLinkStrategy']}`")
        if packet.get("primitiveTypes"):
            lines.append(f"- Primitive types: `{', '.join(packet['primitiveTypes'])}`")
        lines.extend([
            f"- Evidence: `{packet['evidence']['manifest']}`",
            f"- Approval boundary: {packet['approvalBoundary']}",
            f"- Rollback: {packet['rollback']}",
            "",
            "Preflight reasons:",
            "",
        ])
        for reason in packet["preflightReasons"]:
            lines.append(f"- {reason}")
        if packet.get("childCollisions"):
            lines.extend([
                "",
                "Child collisions:",
                "",
            ])
            for collision in packet["childCollisions"]:
                lines.append(
                    f"- `{collision['name']}` -> `{collision['targetPath']}` (`{collision['targetKind']}`)"
                )
        if packet.get("dependencyPackets"):
            lines.extend([
                "",
                "Dependency packets:",
                "",
            ])
            for dependency in packet["dependencyPackets"]:
                lines.append(
                    f"- `{dependency['name']}` targets `{dependency['targetPath']}`: {dependency['reason']}"
                )
        lines.extend([
            "",
            "Step preview:",
            "",
        ])
        for step in packet["stepPreviews"]:
            lines.append(f"1. {step['description']}: `{step['commandPreview']}`")
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
