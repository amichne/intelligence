#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build per-entry decision coverage evidence for discovered primitives."
    )
    parser.add_argument(
        "--discovered-primitives",
        default="garden/manifests/discovered-primitives.json",
        help="Generated primitive inventory.",
    )
    parser.add_argument(
        "--consolidation-report",
        default="garden/manifests/consolidation-report.json",
        help="Generated consolidation report.",
    )
    parser.add_argument(
        "--source-review-decisions",
        default="garden/manifests/source-review-decisions.json",
        help="Source review decisions manifest.",
    )
    parser.add_argument(
        "--digest-review-decisions",
        default="garden/manifests/digest-review-decisions.json",
        help="Digest review decisions manifest.",
    )
    parser.add_argument(
        "--source-root-decisions",
        default="garden/manifests/source-root-decisions.json",
        help="Source-root decisions manifest.",
    )
    parser.add_argument(
        "--out",
        default="garden/manifests/primitive-decision-coverage.json",
        help="Primitive decision coverage JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="garden/docs/primitive-decision-coverage.md",
        help="Markdown primitive decision coverage summary to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    discovered = read_json((repo_root / args.discovered_primitives).resolve())
    consolidation = read_json((repo_root / args.consolidation_report).resolve())
    source_review = read_json((repo_root / args.source_review_decisions).resolve())
    digest_review = read_json((repo_root / args.digest_review_decisions).resolve())
    source_root_decisions = read_json((repo_root / args.source_root_decisions).resolve())
    report = build_report(discovered, consolidation, source_review, digest_review, source_root_decisions, repo_root, args)
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
    consolidation: dict[str, Any],
    source_review: dict[str, Any],
    digest_review: dict[str, Any],
    source_root_decisions: dict[str, Any],
    repo_root: Path,
    args: argparse.Namespace,
) -> dict[str, Any]:
    source_review_keys = source_review_entry_keys(consolidation, source_review)
    digest_review_shas = {entry["target"]["sha256"] for entry in digest_review.get("entries", [])}
    source_root_decision_roots = {entry["sourceRoot"] for entry in source_root_decisions.get("entries", [])}
    entries = [
        coverage_entry(item, source_review_keys, digest_review_shas, source_root_decision_roots, repo_root)
        for item in discovered.get("entries", [])
    ]
    entries = sorted(entries, key=lambda item: (
        item["coverageState"],
        item["sourceRoot"],
        item["primitive"]["primitiveType"],
        item["primitive"]["name"],
        item["path"],
    ))
    states = Counter(entry["coverageState"] for entry in entries)
    unreviewed = states.get("UNREVIEWED_SINGLETON", 0)
    return {
        "type": "PRIMITIVE_DECISION_COVERAGE_REPORT",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "PRIMITIVE_DECISION_COVERAGE_INPUTS",
            "discoveredPrimitives": args.discovered_primitives,
            "consolidationReport": args.consolidation_report,
            "sourceReviewDecisions": args.source_review_decisions,
            "digestReviewDecisions": args.digest_review_decisions,
            "sourceRootDecisions": args.source_root_decisions,
        },
        "summary": {
            "type": "PRIMITIVE_DECISION_COVERAGE_SUMMARY",
            "totalEntries": len(entries),
            "canonicalOwner": states.get("CANONICAL_OWNER", 0),
            "canonicalRuntimeAlias": states.get("CANONICAL_RUNTIME_ALIAS", 0),
            "sourceReviewDecision": states.get("SOURCE_REVIEW_DECISION", 0),
            "digestReviewDecision": states.get("DIGEST_REVIEW_DECISION", 0),
            "sourceRootDecision": states.get("SOURCE_ROOT_DECISION", 0),
            "unreviewedSingleton": unreviewed,
            "allEntriesCovered": unreviewed == 0,
            "nextAction": next_action(unreviewed),
        },
        "entries": entries,
    }


def source_review_entry_keys(
    consolidation: dict[str, Any],
    source_review: dict[str, Any],
) -> set[tuple[str, str, str, str]]:
    decided = {
        (entry["target"]["primitiveType"], entry["target"]["name"])
        for entry in source_review.get("entries", [])
        if entry.get("status") == "DECIDED"
    }
    result = set()
    for queue_item in consolidation.get("nameReviewQueue", []):
        if (queue_item["type"], queue_item["name"]) not in decided:
            continue
        for entry in queue_item.get("entries", []):
            result.add((entry["sourceRoot"], entry["type"], entry["name"], entry["path"]))
    return result


def coverage_entry(
    item: dict[str, Any],
    source_review_keys: set[tuple[str, str, str, str]],
    digest_review_shas: set[str],
    source_root_decision_roots: set[str],
    repo_root: Path,
) -> dict[str, Any]:
    state, evidence = coverage_state(item, source_review_keys, digest_review_shas, source_root_decision_roots, repo_root)
    return {
        "type": "PRIMITIVE_DECISION_COVERAGE_ENTRY",
        "primitive": {
            "type": "PRIMITIVE_REFERENCE",
            "primitiveType": item["type"],
            "name": item["name"],
        },
        "sourceRoot": item["sourceRoot"],
        "sourceRootRole": item["sourceRootRole"],
        "path": item["path"],
        "sha256": item["sha256"],
        "coverageState": state,
        "evidence": evidence,
    }


def coverage_state(
    item: dict[str, Any],
    source_review_keys: set[tuple[str, str, str, str]],
    digest_review_shas: set[str],
    source_root_decision_roots: set[str],
    repo_root: Path,
) -> tuple[str, str]:
    key = (item["sourceRoot"], item["type"], item["name"], item["path"])
    if item["sourceRootRole"] == "canonical-candidate":
        return "CANONICAL_OWNER", "Entry is inside the canonical candidate source root."
    if is_canonical_runtime_alias(item, repo_root):
        return "CANONICAL_RUNTIME_ALIAS", "Entry is a runtime alias resolving to a canonical path in this repository."
    if key in source_review_keys:
        return "SOURCE_REVIEW_DECISION", "Entry is part of a decided generated duplicate-name review group."
    if item["sha256"] in digest_review_shas:
        return "DIGEST_REVIEW_DECISION", "Entry content digest is part of a decided generated duplicate-content review group."
    if item["sourceRoot"] in source_root_decision_roots:
        return "SOURCE_ROOT_DECISION", "Entry is covered by a hand-authored source-root decision."
    return "UNREVIEWED_SINGLETON", "Entry is not yet covered by canonical ownership, a duplicate review decision, or a source-root decision."


def is_canonical_runtime_alias(item: dict[str, Any], repo_root: Path) -> bool:
    if item.get("sourceRootRole") != "runtime-source":
        return False
    resolved = item.get("resolvedPath")
    if not resolved:
        return False
    try:
        Path(resolved).resolve(strict=False).relative_to(repo_root)
    except ValueError:
        return False
    return True


def next_action(unreviewed: int) -> str:
    if unreviewed:
        return "Review UNREVIEWED_SINGLETON entries by promoting candidates, adding source-root decisions, or recording explicit retain decisions."
    return "All discovered primitive entries have a decision path."


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Primitive Decision Coverage",
        "",
        "This generated report checks whether each discovered primitive entry has a decision path. It does not authorize deletion or runtime activation.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Total entries | {summary['totalEntries']} |",
        f"| Canonical owner | {summary['canonicalOwner']} |",
        f"| Canonical runtime alias | {summary['canonicalRuntimeAlias']} |",
        f"| Source review decision | {summary['sourceReviewDecision']} |",
        f"| Digest review decision | {summary['digestReviewDecision']} |",
        f"| Source root decision | {summary['sourceRootDecision']} |",
        f"| Unreviewed singleton | {summary['unreviewedSingleton']} |",
        f"| All entries covered | `{str(summary['allEntriesCovered']).lower()}` |",
        "",
        f"Next action: {summary['nextAction']}",
        "",
        "## Unreviewed By Root",
        "",
        "| Source Root | Count | Examples |",
        "|---|---:|---|",
    ]
    unreviewed = [entry for entry in report["entries"] if entry["coverageState"] == "UNREVIEWED_SINGLETON"]
    by_root: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for entry in unreviewed:
        by_root[entry["sourceRoot"]].append(entry)
    for source_root, entries in sorted(by_root.items()):
        examples = ", ".join(
            f"`{entry['primitive']['primitiveType']}:{entry['primitive']['name']}`"
            for entry in entries[:5]
        )
        lines.append(f"| `{source_root}` | {len(entries)} | {examples} |")
    if not by_root:
        lines.append("| - | 0 | - |")
    lines.extend([
        "",
        "## Coverage States",
        "",
        "| State | Count |",
        "|---|---:|",
    ])
    states = Counter(entry["coverageState"] for entry in report["entries"])
    for state in [
        "CANONICAL_OWNER",
        "CANONICAL_RUNTIME_ALIAS",
        "SOURCE_REVIEW_DECISION",
        "DIGEST_REVIEW_DECISION",
        "SOURCE_ROOT_DECISION",
        "UNREVIEWED_SINGLETON",
    ]:
        lines.append(f"| `{state}` | {states.get(state, 0)} |")
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
