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
        description="Build per-entry cleanup gap evidence for discovered primitives."
    )
    parser.add_argument(
        "--discovered-primitives",
        default="garden/manifests/discovered-primitives.json",
        help="Generated primitive inventory.",
    )
    parser.add_argument(
        "--source-review-decisions",
        default="garden/manifests/source-review-decisions.json",
        help="Source review decision manifest.",
    )
    parser.add_argument(
        "--digest-review-decisions",
        default="garden/manifests/digest-review-decisions.json",
        help="Digest review decision manifest.",
    )
    parser.add_argument(
        "--source-root-decisions",
        default="garden/manifests/source-root-decisions.json",
        help="Source root decision manifest.",
    )
    parser.add_argument(
        "--cleanup-ledger",
        default="garden/manifests/cleanup-ledger.json",
        help="Cleanup ledger manifest.",
    )
    parser.add_argument(
        "--out",
        default="garden/manifests/source-cleanup-gaps.json",
        help="Cleanup gap report JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="garden/docs/source-cleanup-gaps.md",
        help="Markdown cleanup gap report to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    discovered = read_json((repo_root / args.discovered_primitives).resolve())
    source_review = read_json((repo_root / args.source_review_decisions).resolve())
    digest_review = read_json((repo_root / args.digest_review_decisions).resolve())
    source_root_decisions = read_json((repo_root / args.source_root_decisions).resolve())
    cleanup_ledger = read_json((repo_root / args.cleanup_ledger).resolve())

    report = build_report(discovered, source_review, digest_review, source_root_decisions, cleanup_ledger, args)
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
    discovered: dict[str, Any],
    source_review: dict[str, Any],
    digest_review: dict[str, Any],
    source_root_decisions: dict[str, Any],
    cleanup_ledger: dict[str, Any],
    args: argparse.Namespace,
) -> dict[str, Any]:
    source_decisions = source_decision_map(source_review)
    digest_decisions = digest_decision_map(digest_review)
    root_decisions = {
        entry["sourceRoot"]: entry
        for entry in source_root_decisions.get("entries", [])
    }
    cleanup_entries = {
        cleanup_key(entry["sourceRoot"], entry["sourcePath"]): entry
        for entry in cleanup_ledger.get("entries", [])
    }
    cleanup_entry_list = cleanup_ledger.get("entries", [])
    entries = [
        cleanup_gap_entry(item, source_decisions, digest_decisions, root_decisions, cleanup_entries, cleanup_entry_list)
        for item in discovered.get("entries", [])
    ]
    entries = sorted(entries, key=lambda item: (item["cleanupState"], item["sourceRoot"], item["primitive"]["primitiveType"], item["primitive"]["name"], item["sourcePath"]))
    states = Counter(entry["cleanupState"] for entry in entries)
    return {
        "type": "SOURCE_CLEANUP_GAP_REPORT",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "SOURCE_CLEANUP_GAP_INPUTS",
            "discoveredPrimitives": args.discovered_primitives,
            "sourceReviewDecisions": args.source_review_decisions,
            "digestReviewDecisions": args.digest_review_decisions,
            "sourceRootDecisions": args.source_root_decisions,
            "cleanupLedger": args.cleanup_ledger,
        },
        "summary": {
            "type": "SOURCE_CLEANUP_GAP_SUMMARY",
            "totalEntries": len(entries),
            "canonicalOwner": states.get("CANONICAL_OWNER", 0),
            "cleanupLedgerEntry": states.get("CLEANUP_LEDGER_ENTRY", 0),
            "sourceRetained": states.get("SOURCE_RETAINED", 0),
            "digestRetained": states.get("DIGEST_RETAINED", 0),
            "rootRetained": states.get("ROOT_RETAINED", 0),
            "rootReviewRequired": states.get("ROOT_REVIEW_REQUIRED", 0),
            "needsCleanupProposal": states.get("NEEDS_CLEANUP_PROPOSAL", 0),
            "noCleanupAction": states.get("NO_CLEANUP_ACTION", 0),
            "nextAction": next_action(states),
        },
        "entries": entries,
    }


def cleanup_gap_entry(
    item: dict[str, Any],
    source_decisions: dict[str, dict[str, Any]],
    digest_decisions: dict[str, dict[str, Any]],
    root_decisions: dict[str, dict[str, Any]],
    cleanup_entries: dict[str, dict[str, Any]],
    cleanup_entry_list: list[dict[str, Any]],
) -> dict[str, Any]:
    cleanup_entry = cleanup_entry_for_item(item, cleanup_entries, cleanup_entry_list)
    source_decision = source_decisions.get(source_decision_key(item))
    digest_decision = digest_decisions.get(digest_decision_key(item))
    root_decision = root_decisions.get(item["sourceRoot"])
    state, reason, evidence = cleanup_state(item, cleanup_entry, source_decision, digest_decision, root_decision)
    return {
        "type": "SOURCE_CLEANUP_GAP_ENTRY",
        "sourceRoot": item["sourceRoot"],
        "sourcePath": item["path"],
        "observedPath": item["observedPath"],
        "primitive": {
            "type": "SOURCE_CLEANUP_PRIMITIVE",
            "primitiveType": item["type"],
            "name": item["name"],
        },
        "sha256": item["sha256"],
        "cleanupState": state,
        "reason": reason,
        "evidence": evidence,
    }


def cleanup_state(
    item: dict[str, Any],
    cleanup_entry: dict[str, Any] | None,
    source_decision: dict[str, Any] | None,
    digest_decision: dict[str, Any] | None,
    root_decision: dict[str, Any] | None,
) -> tuple[str, str, list[dict[str, Any]]]:
    if item.get("sourceRootRole") == "canonical-candidate":
        return (
            "CANONICAL_OWNER",
            "This entry is already in the canonical source root.",
            [source_root_evidence("CANONICAL_OWNER")],
        )
    if cleanup_entry:
        return (
            "CLEANUP_LEDGER_ENTRY",
            f"Cleanup ledger already records {cleanup_entry['decision']} with status {cleanup_entry['status']}.",
            [cleanup_evidence(cleanup_entry)],
        )
    if root_decision and root_decision["decision"] == "REVIEW_REQUIRED":
        return (
            "ROOT_REVIEW_REQUIRED",
            "Source-root decision still requires review before cleanup can be proposed.",
            [source_root_evidence(root_decision["decision"])],
        )
    if root_decision and root_decision["decision"] == "RETAIN_EXTERNAL_OWNER":
        return (
            "ROOT_RETAINED",
            "Source-root decision retains this root under its external owner.",
            [source_root_evidence(root_decision["decision"])],
        )
    if source_decision and source_decision["decision"] == "RETAIN_EXTERNAL":
        return (
            "SOURCE_RETAINED",
            "Source-review decision retains this primitive under its external owner.",
            [source_review_evidence(source_decision)],
        )
    if digest_decision and digest_decision["decision"] == "RETAIN_EXTERNAL":
        return (
            "DIGEST_RETAINED",
            "Digest-review decision retains this content under its external owner.",
            [digest_review_evidence(digest_decision)],
        )

    coverage_evidence = []
    if source_decision and source_decision["decision"] in {"COVERED_BY_CANONICAL", "KEEP_CANONICAL"}:
        coverage_evidence.append(source_review_evidence(source_decision))
    if digest_decision and digest_decision["decision"] == "COVERED_BY_CANONICAL":
        coverage_evidence.append(digest_review_evidence(digest_decision))
    if coverage_evidence:
        return (
            "NEEDS_CLEANUP_PROPOSAL",
            "Canonical coverage exists but no cleanup-ledger entry records retention, replacement, or removal.",
            coverage_evidence,
        )

    return (
        "NO_CLEANUP_ACTION",
        "No cleanup action is currently implied by review decisions.",
        [source_root_evidence(root_decision["decision"] if root_decision else "NO_SOURCE_ROOT_DECISION")],
    )


def source_decision_map(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    result = {}
    for decision in manifest.get("entries", []):
        target = decision["target"]
        evidence = decision.get("queueEvidence", {})
        for source_root in evidence.get("sourceRoots", []):
            for sha256 in evidence.get("sourceDigests", []):
                key = primitive_key(source_root, target["primitiveType"], target["name"], sha256)
                result[key] = decision
    return result


def digest_decision_map(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    result = {}
    for decision in manifest.get("entries", []):
        sha256 = decision["target"]["sha256"]
        for source_root in decision.get("queueEvidence", {}).get("sourceRoots", []):
            result[digest_key(source_root, sha256)] = decision
    return result


def source_decision_key(item: dict[str, Any]) -> str:
    return primitive_key(item["sourceRoot"], item["type"], item["name"], item["sha256"])


def digest_decision_key(item: dict[str, Any]) -> str:
    return digest_key(item["sourceRoot"], item["sha256"])


def primitive_key(source_root: str, primitive_type: str, name: str, sha256: str) -> str:
    return f"{source_root}\u0000{primitive_type}\u0000{name}\u0000{sha256}"


def digest_key(source_root: str, sha256: str) -> str:
    return f"{source_root}\u0000{sha256}"


def cleanup_key(source_root: str, source_path: str) -> str:
    return f"{source_root}\u0000{source_path}"


def cleanup_entry_for_item(
    item: dict[str, Any],
    cleanup_entries: dict[str, dict[str, Any]],
    cleanup_entry_list: list[dict[str, Any]],
) -> dict[str, Any] | None:
    direct = cleanup_entries.get(cleanup_key(item["sourceRoot"], item["path"]))
    if direct:
        return direct
    item_paths = [item.get("observedPath", ""), item.get("resolvedPath", "")]
    for cleanup_entry in cleanup_entry_list:
        cleanup_path = cleanup_entry.get("observedPath", "")
        if any(path_is_or_is_under(path_value, cleanup_path) for path_value in item_paths):
            return cleanup_entry
    return None


def path_is_or_is_under(path_value: str, root_value: str) -> bool:
    if not path_value or not root_value:
        return False
    path = Path(path_value).resolve(strict=False)
    root = Path(root_value).resolve(strict=False)
    return path == root or is_relative_to(path, root)


def is_relative_to(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def cleanup_evidence(entry: dict[str, Any]) -> dict[str, Any]:
    return {
        "type": "SOURCE_CLEANUP_GAP_EVIDENCE",
        "source": "cleanup-ledger",
        "decision": f"{entry['decision']}:{entry['status']}",
    }


def source_review_evidence(decision: dict[str, Any]) -> dict[str, Any]:
    evidence = {
        "type": "SOURCE_CLEANUP_GAP_EVIDENCE",
        "source": "source-review-decision",
        "decision": decision["decision"],
    }
    canonical_paths = canonical_paths_for(decision)
    if canonical_paths:
        evidence["canonicalPaths"] = canonical_paths
    return evidence


def digest_review_evidence(decision: dict[str, Any]) -> dict[str, Any]:
    evidence = {
        "type": "SOURCE_CLEANUP_GAP_EVIDENCE",
        "source": "digest-review-decision",
        "decision": decision["decision"],
    }
    canonical_paths = canonical_paths_for(decision)
    if canonical_paths:
        evidence["canonicalPaths"] = canonical_paths
    return evidence


def source_root_evidence(decision: str) -> dict[str, Any]:
    return {
        "type": "SOURCE_CLEANUP_GAP_EVIDENCE",
        "source": "source-root-decision",
        "decision": decision,
    }


def canonical_paths_for(decision: dict[str, Any]) -> list[str]:
    return sorted(coverage["path"] for coverage in decision.get("canonicalCoverage", []))


def next_action(states: Counter[str]) -> str:
    if states.get("NEEDS_CLEANUP_PROPOSAL", 0) > 0:
        return "Review NEEDS_CLEANUP_PROPOSAL entries and add cleanup-ledger proposals only for entries that should be replaced after approval."
    if states.get("ROOT_REVIEW_REQUIRED", 0) > 0:
        return "Resolve source-root review decisions before proposing cleanup."
    return "No generated cleanup proposal gaps remain; use cleanup-ledger approval packets for future source turnoff."


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Source Cleanup Gaps",
        "",
        "This generated report classifies every discovered primitive entry by cleanup coverage. It does not authorize deletion or symlink writes.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Total entries | {summary['totalEntries']} |",
        f"| Canonical owner | {summary['canonicalOwner']} |",
        f"| Cleanup ledger entry | {summary['cleanupLedgerEntry']} |",
        f"| Source retained | {summary['sourceRetained']} |",
        f"| Digest retained | {summary['digestRetained']} |",
        f"| Root retained | {summary['rootRetained']} |",
        f"| Root review required | {summary['rootReviewRequired']} |",
        f"| Needs cleanup proposal | {summary['needsCleanupProposal']} |",
        f"| No cleanup action | {summary['noCleanupAction']} |",
        "",
        f"Next action: {summary['nextAction']}",
        "",
        "## Entries Needing Cleanup Proposals",
        "",
        "| Root | Type | Name | Path | Coverage |",
        "|---|---|---|---|---|",
    ]
    gap_entries = [entry for entry in report["entries"] if entry["cleanupState"] == "NEEDS_CLEANUP_PROPOSAL"]
    if not gap_entries:
        lines.append("| - | - | - | - | - |")
    for entry in gap_entries:
        coverage = ", ".join(
            path
            for evidence in entry["evidence"]
            for path in evidence.get("canonicalPaths", [])
        ) or "-"
        lines.append(
            f"| `{entry['sourceRoot']}` | `{entry['primitive']['primitiveType']}` | "
            f"`{entry['primitive']['name']}` | `{entry['sourcePath']}` | `{coverage}` |"
        )
    lines.extend([
        "",
        "## State Counts",
        "",
        "| State | Count |",
        "|---|---:|",
    ])
    state_counts = Counter(entry["cleanupState"] for entry in report["entries"])
    for state, count in sorted(state_counts.items()):
        lines.append(f"| `{state}` | {count} |")
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
