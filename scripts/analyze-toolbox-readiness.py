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
        description="Build objective-level readiness evidence for the intelligence toolbox."
    )
    parser.add_argument(
        "--plugin-coverage",
        default="manifests/plugin-coverage.json",
        help="Generated plugin coverage report.",
    )
    parser.add_argument(
        "--review-completeness",
        default="manifests/review-completeness.json",
        help="Generated review completeness report.",
    )
    parser.add_argument(
        "--primitive-decision-coverage",
        default="manifests/primitive-decision-coverage.json",
        help="Generated primitive decision coverage report.",
    )
    parser.add_argument(
        "--source-cleanup-gaps",
        default="manifests/source-cleanup-gaps.json",
        help="Generated source cleanup gaps report.",
    )
    parser.add_argument(
        "--source-turnoff-readiness",
        default="manifests/source-turnoff-readiness.json",
        help="Generated source turnoff readiness report.",
    )
    parser.add_argument(
        "--runtime-activation-approvals",
        default="manifests/runtime-activation-approvals.json",
        help="Generated runtime activation approval queue.",
    )
    parser.add_argument(
        "--cleanup-ledger",
        default="manifests/cleanup-ledger.json",
        help="Cleanup ledger manifest.",
    )
    parser.add_argument(
        "--out",
        default="manifests/toolbox-readiness.json",
        help="Toolbox readiness JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="docs/toolbox-readiness.md",
        help="Markdown toolbox readiness summary to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    inputs = {
        "pluginCoverage": read_json((repo_root / args.plugin_coverage).resolve()),
        "reviewCompleteness": read_json((repo_root / args.review_completeness).resolve()),
        "primitiveDecisionCoverage": read_json((repo_root / args.primitive_decision_coverage).resolve()),
        "sourceCleanupGaps": read_json((repo_root / args.source_cleanup_gaps).resolve()),
        "sourceTurnoffReadiness": read_json((repo_root / args.source_turnoff_readiness).resolve()),
        "runtimeActivationApprovals": read_json((repo_root / args.runtime_activation_approvals).resolve()),
        "cleanupLedger": read_json((repo_root / args.cleanup_ledger).resolve()),
    }
    report = build_report(inputs, repo_root, args)
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


def build_report(inputs: dict[str, dict[str, Any]], repo_root: Path, args: argparse.Namespace) -> dict[str, Any]:
    requirements = [
        canonical_primitives_requirement(inputs),
        referential_plugins_requirement(inputs),
        hook_federation_requirement(inputs),
        independent_agents_requirement(inputs),
        comprehensive_review_requirement(inputs),
        schema_governance_requirement(repo_root),
        source_preservation_requirement(inputs, repo_root),
        runtime_activation_requirement(inputs),
    ]
    counts = Counter(entry["status"] for entry in requirements)
    completion_status = completion_status_for(counts)
    return {
        "type": "TOOLBOX_READINESS_REPORT",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "TOOLBOX_READINESS_INPUTS",
            "pluginCoverage": args.plugin_coverage,
            "reviewCompleteness": args.review_completeness,
            "primitiveDecisionCoverage": args.primitive_decision_coverage,
            "sourceCleanupGaps": args.source_cleanup_gaps,
            "sourceTurnoffReadiness": args.source_turnoff_readiness,
            "runtimeActivationApprovals": args.runtime_activation_approvals,
            "cleanupLedger": args.cleanup_ledger,
            "concordancePackage": "concordance/package.json",
            "localSchemaDirectory": "schemas/intelligence",
        },
        "summary": {
            "type": "TOOLBOX_READINESS_SUMMARY",
            "completionStatus": completion_status,
            "totalRequirements": len(requirements),
            "satisfied": counts.get("SATISFIED", 0),
            "needsApproval": counts.get("NEEDS_APPROVAL", 0),
            "needsReview": counts.get("NEEDS_REVIEW", 0),
            "incomplete": counts.get("INCOMPLETE", 0),
            "readyForCompletion": completion_status == "COMPLETE",
            "nextAction": next_action(completion_status, requirements),
        },
        "requirements": requirements,
    }


def canonical_primitives_requirement(inputs: dict[str, dict[str, Any]]) -> dict[str, Any]:
    plugin_summary = inputs["pluginCoverage"]["summary"]
    review_summary = inputs["reviewCompleteness"]["summary"]
    status = "SATISFIED" if review_summary["allCanonicalAudited"] and plugin_summary["totalPrimitives"] > 0 else "INCOMPLETE"
    return requirement(
        "canonical-primitive-set",
        status,
        [
            f"Canonical primitives: {plugin_summary['totalPrimitives']}.",
            f"Audited canonical primitives: {review_summary['audited']}/{review_summary['totalCanonical']}.",
            f"Needs audit: {review_summary['needsAudit']}.",
        ],
        "Keep adding primitive audits before treating any new canonical primitive as ready."
        if status != "SATISFIED"
        else "Maintain primitive audits alongside any new canonical primitive.",
    )


def referential_plugins_requirement(inputs: dict[str, dict[str, Any]]) -> dict[str, Any]:
    summary = inputs["pluginCoverage"]["summary"]
    plugin_count = count_by_name(summary["byType"], "PLUGIN")
    status = "SATISFIED" if summary["allCanonicalRouted"] and plugin_count > 0 else "INCOMPLETE"
    return requirement(
        "referential-plugins",
        status,
        [
            f"Referential plugins: {plugin_count}.",
            f"All canonical primitives routed: {str(summary['allCanonicalRouted']).lower()}.",
            f"Coverage statuses: {format_counts(summary['byStatus'])}.",
        ],
        "Route remaining standalone canonical primitives through plugins, marketplace entries, or scoped instructions."
        if status != "SATISFIED"
        else "Keep plugin manifests referential; do not copy primitive payloads into plugin folders.",
    )


def hook_federation_requirement(inputs: dict[str, dict[str, Any]]) -> dict[str, Any]:
    plugin_summary = inputs["pluginCoverage"]["summary"]
    approvals = inputs["runtimeActivationApprovals"]
    hook_count = count_by_name(plugin_summary["byType"], "HOOK")
    hook_packet = packet_by_name(approvals, "codex-hook-adapters")
    active = hook_packet is not None and hook_packet["approvalState"] == "ALREADY_ACTIVE"
    status = "SATISFIED" if hook_count > 0 and active else "NEEDS_APPROVAL"
    return requirement(
        "hook-federation",
        status,
        [
            f"Canonical hook primitives: {hook_count}.",
            f"Codex hook adapter packet: {hook_packet['approvalState'] if hook_packet else 'missing'}.",
        ],
        "Review and activate hook adapter runtime packets before relying on runtime hook federation."
        if status != "SATISFIED"
        else "Keep provider-neutral hook metadata separate from provider adapter files.",
    )


def independent_agents_requirement(inputs: dict[str, dict[str, Any]]) -> dict[str, Any]:
    plugin_summary = inputs["pluginCoverage"]["summary"]
    approvals = inputs["runtimeActivationApprovals"]
    agent_count = count_by_name(plugin_summary["byType"], "AGENT")
    claude_packet = packet_by_name(approvals, "claude-agent-children")
    active = claude_packet is not None and claude_packet["approvalState"] == "ALREADY_ACTIVE"
    status = "SATISFIED" if agent_count > 0 and active else "NEEDS_APPROVAL"
    return requirement(
        "independent-agent-profiles",
        status,
        [
            f"Canonical agent profiles: {agent_count}.",
            f"Claude agent runtime packet: {claude_packet['approvalState'] if claude_packet else 'missing'}.",
        ],
        "Review and activate agent runtime packets before relying on runtime agent profile exposure."
        if status != "SATISFIED"
        else "Keep agent profiles in agents/ and compose them into plugins by reference.",
    )


def comprehensive_review_requirement(inputs: dict[str, dict[str, Any]]) -> dict[str, Any]:
    primitive_summary = inputs["primitiveDecisionCoverage"]["summary"]
    turnoff_summary = inputs["sourceTurnoffReadiness"]["summary"]
    cleanup_summary = inputs["sourceCleanupGaps"]["summary"]
    status = "SATISFIED"
    if not primitive_summary["allEntriesCovered"] or cleanup_summary["needsCleanupProposal"] != 0:
        status = "INCOMPLETE"
    elif turnoff_summary["sourceReviewOpen"] or turnoff_summary["digestReviewOpen"] or turnoff_summary["reviewCompletenessOpen"]:
        status = "NEEDS_REVIEW"
    return requirement(
        "comprehensive-source-review",
        status,
        [
            f"Discovered primitive entries: {primitive_summary['totalEntries']}.",
            f"Unreviewed singleton entries: {primitive_summary['unreviewedSingleton']}.",
            f"Cleanup proposal gaps: {cleanup_summary['needsCleanupProposal']}.",
            f"Open source review groups: {turnoff_summary['sourceReviewOpen']}.",
            f"Open digest review groups: {turnoff_summary['digestReviewOpen']}.",
        ],
        "Resolve unreviewed entries, cleanup proposal gaps, or open review groups."
        if status != "SATISFIED"
        else "Continue using source-review, digest-review, and source-root decisions for new scan roots.",
    )


def schema_governance_requirement(repo_root: Path) -> dict[str, Any]:
    concordance_package = repo_root / "concordance" / "package.json"
    local_schema_dir = repo_root / "schemas" / "intelligence"
    status = "SATISFIED" if concordance_package.exists() and local_schema_dir.is_dir() else "INCOMPLETE"
    return requirement(
        "schema-driven-structured-data",
        status,
        [
            f"Concordance schema package present: {str(concordance_package.exists()).lower()}.",
            f"Repository manifest schema directory present: {str(local_schema_dir.is_dir()).lower()}.",
            "validate-manifests.mjs rejects JSON files without a schema validation path.",
        ],
        "Restore the Concordance schema reference and local manifest schemas."
        if status != "SATISFIED"
        else "Keep every persisted structured-data change on a schema-backed validation path.",
    )


def source_preservation_requirement(inputs: dict[str, dict[str, Any]], repo_root: Path) -> dict[str, Any]:
    entries = inputs["cleanupLedger"].get("entries", [])
    executed_deletes = [entry for entry in entries if entry["decision"] == "DELETE_ORIGINAL" and entry["status"] == "EXECUTED"]
    executed_replacements = [
        entry for entry in entries
        if entry["decision"] == "REPLACE_WITH_SYMLINK" and entry["status"] == "EXECUTED"
    ]
    missing_backups = [
        backup_path(repo_root, entry)
        for entry in executed_replacements
        if not backup_path(repo_root, entry).exists()
    ]
    status = "SATISFIED" if not executed_deletes and not missing_backups else "INCOMPLETE"
    return requirement(
        "preserve-original-sources",
        status,
        [
            f"Executed source delete entries: {len(executed_deletes)}.",
            f"Executed symlink replacements: {len(executed_replacements)}.",
            f"Missing replacement backups: {len(missing_backups)}.",
            f"Executed broken symlink removals: {count_cleanup(entries, 'REMOVE_BROKEN_SYMLINK', 'EXECUTED')}.",
        ],
        "Restore missing backups or stop any source delete path before continuing cleanup."
        if status != "SATISFIED"
        else "Continue preserving replaced originals under .migration-backups/source-turnoff/.",
    )


def runtime_activation_requirement(inputs: dict[str, dict[str, Any]]) -> dict[str, Any]:
    summary = inputs["runtimeActivationApprovals"]["summary"]
    if summary["reviewRequired"] > 0:
        status = "NEEDS_REVIEW"
        next_step = "Inspect REVIEW_REQUIRED runtime activation packets and update runtime-links.json only after the target mutation policy is clear."
    elif summary["readyForApproval"] > 0 or summary["readyForManualImport"] > 0:
        status = "NEEDS_APPROVAL"
        next_step = summary["nextAction"]
    else:
        status = "SATISFIED"
        next_step = summary["nextAction"]
    return requirement(
        "runtime-activation",
        status,
        [
            f"Already active packets: {summary['alreadyActive']}.",
            f"Ready for approval packets: {summary['readyForApproval']}.",
            f"Ready for manual import packets: {summary['readyForManualImport']}.",
            f"Review-required packets: {summary['reviewRequired']}.",
            f"Blocked packets: {summary['blocked']}.",
        ],
        next_step,
    )


def requirement(requirement_id: str, status: str, evidence: list[str], next_action: str) -> dict[str, Any]:
    return {
        "type": "TOOLBOX_READINESS_REQUIREMENT",
        "id": requirement_id,
        "status": status,
        "evidence": evidence,
        "nextAction": next_action,
    }


def completion_status_for(counts: Counter[str]) -> str:
    if counts.get("INCOMPLETE", 0):
        return "INCOMPLETE"
    if counts.get("NEEDS_REVIEW", 0):
        return "REVIEW_READY"
    if counts.get("NEEDS_APPROVAL", 0):
        return "APPROVAL_READY"
    return "COMPLETE"


def next_action(completion_status: str, requirements: list[dict[str, Any]]) -> str:
    if completion_status == "COMPLETE":
        return "All objective-level requirements are satisfied by current generated evidence."
    for state in ["INCOMPLETE", "NEEDS_REVIEW", "NEEDS_APPROVAL"]:
        for item in requirements:
            if item["status"] == state:
                return item["nextAction"]
    return "Inspect requirement entries for the next action."


def count_by_name(items: list[dict[str, Any]], name: str) -> int:
    for item in items:
        if item["name"] == name:
            return item["count"]
    return 0


def format_counts(items: list[dict[str, Any]]) -> str:
    return ", ".join(f"{item['name']}={item['count']}" for item in items)


def packet_by_name(report: dict[str, Any], name: str) -> dict[str, Any] | None:
    for packet in report.get("packets", []):
        if packet["name"] == name:
            return packet
    return None


def count_cleanup(entries: list[dict[str, Any]], decision: str, status: str) -> int:
    return sum(1 for entry in entries if entry["decision"] == decision and entry["status"] == status)


def backup_path(repo_root: Path, entry: dict[str, Any]) -> Path:
    return repo_root / ".migration-backups" / "source-turnoff" / entry["sourceRoot"] / entry["sourcePath"]


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Toolbox Readiness",
        "",
        "This generated report maps the original toolbox objective to current manifest evidence. It does not authorize runtime mutation or cleanup.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Completion status | `{summary['completionStatus']}` |",
        f"| Total requirements | {summary['totalRequirements']} |",
        f"| Satisfied | {summary['satisfied']} |",
        f"| Needs approval | {summary['needsApproval']} |",
        f"| Needs review | {summary['needsReview']} |",
        f"| Incomplete | {summary['incomplete']} |",
        f"| Ready for completion | `{str(summary['readyForCompletion']).lower()}` |",
        "",
        f"Next action: {summary['nextAction']}",
        "",
        "## Requirements",
        "",
        "| Status | Requirement | Evidence | Next Action |",
        "|---|---|---|---|",
    ]
    for item in report["requirements"]:
        lines.append(
            f"| `{item['status']}` | `{item['id']}` | {join_evidence(item['evidence'])} | {item['nextAction']} |"
        )
    lines.append("")
    return "\n".join(lines)


def join_evidence(items: list[str]) -> str:
    return "<br>".join(items)


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
