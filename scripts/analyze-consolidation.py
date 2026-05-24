#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


INSTALLED_MARKETPLACE_PREFIXES = ("cache/", "marketplaces/")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build an actionable consolidation report from discovered primitives."
    )
    parser.add_argument(
        "--inventory",
        default="manifests/discovered-primitives.json",
        help="Inventory JSON produced by scripts/inventory-primitives.py.",
    )
    parser.add_argument(
        "--out",
        default="manifests/consolidation-report.json",
        help="Report JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="docs/consolidation-queue.md",
        help="Markdown review queue to write.",
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
    report = build_report(inventory)
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


def build_report(inventory: dict[str, Any]) -> dict[str, Any]:
    entries = inventory.get("entries", [])
    enriched = [with_bucket(entry) for entry in entries]
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
    name_review = [
        name_review_item(values)
        for values in name_groups.values()
        if len(values) > 1 and needs_name_review(values)
    ]
    digest_review = [
        digest_review_item(values)
        for values in digest_groups.values()
        if len(values) > 1 and needs_digest_review(values)
    ]

    return {
        "schemaVersion": 1,
        "inventoryGeneratedAt": inventory.get("generatedAt"),
        "inventory": "manifests/discovered-primitives.json",
        "counts": {
            "byType": dict(sorted(by_type.items())),
            "byBucket": dict(sorted(by_bucket.items())),
            "byBucketAndType": by_bucket_type,
            "totalEntries": len(enriched),
            "nameReviewGroups": len(name_review),
            "digestReviewGroups": len(digest_review),
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
        "brokenSymlinks": inventory.get("brokenSymlinks", []),
    }


def with_bucket(entry: dict[str, Any]) -> dict[str, Any]:
    result = dict(entry)
    result["bucket"] = bucket(entry)
    return result


def bucket(entry: dict[str, Any]) -> str:
    role = entry.get("sourceRootRole")
    source_root = entry.get("sourceRoot")
    path = entry.get("path", "")
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


def render_doc(report: dict[str, Any]) -> str:
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

    lines.extend([
        "",
        "## Broken Symlinks",
        "",
        "Broken symlinks are cleanup candidates, but they still need ledger entries before removal.",
        "",
        "| Source | Path | Target |",
        "|---|---|---|",
    ])
    for item in report["brokenSymlinks"]:
        lines.append(f"| `{item['sourceRoot']}` | `{item['path']}` | `{item['target']}` |")
    lines.append("")
    return "\n".join(lines)


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
