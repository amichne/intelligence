#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build source-root retirement evidence from source graph review manifests."
    )
    parser.add_argument(
        "--source-roots",
        default="manifests/source-roots.json",
        help="Source roots manifest.",
    )
    parser.add_argument(
        "--discovered-primitives",
        default="manifests/discovered-primitives.json",
        help="Generated primitive inventory.",
    )
    parser.add_argument(
        "--source-review-decisions",
        default="manifests/source-review-decisions.json",
        help="Source review decision manifest.",
    )
    parser.add_argument(
        "--digest-review-decisions",
        default="manifests/digest-review-decisions.json",
        help="Digest review decision manifest.",
    )
    parser.add_argument(
        "--source-root-decisions",
        default="manifests/source-root-decisions.json",
        help="Source-root decision manifest.",
    )
    parser.add_argument(
        "--cleanup-ledger",
        default="manifests/cleanup-ledger.json",
        help="Cleanup ledger manifest.",
    )
    parser.add_argument(
        "--runtime-activation-approvals",
        default="manifests/runtime-activation-approvals.json",
        help="Generated runtime activation approval queue.",
    )
    parser.add_argument(
        "--out",
        default="manifests/source-root-retirement.json",
        help="Source-root retirement JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="docs/source-root-retirement.md",
        help="Markdown source-root retirement summary to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    source_roots = read_json((repo_root / args.source_roots).resolve())
    discovered = read_json((repo_root / args.discovered_primitives).resolve())
    source_review = read_json((repo_root / args.source_review_decisions).resolve())
    digest_review = read_json((repo_root / args.digest_review_decisions).resolve())
    source_root_decisions = read_json((repo_root / args.source_root_decisions).resolve())
    cleanup_ledger = read_json((repo_root / args.cleanup_ledger).resolve())
    approvals = read_json((repo_root / args.runtime_activation_approvals).resolve())
    report = build_report(
        source_roots,
        discovered,
        source_review,
        digest_review,
        source_root_decisions,
        cleanup_ledger,
        approvals,
        repo_root,
        args,
    )
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
    source_roots: dict[str, Any],
    discovered: dict[str, Any],
    source_review: dict[str, Any],
    digest_review: dict[str, Any],
    source_root_decisions: dict[str, Any],
    cleanup_ledger: dict[str, Any],
    approvals: dict[str, Any],
    repo_root: Path,
    args: argparse.Namespace,
) -> dict[str, Any]:
    observed = Counter(entry["sourceRoot"] for entry in discovered.get("entries", []))
    source_decisions = decision_counts(source_review, "sourceRoots")
    digest_decisions = decision_counts(digest_review, "sourceRoots")
    root_decisions = root_decision_counts(source_root_decisions)
    cleanup = cleanup_counts(cleanup_ledger)
    approval = approval_counts(source_roots, cleanup_ledger, approvals, repo_root)
    roots = []
    for root in source_roots.get("scanRoots", []):
        name = root["name"]
        entry = root_entry(
            root,
            observed.get(name, 0),
            source_decisions[name],
            digest_decisions[name],
            root_decisions[name],
            cleanup[name],
            approval[name],
            repo_root,
        )
        roots.append(entry)
    roots = sorted(roots, key=lambda item: (item["retirementState"], item["sourceRoot"]))
    states = Counter(entry["retirementState"] for entry in roots)
    return {
        "type": "SOURCE_ROOT_RETIREMENT_REPORT",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "SOURCE_ROOT_RETIREMENT_INPUTS",
            "sourceRoots": args.source_roots,
            "discoveredPrimitives": args.discovered_primitives,
            "sourceReviewDecisions": args.source_review_decisions,
            "digestReviewDecisions": args.digest_review_decisions,
            "sourceRootDecisions": args.source_root_decisions,
            "cleanupLedger": args.cleanup_ledger,
            "runtimeActivationApprovals": args.runtime_activation_approvals,
        },
        "summary": {
            "type": "SOURCE_ROOT_RETIREMENT_SUMMARY",
            "totalRoots": len(roots),
            "canonicalOwners": states.get("CANONICAL_OWNER", 0),
            "partialReplacementReady": states.get("PARTIAL_REPLACEMENT_READY", 0),
            "runtimeDependencyMapped": states.get("RUNTIME_DEPENDENCY_MAPPED", 0),
            "runtimeReviewRequired": states.get("RUNTIME_REVIEW_REQUIRED", 0),
            "coveredNoReplacementPlan": states.get("COVERED_NO_REPLACEMENT_PLAN", 0),
            "retainExternalOwners": states.get("RETAIN_EXTERNAL_OWNER", 0),
            "mixedRetainAndCovered": states.get("MIXED_RETAIN_AND_COVERED", 0),
            "cleanupRecorded": states.get("CLEANUP_RECORDED", 0),
            "emptySourceRoots": states.get("EMPTY_SOURCE_ROOT", 0),
            "noActionRecorded": states.get("NO_ACTION_RECORDED", 0),
            "totalObservedEntries": sum(entry["observedEntries"] for entry in roots),
            "nextAction": next_summary_action(states),
        },
        "roots": roots,
    }


def root_entry(
    root: dict[str, Any],
    observed: int,
    source_decisions: Counter[str],
    digest_decisions: Counter[str],
    root_decisions: Counter[str],
    cleanup: Counter[str],
    approval: Counter[str],
    repo_root: Path,
) -> dict[str, Any]:
    decision_summary = {
        "type": "SOURCE_ROOT_DECISION_SUMMARY",
        "sourceCoveredByCanonical": source_decisions.get("COVERED_BY_CANONICAL", 0),
        "sourceRetainedExternal": source_decisions.get("RETAIN_EXTERNAL", 0),
        "sourceKeepCanonical": source_decisions.get("KEEP_CANONICAL", 0),
        "digestCoveredByCanonical": digest_decisions.get("COVERED_BY_CANONICAL", 0),
        "digestRetainedExternal": digest_decisions.get("RETAIN_EXTERNAL", 0),
        "rootRetainedExternal": root_decisions.get("RETAIN_EXTERNAL_OWNER", 0),
        "rootPromoteCandidates": root_decisions.get("PROMOTE_CANDIDATES", 0),
        "rootReviewRequired": root_decisions.get("REVIEW_REQUIRED", 0),
    }
    cleanup_summary = {
        "type": "SOURCE_ROOT_CLEANUP_SUMMARY",
        "proposed": cleanup.get("PROPOSED", 0),
        "approved": cleanup.get("APPROVED", 0),
        "executed": cleanup.get("EXECUTED", 0),
    }
    approval_summary = {
        "type": "SOURCE_ROOT_APPROVAL_SUMMARY",
        "readyForApproval": approval.get("READY_FOR_APPROVAL", 0),
        "readyForManualImport": approval.get("READY_FOR_MANUAL_IMPORT", 0),
        "reviewRequired": approval.get("REVIEW_REQUIRED", 0),
        "dependencyMapped": approval.get("DEPENDENCY_MAPPED", 0),
        "blocked": approval.get("BLOCKED", 0),
        "alreadyActive": approval.get("ALREADY_ACTIVE", 0),
    }
    state = retirement_state(root, observed, decision_summary, cleanup_summary, approval_summary)
    entry = {
        "type": "SOURCE_ROOT_RETIREMENT_ENTRY",
        "sourceRoot": root["name"],
        "sourcePath": root["path"],
        "resolvedPath": str(expand_path(root["path"], repo_root)),
        "role": root["role"],
        "observedEntries": observed,
        "decisionSummary": decision_summary,
        "cleanupSummary": cleanup_summary,
        "approvalSummary": approval_summary,
        "retirementState": state,
        "nextAction": next_root_action(state),
        "evidence": evidence(root["name"], decision_summary, cleanup_summary, approval_summary),
    }
    return entry


def retirement_state(
    root: dict[str, Any],
    observed: int,
    decision: dict[str, int | str],
    cleanup: dict[str, int | str],
    approval: dict[str, int | str],
) -> str:
    if root["role"] == "canonical-candidate" and root["name"] == "intelligence":
        return "CANONICAL_OWNER"
    if decision["rootReviewRequired"] > 0:
        return "RUNTIME_REVIEW_REQUIRED"
    if approval["blocked"] > 0:
        return "RUNTIME_REVIEW_REQUIRED"
    if approval["reviewRequired"] > 0:
        if approval["dependencyMapped"] >= approval["reviewRequired"]:
            return "RUNTIME_DEPENDENCY_MAPPED"
        return "RUNTIME_REVIEW_REQUIRED"
    if approval["readyForApproval"] > 0 or cleanup["proposed"] > 0:
        return "PARTIAL_REPLACEMENT_READY"
    if observed == 0 and cleanup["executed"] > 0:
        return "CLEANUP_RECORDED"
    if observed == 0:
        return "EMPTY_SOURCE_ROOT"
    covered = (
        decision["sourceCoveredByCanonical"] +
        decision["digestCoveredByCanonical"] +
        decision["rootPromoteCandidates"]
    )
    retained = (
        decision["sourceRetainedExternal"] +
        decision["digestRetainedExternal"] +
        decision["rootRetainedExternal"]
    )
    if covered > 0 and retained > 0:
        return "MIXED_RETAIN_AND_COVERED"
    if covered > 0:
        return "COVERED_NO_REPLACEMENT_PLAN"
    if retained > 0:
        return "RETAIN_EXTERNAL_OWNER"
    if cleanup["executed"] > 0:
        return "CLEANUP_RECORDED"
    return "NO_ACTION_RECORDED"


def next_root_action(state: str) -> str:
    if state == "CANONICAL_OWNER":
        return "Keep this repository as the source of truth; expose primitives only through referential plugins and approved runtime links."
    if state == "PARTIAL_REPLACEMENT_READY":
        return "Review approval packets by name before changing cleanup-ledger status or executing any symlink replacement."
    if state == "RUNTIME_DEPENDENCY_MAPPED":
        return "Review and approve the mapped dependency packets before rerunning runtime activation preflight."
    if state == "RUNTIME_REVIEW_REQUIRED":
        return "Inspect runtime target contents and collision policy before approving runtime-link activation."
    if state == "MIXED_RETAIN_AND_COVERED":
        return "Retain external groups and create replacement plans only for covered canonical groups that have explicit approval evidence."
    if state == "COVERED_NO_REPLACEMENT_PLAN":
        return "Decide whether this covered source should stay as provenance or receive a cleanup-ledger replacement proposal."
    if state == "RETAIN_EXTERNAL_OWNER":
        return "Leave this source root under its current owner unless a future review promotes a canonical replacement."
    if state == "CLEANUP_RECORDED":
        return "Keep the cleanup ledger as the source of truth for completed cleanup and rerun inventory to detect drift."
    if state == "EMPTY_SOURCE_ROOT":
        return "Keep or remove the scan root only through a source-roots manifest review; no primitives are currently discovered."
    return "Review this source root manually; no generated trim or retention action currently covers it."


def next_summary_action(states: Counter[str]) -> str:
    if states.get("PARTIAL_REPLACEMENT_READY", 0) > 0:
        return "Review PARTIAL_REPLACEMENT_READY roots and their activation approval packets before any source turnoff execution."
    if states.get("RUNTIME_DEPENDENCY_MAPPED", 0) > 0:
        return "Review mapped dependency packets before rerunning runtime activation preflight."
    if states.get("RUNTIME_REVIEW_REQUIRED", 0) > 0:
        return "Resolve RUNTIME_REVIEW_REQUIRED roots before activating runtime links."
    if states.get("COVERED_NO_REPLACEMENT_PLAN", 0) > 0:
        return "Decide whether covered roots without replacement plans should remain provenance or receive cleanup-ledger proposals."
    return "No source-root retirement action is ready without additional review."


def evidence(
    source_root: str,
    decision: dict[str, int | str],
    cleanup: dict[str, int | str],
    approval: dict[str, int | str],
) -> list[str]:
    return [
        f"manifests/discovered-primitives.json records entries for sourceRoot {source_root}.",
        (
            "manifests/source-review-decisions.json and manifests/digest-review-decisions.json "
            f"record covered={decision['sourceCoveredByCanonical'] + decision['digestCoveredByCanonical'] + decision['rootPromoteCandidates']} "
            f"and retained={decision['sourceRetainedExternal'] + decision['digestRetainedExternal'] + decision['rootRetainedExternal']} review groups."
        ),
        (
            "manifests/cleanup-ledger.json and manifests/runtime-activation-approvals.json "
            f"record proposed={cleanup['proposed']}, executed={cleanup['executed']}, "
            f"readyForApproval={approval['readyForApproval']}, reviewRequired={approval['reviewRequired']}, "
            f"dependencyMapped={approval['dependencyMapped']}."
        ),
    ]


def decision_counts(manifest: dict[str, Any], root_key: str) -> dict[str, Counter[str]]:
    result: dict[str, Counter[str]] = defaultdict(Counter)
    for entry in manifest.get("entries", []):
        for source_root in entry.get("queueEvidence", {}).get(root_key, []):
            result[source_root][entry["decision"]] += 1
    return result


def root_decision_counts(manifest: dict[str, Any]) -> dict[str, Counter[str]]:
    result: dict[str, Counter[str]] = defaultdict(Counter)
    for entry in manifest.get("entries", []):
        result[entry["sourceRoot"]][entry["decision"]] += 1
    return result


def cleanup_counts(cleanup_ledger: dict[str, Any]) -> dict[str, Counter[str]]:
    result: dict[str, Counter[str]] = defaultdict(Counter)
    for entry in cleanup_ledger.get("entries", []):
        result[entry["sourceRoot"]][entry["status"]] += 1
    return result


def approval_counts(
    source_roots: dict[str, Any],
    cleanup_ledger: dict[str, Any],
    approvals: dict[str, Any],
    repo_root: Path,
) -> dict[str, Counter[str]]:
    result: dict[str, Counter[str]] = defaultdict(Counter)
    cleanup_root_by_operation = {}
    for entry in cleanup_ledger.get("entries", []):
        if entry.get("decision") != "REPLACE_WITH_SYMLINK":
            continue
        cleanup_root_by_operation[slug(f"replace-{entry['sourceRoot']}-{entry['sourcePath']}")] = entry["sourceRoot"]

    roots = [
        (root["name"], expand_path(root["path"], repo_root))
        for root in source_roots.get("scanRoots", [])
    ]

    for packet in approvals.get("packets", []):
        state = packet["approvalState"]
        source_root = cleanup_root_by_operation.get(packet["name"])
        if not source_root:
            source_root = matching_root(packet, roots)
        if source_root:
            result[source_root][state] += 1
            if state == "REVIEW_REQUIRED" and packet.get("dependencyPackets"):
                result[source_root]["DEPENDENCY_MAPPED"] += 1
    return result


def matching_root(packet: dict[str, Any], roots: list[tuple[str, Path]]) -> str | None:
    paths = [packet.get("targetPath", ""), packet.get("sourcePath", "")]
    for path_value in paths:
        if path_value.startswith("codex://"):
            continue
        path = Path(os.path.expanduser(path_value))
        for name, root in roots:
            if path == root:
                return name
        for name, root in roots:
            if path.resolve(strict=False) == root.resolve(strict=False) or is_relative_to(path, root):
                return name
    return None


def is_relative_to(path: Path, root: Path) -> bool:
    try:
        path.resolve(strict=False).relative_to(root.resolve(strict=False))
        return True
    except ValueError:
        return False


def expand_path(path_value: str, repo_root: Path) -> Path:
    expanded = os.path.expanduser(path_value)
    path = Path(expanded)
    if path.is_absolute():
        return path
    return (repo_root / path).resolve()


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
        "# Source Root Retirement",
        "",
        "This generated report summarizes which scanned roots can be retained, reviewed, or prepared for source turnoff. It does not authorize deletion or symlink writes.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Total roots | {summary['totalRoots']} |",
        f"| Total observed entries | {summary['totalObservedEntries']} |",
        f"| Canonical owners | {summary['canonicalOwners']} |",
        f"| Partial replacement ready | {summary['partialReplacementReady']} |",
        f"| Runtime dependency mapped | {summary['runtimeDependencyMapped']} |",
        f"| Runtime review required | {summary['runtimeReviewRequired']} |",
        f"| Covered, no replacement plan | {summary['coveredNoReplacementPlan']} |",
        f"| Mixed retain and covered | {summary['mixedRetainAndCovered']} |",
        f"| Retain external owners | {summary['retainExternalOwners']} |",
        f"| Cleanup recorded | {summary['cleanupRecorded']} |",
        f"| Empty source roots | {summary['emptySourceRoots']} |",
        f"| No action recorded | {summary['noActionRecorded']} |",
        "",
        f"Next action: {summary['nextAction']}",
        "",
        "## Roots",
        "",
        "| State | Root | Role | Entries | Covered | Retained | Proposed | Ready | Review | Mapped | Next Action |",
        "|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|",
    ]
    for root in report["roots"]:
        decision = root["decisionSummary"]
        cleanup = root["cleanupSummary"]
        approval = root["approvalSummary"]
        covered = (
            decision["sourceCoveredByCanonical"] +
            decision["digestCoveredByCanonical"] +
            decision["rootPromoteCandidates"]
        )
        retained = (
            decision["sourceRetainedExternal"] +
            decision["digestRetainedExternal"] +
            decision["rootRetainedExternal"]
        )
        lines.append(
            f"| `{root['retirementState']}` | `{root['sourceRoot']}` | `{root['role']}` | "
            f"{root['observedEntries']} | {covered} | {retained} | {cleanup['proposed']} | "
            f"{approval['readyForApproval']} | {approval['reviewRequired']} | {approval['dependencyMapped']} | {root['nextAction']} |"
        )
    lines.extend([
        "",
        "## Root Details",
        "",
    ])
    for root in report["roots"]:
        lines.extend([
            f"### `{root['sourceRoot']}`",
            "",
            f"- Path: `{root['sourcePath']}`",
            f"- Resolved path: `{root['resolvedPath']}`",
            f"- State: `{root['retirementState']}`",
            f"- Next action: {root['nextAction']}",
            "",
            "Evidence:",
            "",
        ])
        for item in root["evidence"]:
            lines.append(f"- {item}")
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
