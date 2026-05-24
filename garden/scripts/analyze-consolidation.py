#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


INSTALLED_MARKETPLACE_PREFIXES = ("cache/", "marketplaces/")
SYSTEM_SOURCE_PREFIX = ".system/"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build an actionable consolidation report from discovered primitives."
    )
    parser.add_argument(
        "--inventory",
        default="garden/manifests/discovered-primitives.json",
        help="Inventory JSON produced by garden/scripts/inventory-primitives.py.",
    )
    parser.add_argument(
        "--out",
        default="garden/manifests/consolidation-report.json",
        help="Report JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="garden/docs/consolidation-queue.md",
        help="Markdown review queue to write.",
    )
    parser.add_argument(
        "--source-review-decisions",
        default="garden/manifests/source-review-decisions.json",
        help="Optional hand-authored source review decisions to summarize in the Markdown queue.",
    )
    parser.add_argument(
        "--digest-review-decisions",
        default="garden/manifests/digest-review-decisions.json",
        help="Optional hand-authored digest review decisions to summarize in the Markdown queue.",
    )
    parser.add_argument(
        "--cleanup-ledger",
        default="garden/manifests/cleanup-ledger.json",
        help="Optional cleanup ledger to summarize in the Markdown queue.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    inventory_path = (repo_root / args.inventory).resolve()
    inventory = read_json(inventory_path)
    source_review_path = (repo_root / args.source_review_decisions).resolve()
    source_review = read_json(source_review_path) if source_review_path.exists() else None
    digest_review_path = (repo_root / args.digest_review_decisions).resolve()
    digest_review = read_json(digest_review_path) if digest_review_path.exists() else None
    cleanup_ledger_path = (repo_root / args.cleanup_ledger).resolve()
    cleanup_ledger = read_json(cleanup_ledger_path) if cleanup_ledger_path.exists() else None
    report = build_report(inventory, repo_root)
    report_text = json.dumps(report, indent=2, sort_keys=True) + "\n"
    doc_text = render_doc(report, source_review, digest_review, cleanup_ledger)

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


def build_report(inventory: dict[str, Any], repo_root: Path) -> dict[str, Any]:
    entries = inventory.get("entries", [])
    enriched = [with_bucket(entry, repo_root) for entry in entries]
    by_type = Counter(entry["type"] for entry in enriched)
    by_bucket = Counter(entry["bucket"] for entry in enriched)
    by_bucket_type = bucket_type_counts(enriched)
    authored = [
        candidate_ref(entry, action_for(entry))
        for entry in enriched
        if entry["bucket"] in {"canonical", "authored-local"}
    ]

    name_groups = grouped(enriched, ("type", "name"))
    digest_groups = grouped(enriched, ("sha256",))
    name_review_values = [reviewable_values(values) for values in name_groups.values()]
    digest_review_values = [reviewable_values(values) for values in digest_groups.values()]
    name_review = [
        name_review_item(values)
        for values in name_review_values
        if len(values) > 1 and needs_name_review(values)
    ]
    digest_review = [
        digest_review_item(values)
        for values in digest_review_values
        if len(values) > 1 and needs_digest_review(values)
    ]
    first_party_collisions = [
        first_party_collision_item(values)
        for values in name_review_values
        if len(values) > 1 and has_first_party_name_collision(values)
    ]
    first_party_digest_matches = [
        first_party_digest_item(values)
        for values in digest_review_values
        if len(values) > 1 and has_first_party_digest_match(values)
    ]

    return {
        "schemaVersion": 1,
        "inventoryGeneratedAt": inventory.get("generatedAt"),
        "inventory": "garden/manifests/discovered-primitives.json",
        "counts": {
            "byType": dict(sorted(by_type.items())),
            "byBucket": dict(sorted(by_bucket.items())),
            "byBucketAndType": by_bucket_type,
            "totalEntries": len(enriched),
            "nameReviewGroups": len(name_review),
            "digestReviewGroups": len(digest_review),
            "firstPartyNameCollisions": len(first_party_collisions),
            "firstPartyDigestMatches": len(first_party_digest_matches),
            "brokenSymlinks": len(inventory.get("brokenSymlinks", [])),
        },
        "promotionCandidates": sorted(
            authored,
            key=lambda item: (item["type"], item["name"], item["bucket"], item["path"]),
        ),
        "nameReviewQueue": sorted(
            name_review,
            key=lambda item: (item["priority"], item["type"], item["name"]),
        ),
        "digestReviewQueue": sorted(
            digest_review,
            key=lambda item: (item["priority"], item["sha256"]),
        ),
        "firstPartyNameCollisions": sorted(
            first_party_collisions,
            key=lambda item: (item["type"], item["name"]),
        ),
        "firstPartyDigestMatches": sorted(
            first_party_digest_matches,
            key=lambda item: item["sha256"],
        ),
        "brokenSymlinks": inventory.get("brokenSymlinks", []),
    }


def with_bucket(entry: dict[str, Any], repo_root: Path) -> dict[str, Any]:
    result = dict(entry)
    result["bucket"] = bucket(entry, repo_root)
    return result


def bucket(entry: dict[str, Any], repo_root: Path) -> str:
    role = entry.get("sourceRootRole")
    source_root = entry.get("sourceRoot")
    path = entry.get("path", "")
    if active_repo_runtime_alias(entry, repo_root):
        return "runtime-alias"
    if role == "canonical-candidate":
        return "canonical"
    if source_root == "claude-plugins" and path.startswith(INSTALLED_MARKETPLACE_PREFIXES):
        return "installed-marketplace"
    if role == "local-repo-source":
        return "authored-local"
    if role == "runtime-source":
        return "runtime"
    if role == "backup-source":
        return "backup"
    return "uncategorized"


def active_repo_runtime_alias(entry: dict[str, Any], repo_root: Path) -> bool:
    if entry.get("sourceRootRole") != "runtime-source" or not entry.get("symlink"):
        return False
    try:
        observed = Path(entry["observedPath"])
        resolved = Path(entry["resolvedPath"]).resolve(strict=False)
    except OSError:
        return False
    return not observed.is_relative_to(repo_root) and resolved.is_relative_to(repo_root)


def reviewable_values(values: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        entry
        for entry in values
        if entry["bucket"] != "runtime-alias"
    ]


def action_for(entry: dict[str, Any]) -> str:
    if entry["bucket"] == "canonical":
        return "KEEP_CANONICAL"
    if entry["bucket"] == "authored-local":
        return "REVIEW_PROMOTE"
    return "REVIEW"


def grouped(entries: list[dict[str, Any]], keys: tuple[str, ...]) -> dict[tuple[str, ...], list[dict[str, Any]]]:
    result: dict[tuple[str, ...], list[dict[str, Any]]] = defaultdict(list)
    for entry in entries:
        result[tuple(entry[key] for key in keys)].append(entry)
    return result


def needs_name_review(values: list[dict[str, Any]]) -> bool:
    buckets = {entry["bucket"] for entry in values}
    return bool(buckets & {"canonical", "authored-local", "runtime", "backup"})


def needs_digest_review(values: list[dict[str, Any]]) -> bool:
    buckets = {entry["bucket"] for entry in values}
    return bool(buckets & {"canonical", "authored-local"}) and bool(buckets & {"runtime", "backup", "installed-marketplace"})


def is_first_party_source(entry: dict[str, Any]) -> bool:
    source_root = entry.get("sourceRoot", "")
    path = entry.get("path", "")
    if entry.get("bucket") == "installed-marketplace":
        return True
    if source_root == "claude-plugins":
        return True
    return source_root in {"apollo-skills", "global-agent-skills", "global-codex-skills", "global-codex-skills-backup"} and path.startswith(SYSTEM_SOURCE_PREFIX)


def has_first_party_name_collision(values: list[dict[str, Any]]) -> bool:
    return any(entry.get("bucket") == "canonical" for entry in values) and any(
        is_first_party_source(entry) for entry in values
    )


def has_first_party_digest_match(values: list[dict[str, Any]]) -> bool:
    return any(entry.get("bucket") == "canonical" for entry in values) and any(
        is_first_party_source(entry) for entry in values
    )


def first_party_collision_item(values: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "type": values[0]["type"],
        "name": values[0]["name"],
        "action": "RENAME_OR_RECORD_INTENTIONAL_REPLACEMENT",
        "entries": [candidate_ref(entry) for entry in sorted(values, key=entry_sort_key)],
    }


def first_party_digest_item(values: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "sha256": values[0]["sha256"],
        "action": "REWRITE_CANONICAL_DERIVATIVE",
        "entries": [candidate_ref(entry) for entry in sorted(values, key=entry_sort_key)],
    }


def name_review_item(values: list[dict[str, Any]]) -> dict[str, Any]:
    buckets = {entry["bucket"] for entry in values}
    digests = {entry["sha256"] for entry in values}
    priority = 1
    action = "SYNTHESIZE"
    if buckets <= {"installed-marketplace"}:
        priority = 4
        action = "IGNORE_INSTALLED_DUPLICATE"
    elif len(digests) == 1:
        priority = 2
        action = "DEDUP_IDENTICAL_NAME"
    elif buckets & {"canonical", "authored-local"} and buckets & {"runtime", "backup"}:
        priority = 2
        action = "RECONCILE_RUNTIME_COPY"
    return {
        "type": values[0]["type"],
        "name": values[0]["name"],
        "priority": priority,
        "action": action,
        "digests": len(digests),
        "buckets": sorted(buckets),
        "entries": [candidate_ref(entry) for entry in sorted(values, key=entry_sort_key)],
    }


def digest_review_item(values: list[dict[str, Any]]) -> dict[str, Any]:
    buckets = {entry["bucket"] for entry in values}
    priority = 1 if "canonical" in buckets else 2
    return {
        "sha256": values[0]["sha256"],
        "priority": priority,
        "action": "REPLACE_DUPLICATES_WITH_REFERENCES_AFTER_REVIEW",
        "buckets": sorted(buckets),
        "entries": [candidate_ref(entry) for entry in sorted(values, key=entry_sort_key)],
    }


def candidate_ref(entry: dict[str, Any], action: str | None = None) -> dict[str, str]:
    result = {
        "type": entry["type"],
        "name": entry["name"],
        "bucket": entry["bucket"],
        "sourceRoot": entry["sourceRoot"],
        "path": entry["path"],
        "resolvedPath": entry["resolvedPath"],
        "sha256": entry["sha256"],
    }
    if action:
        result["action"] = action
    return result


def bucket_type_counts(entries: list[dict[str, Any]]) -> dict[str, dict[str, int]]:
    buckets: dict[str, Counter[str]] = defaultdict(Counter)
    for entry in entries:
        buckets[entry["bucket"]][entry["type"]] += 1
    return {
        bucket: dict(sorted(counts.items()))
        for bucket, counts in sorted(buckets.items())
    }


def render_doc(
    report: dict[str, Any],
    source_review: dict[str, Any] | None = None,
    digest_review: dict[str, Any] | None = None,
    cleanup_ledger: dict[str, Any] | None = None,
) -> str:
    counts = report["counts"]
    lines = [
        "# Consolidation Queue",
        "",
        f"Inventory source: `{report['inventory']}`",
        f"Inventory generated at: `{report.get('inventoryGeneratedAt')}`",
        "",
        "## Counts",
        "",
        "| Bucket | Count |",
        "|---|---:|",
    ]
    for bucket, count in counts["byBucket"].items():
        lines.append(f"| `{bucket}` | {count} |")
    lines.extend([
        "",
        "| Type | Count |",
        "|---|---:|",
    ])
    for primitive_type, count in counts["byType"].items():
        lines.append(f"| `{primitive_type}` | {count} |")
    lines.extend([
        "",
        "## Promotion Candidates",
        "",
        "These are canonical or local-authored entries. They are the first review set before importing runtime/cache material.",
        "",
        "| Type | Name | Bucket | Source | Path | Action |",
        "|---|---|---|---|---|---|",
    ])
    for entry in report["promotionCandidates"][:80]:
        lines.append(
            f"| `{entry['type']}` | `{entry['name']}` | `{entry['bucket']}` | "
            f"`{entry['sourceRoot']}` | `{entry['path']}` | `{entry['action']}` |"
        )
    remaining = len(report["promotionCandidates"]) - 80
    if remaining > 0:
        lines.append(f"| | | | | plus {remaining} more in the JSON report | |")

    lines.extend([
        "",
        "## Name Review Queue",
        "",
        "Name collisions need semantic review unless every entry has the same digest.",
        "",
        "| Priority | Type | Name | Action | Buckets | Entries |",
        "|---:|---|---|---|---|---:|",
    ])
    for group in report["nameReviewQueue"][:60]:
        lines.append(
            f"| {group['priority']} | `{group['type']}` | `{group['name']}` | "
            f"`{group['action']}` | `{', '.join(group['buckets'])}` | {len(group['entries'])} |"
        )

    if source_review:
        lines.extend(render_source_review_decisions(source_review))

    lines.extend([
        "",
        "## Digest Review Queue",
        "",
        "Digest duplicates are candidates for symlink/reference replacement after promotion is verified.",
        "",
        "| Priority | Entries | Buckets | Digest |",
        "|---:|---:|---|---|",
    ])
    for group in report["digestReviewQueue"][:60]:
        lines.append(
            f"| {group['priority']} | {len(group['entries'])} | "
            f"`{', '.join(group['buckets'])}` | `{group['sha256']}` |"
        )

    if digest_review:
        lines.extend(render_digest_review_decisions(digest_review))

    lines.extend([
        "",
        "## First-Party Name Collisions",
        "",
        "Canonical primitives should not reuse first-party OpenAI/Anthropic names unless an intentional replacement is recorded.",
        "",
    ])
    if report["firstPartyNameCollisions"]:
        lines.extend([
            "| Type | Name | Action | Entries |",
            "|---|---|---|---:|",
        ])
        for group in report["firstPartyNameCollisions"]:
            lines.append(
                f"| `{group['type']}` | `{group['name']}` | `{group['action']}` | {len(group['entries'])} |"
            )
    else:
        lines.append("No canonical primitive currently shares a type/name with a first-party installed or system source.")

    lines.extend([
        "",
        "## First-Party Raw Digest Matches",
        "",
        "Canonical primitives should not have identical content digests to first-party OpenAI/Anthropic sources.",
        "",
    ])
    if report["firstPartyDigestMatches"]:
        lines.extend([
            "| Digest | Action | Entries |",
            "|---|---|---:|",
        ])
        for group in report["firstPartyDigestMatches"]:
            lines.append(
                f"| `{group['sha256']}` | `{group['action']}` | {len(group['entries'])} |"
            )
    else:
        lines.append("No canonical primitive currently has the same digest as a first-party installed or system source.")

    if cleanup_ledger:
        lines.extend(render_cleanup_ledger(cleanup_ledger))

    lines.extend([
        "",
        "## Broken Symlinks",
        "",
        "Broken symlinks are cleanup candidates, but they still need ledger entries before removal.",
        "",
    ])
    if report["brokenSymlinks"]:
        lines.extend([
            "| Source | Path | Target |",
            "|---|---|---|",
        ])
        for item in report["brokenSymlinks"]:
            lines.append(f"| `{item['sourceRoot']}` | `{item['path']}` | `{item['target']}` |")
    else:
        lines.append("No broken symlinks currently found.")
    lines.append("")
    return "\n".join(lines)


def render_source_review_decisions(source_review: dict[str, Any]) -> list[str]:
    entries = sorted(
        source_review.get("entries", []),
        key=lambda item: (
            item.get("queueEvidence", {}).get("priority", 99),
            item.get("target", {}).get("primitiveType", ""),
            item.get("target", {}).get("name", ""),
        ),
    )
    decision_counts = Counter(entry.get("decision", "") for entry in entries)
    status_counts = Counter(entry.get("status", "") for entry in entries)
    lines = [
        "",
        "## Source Review Decisions",
        "",
        "Hand-authored trim decisions cover generated name-review groups. These decisions do not authorize deletion; cleanup still requires `garden/manifests/cleanup-ledger.json`.",
        "",
        "| Decision | Count |",
        "|---|---:|",
    ]
    for decision, count in sorted(decision_counts.items()):
        lines.append(f"| `{decision}` | {count} |")
    lines.extend([
        "",
        "| Status | Count |",
        "|---|---:|",
    ])
    for status, count in sorted(status_counts.items()):
        lines.append(f"| `{status}` | {count} |")
    lines.extend([
        "",
        "| Priority | Type | Name | Decision | Status | Coverage |",
        "|---:|---|---|---|---|---|",
    ])
    for entry in entries[:80]:
        target = entry.get("target", {})
        evidence = entry.get("queueEvidence", {})
        coverage = ", ".join(f"`{item['path']}`" for item in entry.get("canonicalCoverage", []))
        if not coverage:
            coverage = "-"
        lines.append(
            f"| {evidence.get('priority')} | `{target.get('primitiveType')}` | "
            f"`{target.get('name')}` | `{entry.get('decision')}` | `{entry.get('status')}` | {coverage} |"
        )
    remaining = len(entries) - 80
    if remaining > 0:
        lines.append(f"| | | | | | plus {remaining} more in the JSON manifest |")
    return lines


def render_cleanup_ledger(cleanup_ledger: dict[str, Any]) -> list[str]:
    status_order = {
        "PROPOSED": 0,
        "APPROVED": 1,
        "EXECUTED": 2,
    }
    entries = sorted(
        cleanup_ledger.get("entries", []),
        key=lambda item: (
            status_order.get(item.get("status", ""), 99),
            item.get("decision", ""),
            item.get("sourceRoot", ""),
            item.get("sourcePath", ""),
        ),
    )
    decision_counts = Counter(entry.get("decision", "") for entry in entries)
    status_counts = Counter(entry.get("status", "") for entry in entries)
    lines = [
        "",
        "## Cleanup Ledger",
        "",
        "Ledger entries are the cleanup authority. `PROPOSED` entries are review records only; they do not authorize deletion or symlink writes.",
        "",
        "| Decision | Count |",
        "|---|---:|",
    ]
    for decision, count in sorted(decision_counts.items()):
        lines.append(f"| `{decision}` | {count} |")
    lines.extend([
        "",
        "| Status | Count |",
        "|---|---:|",
    ])
    for status, count in sorted(status_counts.items()):
        lines.append(f"| `{status}` | {count} |")
    lines.extend([
        "",
        "| Status | Decision | Source | Path | Canonical | Evidence |",
        "|---|---|---|---|---|---|",
    ])
    for entry in entries[:80]:
        evidence = entry.get("reviewEvidence", {})
        evidence_target = cleanup_evidence_label(evidence)
        if evidence_target != "-":
            evidence_target = f"`{evidence_target}`"
        canonical = entry.get("canonicalPath") or "-"
        if canonical != "-":
            canonical = f"`{canonical}`"
        lines.append(
            f"| `{entry.get('status')}` | `{entry.get('decision')}` | "
            f"`{entry.get('sourceRoot')}` | `{entry.get('sourcePath')}` | {canonical} | {evidence_target} |"
        )
    remaining = len(entries) - 80
    if remaining > 0:
        lines.append(f"| | | | | | plus {remaining} more in the JSON manifest |")
    return lines


def cleanup_evidence_label(evidence: dict[str, Any]) -> str:
    if evidence.get("type") == "SOURCE_REVIEW_EVIDENCE":
        return evidence.get("sourceSha256", "-")
    return evidence.get("targetSha256", "-")


def render_digest_review_decisions(digest_review: dict[str, Any]) -> list[str]:
    entries = sorted(
        digest_review.get("entries", []),
        key=lambda item: (
            item.get("queueEvidence", {}).get("priority", 99),
            item.get("target", {}).get("sha256", ""),
        ),
    )
    decision_counts = Counter(entry.get("decision", "") for entry in entries)
    status_counts = Counter(entry.get("status", "") for entry in entries)
    lines = [
        "",
        "## Digest Review Decisions",
        "",
        "Hand-authored trim decisions cover generated duplicate-content groups. These decisions do not authorize deletion; cleanup still requires `garden/manifests/cleanup-ledger.json`.",
        "",
        "| Decision | Count |",
        "|---|---:|",
    ]
    for decision, count in sorted(decision_counts.items()):
        lines.append(f"| `{decision}` | {count} |")
    lines.extend([
        "",
        "| Status | Count |",
        "|---|---:|",
    ])
    for status, count in sorted(status_counts.items()):
        lines.append(f"| `{status}` | {count} |")
    lines.extend([
        "",
        "| Priority | Digest | Decision | Status | Candidates | Coverage |",
        "|---:|---|---|---|---|---|",
    ])
    for entry in entries[:80]:
        target = entry.get("target", {})
        evidence = entry.get("queueEvidence", {})
        candidates = ", ".join(f"`{item}`" for item in evidence.get("candidateKeys", []))
        coverage = ", ".join(f"`{item['path']}`" for item in entry.get("canonicalCoverage", []))
        if not coverage:
            coverage = "-"
        lines.append(
            f"| {evidence.get('priority')} | `{target.get('sha256')}` | "
            f"`{entry.get('decision')}` | `{entry.get('status')}` | {candidates} | {coverage} |"
        )
    remaining = len(entries) - 80
    if remaining > 0:
        lines.append(f"| | | | | | plus {remaining} more in the JSON manifest |")
    return lines


def entry_sort_key(entry: dict[str, Any]) -> tuple[str, str, str, str]:
    return (entry["bucket"], entry["sourceRoot"], entry["type"], entry["path"])


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
