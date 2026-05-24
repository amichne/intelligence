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
        description="Build source turnoff readiness evidence from source graph manifests."
    )
    parser.add_argument(
        "--plugin-coverage",
        default="manifests/plugin-coverage.json",
        help="Generated plugin coverage report.",
    )
    parser.add_argument(
        "--review-completeness",
        default="manifests/review-completeness.json",
        help="Generated canonical primitive review-completeness report.",
    )
    parser.add_argument(
        "--source-review-decisions",
        default="manifests/source-review-decisions.json",
        help="Source review decisions manifest.",
    )
    parser.add_argument(
        "--digest-review-decisions",
        default="manifests/digest-review-decisions.json",
        help="Digest review decisions manifest.",
    )
    parser.add_argument(
        "--cleanup-ledger",
        default="manifests/cleanup-ledger.json",
        help="Cleanup ledger manifest.",
    )
    parser.add_argument(
        "--runtime-links",
        default="manifests/runtime-links.json",
        help="Runtime link plan manifest.",
    )
    parser.add_argument(
        "--out",
        default="manifests/source-turnoff-readiness.json",
        help="Readiness JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="docs/source-turnoff-readiness.md",
        help="Markdown readiness summary to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    plugin_coverage = read_json((repo_root / args.plugin_coverage).resolve())
    review_completeness = read_json((repo_root / args.review_completeness).resolve())
    source_review = read_json((repo_root / args.source_review_decisions).resolve())
    digest_review = read_json((repo_root / args.digest_review_decisions).resolve())
    cleanup_ledger = read_json((repo_root / args.cleanup_ledger).resolve())
    runtime_links = read_json((repo_root / args.runtime_links).resolve())
    report = build_report(
        plugin_coverage,
        review_completeness,
        source_review,
        digest_review,
        cleanup_ledger,
        runtime_links,
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
    plugin_coverage: dict[str, Any],
    review_completeness: dict[str, Any],
    source_review: dict[str, Any],
    digest_review: dict[str, Any],
    cleanup_ledger: dict[str, Any],
    runtime_links: dict[str, Any],
    args: argparse.Namespace,
) -> dict[str, Any]:
    all_routed = plugin_coverage["summary"]["allCanonicalRouted"]
    review_open = review_completeness["summary"]["needsAudit"]
    source_open = count_status_not(source_review, "DECIDED")
    digest_open = count_status_not(digest_review, "DECIDED")
    cleanup_status = Counter(entry["status"] for entry in cleanup_ledger.get("entries", []))
    cleanup_decision = Counter(entry["decision"] for entry in cleanup_ledger.get("entries", []))
    source_retained = count_decision(source_review, "RETAIN_EXTERNAL")
    digest_retained = count_decision(digest_review, "RETAIN_EXTERNAL")
    runtime_approval = runtime_links_requiring_approval(runtime_links, Path.cwd().resolve())
    proposed = cleanup_status.get("PROPOSED", 0)
    approved = cleanup_status.get("APPROVED", 0)
    executed = cleanup_status.get("EXECUTED", 0)
    readiness = readiness_status(all_routed, review_open, source_open, digest_open, proposed, approved)
    can_mutate = readiness == "APPROVED_FOR_EXECUTION" and runtime_approval == 0
    summary = {
        "type": "SOURCE_TURNOFF_READINESS_SUMMARY",
        "readinessStatus": readiness,
        "allCanonicalRouted": all_routed,
        "reviewCompletenessOpen": review_open,
        "sourceReviewOpen": source_open,
        "digestReviewOpen": digest_open,
        "proposedReplacements": proposed,
        "approvedReplacements": approved,
        "executedCleanupEntries": executed,
        "retainedExternalGroups": source_retained + digest_retained,
        "runtimeLinksRequiringApproval": runtime_approval,
        "canMutateRuntimeWithoutApproval": can_mutate,
        "nextAction": next_action(readiness, proposed, runtime_approval),
    }
    return {
        "type": "SOURCE_TURNOFF_READINESS_REPORT",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "SOURCE_TURNOFF_READINESS_INPUTS",
            "pluginCoverage": args.plugin_coverage,
            "reviewCompleteness": args.review_completeness,
            "sourceReviewDecisions": args.source_review_decisions,
            "digestReviewDecisions": args.digest_review_decisions,
            "cleanupLedger": args.cleanup_ledger,
            "runtimeLinks": args.runtime_links,
        },
        "summary": summary,
        "gates": gates(
            all_routed,
            review_open,
            source_open,
            digest_open,
            proposed,
            approved,
            cleanup_decision,
            source_retained + digest_retained,
            runtime_approval,
        ),
        "proposedReplacements": proposed_replacements(cleanup_ledger),
    }


def count_status_not(manifest: dict[str, Any], status: str) -> int:
    return sum(1 for entry in manifest.get("entries", []) if entry.get("status") != status)


def count_decision(manifest: dict[str, Any], decision: str) -> int:
    return sum(1 for entry in manifest.get("entries", []) if entry.get("decision") == decision)


def readiness_status(
    all_routed: bool,
    review_open: int,
    source_open: int,
    digest_open: int,
    proposed: int,
    approved: int,
) -> str:
    if not all_routed or review_open > 0 or source_open > 0 or digest_open > 0:
        return "NOT_READY"
    if approved > 0 and proposed == 0:
        return "APPROVED_FOR_EXECUTION"
    return "REVIEW_READY"


def next_action(readiness: str, proposed: int, runtime_approval: int) -> str:
    if readiness == "NOT_READY":
        return "Resolve blocked coverage, audit-completeness, or review-decision gates before planning runtime changes."
    if readiness == "APPROVED_FOR_EXECUTION":
        return "Execute only the approved ledger entries and record the result in cleanup-ledger.json."
    if proposed > 0:
        return "Review proposed cleanup-ledger replacements; do not mutate runtime paths until explicitly approved."
    if runtime_approval > 0:
        return "Review remaining inactive runtime-link or marketplace-import entries that still require explicit approval."
    return "No runtime mutation is approved; keep monitoring source graph drift."


def gates(
    all_routed: bool,
    review_open: int,
    source_open: int,
    digest_open: int,
    proposed: int,
    approved: int,
    cleanup_decision: Counter[str],
    retained_external: int,
    runtime_approval: int,
) -> list[dict[str, str]]:
    return [
        {
            "type": "SOURCE_TURNOFF_GATE",
            "name": "canonical-plugin-coverage",
            "status": "PASS" if all_routed else "BLOCKED",
            "evidence": "manifests/plugin-coverage.json reports all canonical primitives routed."
            if all_routed else
            "manifests/plugin-coverage.json has STANDALONE_ONLY canonical primitives.",
            "nextAction": "Keep plugin coverage generated and checked before source replacement.",
        },
        {
            "type": "SOURCE_TURNOFF_GATE",
            "name": "review-completeness",
            "status": "PASS" if review_open == 0 else "BLOCKED",
            "evidence": f"{review_open} canonical primitives still need audit decisions.",
            "nextAction": "Close review-completeness gaps before treating the source graph as fully reviewed.",
        },
        {
            "type": "SOURCE_TURNOFF_GATE",
            "name": "source-review-decisions",
            "status": "PASS" if source_open == 0 else "BLOCKED",
            "evidence": f"{source_open} source-review entries remain open.",
            "nextAction": "Keep every generated name-review group synchronized with source-review decisions.",
        },
        {
            "type": "SOURCE_TURNOFF_GATE",
            "name": "digest-review-decisions",
            "status": "PASS" if digest_open == 0 else "BLOCKED",
            "evidence": f"{digest_open} digest-review entries remain open.",
            "nextAction": "Keep every generated duplicate-content group synchronized with digest-review decisions.",
        },
        {
            "type": "SOURCE_TURNOFF_GATE",
            "name": "cleanup-ledger-approval",
            "status": "REVIEW_REQUIRED" if proposed > 0 else ("PASS" if approved == 0 else "REVIEW_REQUIRED"),
            "evidence": f"{proposed} proposed replacements and {approved} approved replacements are recorded.",
            "nextAction": "Treat PROPOSED entries as review records only; execute nothing until explicit approval changes status.",
        },
        {
            "type": "SOURCE_TURNOFF_GATE",
            "name": "external-retention",
            "status": "PASS",
            "evidence": f"{retained_external} generated review groups are intentionally retained in external owners.",
            "nextAction": "Do not replace RETAIN_EXTERNAL groups from this repository unless a new canonical promotion is reviewed.",
        },
        {
            "type": "SOURCE_TURNOFF_GATE",
            "name": "runtime-activation-approval",
            "status": "REVIEW_REQUIRED" if runtime_approval > 0 else "PASS",
            "evidence": f"{runtime_approval} inactive runtime link or marketplace import plans still require explicit approval.",
            "nextAction": "Use runtime-links.json as activation planning evidence, not as permission to write runtime paths.",
        },
    ]


def proposed_replacements(cleanup_ledger: dict[str, Any]) -> list[dict[str, str]]:
    proposals = []
    for entry in cleanup_ledger.get("entries", []):
        if entry.get("status") != "PROPOSED":
            continue
        proposals.append(
            {
                "type": "SOURCE_REPLACEMENT_PROPOSAL",
                "sourceRoot": entry["sourceRoot"],
                "sourcePath": entry["sourcePath"],
                "canonicalPath": entry["canonicalPath"],
                "decision": entry["decision"],
                "status": entry["status"],
                "reviewEvidenceSha256": review_evidence_sha256(entry["reviewEvidence"]),
            }
        )
    return sorted(
        proposals,
        key=lambda item: (item["sourceRoot"], item["sourcePath"], item["canonicalPath"]),
    )


def review_evidence_sha256(evidence: dict[str, Any]) -> str:
    if evidence["type"] == "SOURCE_REVIEW_EVIDENCE":
        return evidence["sourceSha256"]
    return evidence["targetSha256"]


def runtime_links_requiring_approval(runtime_links: dict[str, Any], repo_root: Path) -> int:
    return sum(
        1
        for entry in runtime_links.get("entries", [])
        if entry.get("requiresApproval") and not runtime_link_already_active(entry, repo_root)
    )


def runtime_link_already_active(entry: dict[str, Any], repo_root: Path) -> bool:
    strategy = entry.get("strategy")
    if strategy == "MARKETPLACE_IMPORT":
        return codex_marketplace_already_configured(entry, repo_root)
    source_path = resolve_runtime_path(entry["sourcePath"], repo_root)
    target_path = resolve_runtime_path(entry["targetPath"], repo_root)
    if strategy == "SYMLINK_CHILDREN":
        return runtime_children_already_active(entry, source_path, target_path)
    if target_path.is_symlink():
        try:
            return target_path.resolve(strict=False) == source_path.resolve(strict=False)
        except OSError:
            return False
    return False


def runtime_children_already_active(entry: dict[str, Any], source_path: Path, target_path: Path) -> bool:
    if not source_path.is_dir() or not target_path.is_dir():
        return False
    source_children = runtime_child_sources(entry, source_path)
    if not source_children:
        return False
    for source_child in source_children:
        target_child = target_path / source_child.name
        if not target_child.is_symlink():
            return False
        try:
            if target_child.resolve(strict=False) != source_child.resolve(strict=False):
                return False
        except OSError:
            return False
    return True


def runtime_child_sources(entry: dict[str, Any], source_path: Path) -> list[Path]:
    primitive_types = set(entry.get("primitiveTypes", []))
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


def resolve_runtime_path(path_value: str, repo_root: Path) -> Path:
    expanded = os.path.expanduser(path_value)
    path = Path(expanded)
    if path.is_absolute():
        return path
    return (repo_root / path).resolve(strict=False)


def codex_marketplace_already_configured(entry: dict[str, Any], repo_root: Path) -> bool:
    config_path = Path(os.path.expanduser(os.environ.get("CODEX_HOME", "~/.codex"))) / "config.toml"
    if not config_path.is_file():
        return False
    marketplace_root = resolve_runtime_path(entry["sourcePath"], repo_root).parent
    try:
        config_text = config_path.read_text(encoding="utf-8")
    except OSError:
        return False
    return f'source = "{marketplace_root}"' in config_text


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Source Turnoff Readiness",
        "",
        "This generated report joins plugin coverage, review decisions, cleanup ledger entries, and runtime-link plans.",
        "",
        f"Readiness status: `{summary['readinessStatus']}`",
        f"Can mutate runtime without approval: `{str(summary['canMutateRuntimeWithoutApproval']).lower()}`",
        f"Next action: {summary['nextAction']}",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| All canonical routed | `{str(summary['allCanonicalRouted']).lower()}` |",
        f"| Review completeness open | {summary['reviewCompletenessOpen']} |",
        f"| Source review open | {summary['sourceReviewOpen']} |",
        f"| Digest review open | {summary['digestReviewOpen']} |",
        f"| Proposed replacements | {summary['proposedReplacements']} |",
        f"| Approved replacements | {summary['approvedReplacements']} |",
        f"| Executed cleanup entries | {summary['executedCleanupEntries']} |",
        f"| Retained external groups | {summary['retainedExternalGroups']} |",
        f"| Runtime links requiring approval | {summary['runtimeLinksRequiringApproval']} |",
        "",
        "## Gates",
        "",
        "| Gate | Status | Evidence | Next Action |",
        "|---|---|---|---|",
    ]
    for gate in report["gates"]:
        lines.append(
            f"| `{gate['name']}` | `{gate['status']}` | {gate['evidence']} | {gate['nextAction']} |"
        )
    lines.extend([
        "",
        "## Proposed Replacements",
        "",
        "These entries are review records only. They do not authorize deletion or symlink writes.",
        "",
        "| Source | Path | Canonical | Evidence |",
        "|---|---|---|---|",
    ])
    for item in report["proposedReplacements"]:
        lines.append(
            f"| `{item['sourceRoot']}` | `{item['sourcePath']}` | "
            f"`{item['canonicalPath']}` | `{item['reviewEvidenceSha256']}` |"
        )
    if not report["proposedReplacements"]:
        lines.append("| - | - | - | - |")
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
