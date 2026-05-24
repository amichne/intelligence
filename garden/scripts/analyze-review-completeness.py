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
        description="Build canonical primitive review-completeness evidence."
    )
    parser.add_argument(
        "--plugin-coverage",
        default="garden/manifests/plugin-coverage.json",
        help="Generated plugin coverage report.",
    )
    parser.add_argument(
        "--primitive-audits",
        default="garden/manifests/primitive-audits.json",
        help="Primitive audit ledger.",
    )
    parser.add_argument(
        "--promotions",
        default="garden/manifests/promotions.json",
        help="Promotion manifest.",
    )
    parser.add_argument(
        "--out",
        default="garden/manifests/review-completeness.json",
        help="Review completeness JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="garden/docs/review-completeness.md",
        help="Markdown review completeness summary to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    plugin_coverage = read_json((repo_root / args.plugin_coverage).resolve())
    primitive_audits = read_json((repo_root / args.primitive_audits).resolve())
    promotions = read_json((repo_root / args.promotions).resolve())
    report = build_report(plugin_coverage, primitive_audits, promotions, args)
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
    primitive_audits: dict[str, Any],
    promotions: dict[str, Any],
    args: argparse.Namespace,
) -> dict[str, Any]:
    audits_by_key = {
        primitive_key(entry["primitive"]["primitiveType"], entry["primitive"]["name"]): entry
        for entry in primitive_audits.get("entries", [])
    }
    promotion_keys = {
        primitive_key(entry["type"], entry["name"])
        for entry in promotions.get("entries", [])
    }
    entries = [
        review_entry(entry, audits_by_key, promotion_keys)
        for entry in plugin_coverage.get("entries", [])
    ]
    entries = sorted(
        entries,
        key=lambda item: (
            item["auditState"],
            item["primitive"]["primitiveType"],
            item["primitive"]["name"],
        ),
    )
    audit_counts = Counter(entry["auditState"] for entry in entries)
    promotion_counts = Counter(entry["promotionState"] for entry in entries)
    return {
        "type": "REVIEW_COMPLETENESS_REPORT",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "REVIEW_COMPLETENESS_INPUTS",
            "pluginCoverage": args.plugin_coverage,
            "primitiveAudits": args.primitive_audits,
            "promotions": args.promotions,
        },
        "summary": {
            "type": "REVIEW_COMPLETENESS_SUMMARY",
            "totalCanonical": len(entries),
            "audited": audit_counts.get("AUDITED", 0),
            "needsAudit": audit_counts.get("NEEDS_AUDIT", 0),
            "promoted": promotion_counts.get("PROMOTED", 0),
            "nativeCanonical": promotion_counts.get("NATIVE_CANONICAL", 0),
            "allCanonicalAudited": audit_counts.get("NEEDS_AUDIT", 0) == 0,
        },
        "entries": entries,
    }


def review_entry(
    coverage_entry: dict[str, Any],
    audits_by_key: dict[str, dict[str, Any]],
    promotion_keys: set[str],
) -> dict[str, Any]:
    primitive = coverage_entry["primitive"]
    key = primitive_key(primitive["primitiveType"], primitive["name"])
    audit = audits_by_key.get(key)
    promoted = key in promotion_keys
    result = {
        "type": "REVIEW_COMPLETENESS_ENTRY",
        "primitive": primitive,
        "canonicalPaths": coverage_entry["canonicalPaths"],
        "coverageStatus": coverage_entry["coverageStatus"],
        "auditState": "AUDITED" if audit else "NEEDS_AUDIT",
        "promotionState": "PROMOTED" if promoted else "NATIVE_CANONICAL",
        "notes": notes(coverage_entry, bool(audit), promoted),
    }
    if audit:
        result["auditDecision"] = audit["decision"]
    return result


def notes(coverage_entry: dict[str, Any], audited: bool, promoted: bool) -> str:
    primitive = coverage_entry["primitive"]
    if audited:
        return "Primitive has a durable audit entry in garden/manifests/primitive-audits.json."
    if coverage_entry["coverageStatus"] == "SCOPED_INSTRUCTION":
        return "Scoped repository instruction is routed by file placement but still needs an explicit audit decision."
    if primitive["primitiveType"] == "PLUGIN":
        return "Referential plugin is marketplace-exposed but still needs a plugin-level audit decision."
    if promoted:
        return "Promotion exists but audit evidence is missing."
    return "Canonical primitive is routed but still needs an explicit audit decision."


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Review Completeness",
        "",
        "This generated report compares canonical primitive coverage with durable audit entries.",
        "",
        "## Summary",
        "",
        "| Measure | Value |",
        "|---|---:|",
        f"| Total canonical | {summary['totalCanonical']} |",
        f"| Audited | {summary['audited']} |",
        f"| Needs audit | {summary['needsAudit']} |",
        f"| Promoted | {summary['promoted']} |",
        f"| Native canonical | {summary['nativeCanonical']} |",
        f"| All canonical audited | `{str(summary['allCanonicalAudited']).lower()}` |",
        "",
        "## Needs Audit",
        "",
        "| Type | Name | Coverage | Promotion | Paths | Notes |",
        "|---|---|---|---|---|---|",
    ]
    needs_audit = [entry for entry in report["entries"] if entry["auditState"] == "NEEDS_AUDIT"]
    for entry in needs_audit:
        primitive = entry["primitive"]
        lines.append(
            f"| `{primitive['primitiveType']}` | `{primitive['name']}` | `{entry['coverageStatus']}` | "
            f"`{entry['promotionState']}` | {join_code(entry['canonicalPaths'])} | {entry['notes']} |"
        )
    if not needs_audit:
        lines.append("| - | - | - | - | - | - |")
    lines.extend([
        "",
        "## Audited",
        "",
        "| Type | Name | Decision | Coverage | Promotion |",
        "|---|---|---|---|---|",
    ])
    for entry in report["entries"]:
        if entry["auditState"] != "AUDITED":
            continue
        primitive = entry["primitive"]
        lines.append(
            f"| `{primitive['primitiveType']}` | `{primitive['name']}` | "
            f"`{entry.get('auditDecision')}` | `{entry['coverageStatus']}` | `{entry['promotionState']}` |"
        )
    lines.append("")
    return "\n".join(lines)


def join_code(values: list[str]) -> str:
    return ", ".join(f"`{value}`" for value in values)


def primitive_key(primitive_type: str, name: str) -> str:
    return f"{primitive_type}\u0000{name}"


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
