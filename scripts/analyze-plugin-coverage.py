#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


PRIMITIVE_COLLECTIONS = ("skills", "agents", "instructions", "hooks")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build plugin coverage evidence for canonical primitives."
    )
    parser.add_argument(
        "--inventory",
        default="manifests/discovered-primitives.json",
        help="Inventory JSON produced by scripts/inventory-primitives.py.",
    )
    parser.add_argument(
        "--marketplace",
        default="marketplace.json",
        help="Marketplace catalog exposing local plugins.",
    )
    parser.add_argument(
        "--plugins-dir",
        default="plugins",
        help="Directory containing referential plugin manifests.",
    )
    parser.add_argument(
        "--out",
        default="manifests/plugin-coverage.json",
        help="Coverage JSON to write.",
    )
    parser.add_argument(
        "--doc",
        default="docs/plugin-coverage.md",
        help="Markdown coverage summary to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated report or doc differs from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    inventory_path = (repo_root / args.inventory).resolve()
    marketplace_path = (repo_root / args.marketplace).resolve()
    plugins_dir = (repo_root / args.plugins_dir).resolve()
    report = build_report(
        read_json(inventory_path),
        read_json(marketplace_path),
        plugins_dir,
        args.inventory,
        args.marketplace,
        args.plugins_dir,
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
    inventory: dict[str, Any],
    marketplace: dict[str, Any],
    plugins_dir: Path,
    inventory_arg: str,
    marketplace_arg: str,
    plugins_dir_arg: str,
) -> dict[str, Any]:
    plugin_references = collect_plugin_references(plugins_dir)
    marketplace_references = collect_marketplace_references(marketplace)
    canonical_groups = group_canonical_primitives(inventory)
    entries = [
        coverage_entry(key, values, plugin_references, marketplace_references)
        for key, values in canonical_groups.items()
    ]
    entries = sorted(
        entries,
        key=lambda item: (
            item["primitive"]["primitiveType"],
            item["primitive"]["name"],
        ),
    )
    status_counts = Counter(entry["coverageStatus"] for entry in entries)
    type_counts = Counter(entry["primitive"]["primitiveType"] for entry in entries)
    return {
        "type": "PLUGIN_COVERAGE_REPORT",
        "schemaVersion": 1,
        "generatedFrom": {
            "type": "PLUGIN_COVERAGE_INPUTS",
            "inventory": inventory_arg,
            "marketplace": marketplace_arg,
            "pluginDirectory": plugins_dir_arg,
        },
        "summary": {
            "type": "PLUGIN_COVERAGE_SUMMARY",
            "totalPrimitives": len(entries),
            "allCanonicalRouted": status_counts.get("STANDALONE_ONLY", 0) == 0,
            "byStatus": count_entries(status_counts),
            "byType": count_entries(type_counts),
        },
        "entries": entries,
    }


def collect_plugin_references(plugins_dir: Path) -> dict[tuple[str, str], list[dict[str, str]]]:
    references: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    for plugin_file in sorted(plugins_dir.glob("*/plugin.json")):
        plugin = read_json(plugin_file)
        plugin_name = plugin["name"]
        for collection in PRIMITIVE_COLLECTIONS:
            for primitive in plugin.get(collection, []):
                references[(primitive["type"], primitive["name"])].append(
                    {
                        "type": "PLUGIN_COVERAGE_REFERENCE",
                        "pluginName": plugin_name,
                        "primitivePath": primitive["path"],
                    }
                )
    return {
        key: sorted(value, key=lambda item: (item["pluginName"], item["primitivePath"]))
        for key, value in references.items()
    }


def collect_marketplace_references(marketplace: dict[str, Any]) -> dict[str, list[dict[str, str]]]:
    references: dict[str, list[dict[str, str]]] = defaultdict(list)
    for entry in marketplace.get("plugins", []):
        plugin = entry.get("plugin", {})
        source = plugin.get("source", {})
        references[plugin.get("name", entry.get("name", ""))].append(
            {
                "type": "MARKETPLACE_COVERAGE_REFERENCE",
                "pluginName": plugin.get("name", entry.get("name", "")),
                "pluginPath": normalize_local_path(source.get("path", "")),
            }
        )
    return {
        key: sorted(value, key=lambda item: item["pluginPath"])
        for key, value in references.items()
    }


def normalize_local_path(path_value: str) -> str:
    if path_value.startswith("./"):
        return path_value[2:]
    return path_value


def group_canonical_primitives(inventory: dict[str, Any]) -> dict[tuple[str, str], list[dict[str, Any]]]:
    groups: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for entry in inventory.get("entries", []):
        if entry.get("sourceRoot") == "intelligence":
            groups[(entry["type"], entry["name"])].append(entry)
    return {
        key: sorted(value, key=lambda item: item["path"])
        for key, value in sorted(groups.items())
    }


def coverage_entry(
    key: tuple[str, str],
    values: list[dict[str, Any]],
    plugin_references: dict[tuple[str, str], list[dict[str, str]]],
    marketplace_references: dict[str, list[dict[str, str]]],
) -> dict[str, Any]:
    primitive_type, name = key
    plugin_refs = plugin_references.get(key, [])
    marketplace_refs = marketplace_references.get(name, []) if primitive_type == "PLUGIN" else []
    canonical_paths = sorted({entry["path"] for entry in values})
    status = coverage_status(primitive_type, canonical_paths, plugin_refs, marketplace_refs)
    return {
        "type": "PLUGIN_COVERAGE_ENTRY",
        "primitive": {
            "type": "PRIMITIVE_TARGET",
            "primitiveType": primitive_type,
            "name": name,
        },
        "coverageStatus": status,
        "canonicalPaths": canonical_paths,
        "pluginReferences": plugin_refs,
        "marketplaceReferences": marketplace_refs,
    }


def coverage_status(
    primitive_type: str,
    canonical_paths: list[str],
    plugin_refs: list[dict[str, str]],
    marketplace_refs: list[dict[str, str]],
) -> str:
    if plugin_refs:
        return "PLUGIN_COMPOSED"
    if primitive_type == "PLUGIN" and marketplace_refs:
        return "MARKETPLACE_EXPOSED"
    if primitive_type == "INSTRUCTION" and any(path.endswith("AGENTS.md") for path in canonical_paths):
        return "SCOPED_INSTRUCTION"
    return "STANDALONE_ONLY"


def count_entries(counter: Counter[str]) -> list[dict[str, Any]]:
    return [
        {
            "type": "COVERAGE_COUNT",
            "name": name,
            "count": count,
        }
        for name, count in sorted(counter.items())
    ]


def render_doc(report: dict[str, Any]) -> str:
    summary = report["summary"]
    lines = [
        "# Plugin Coverage",
        "",
        "This generated report shows how canonical primitives are exposed through referential plugins, marketplace entries, or scoped repository instructions.",
        "",
        f"All canonical primitives routed: `{str(summary['allCanonicalRouted']).lower()}`",
        "",
        "## Coverage Status",
        "",
        "| Status | Count |",
        "|---|---:|",
    ]
    for item in summary["byStatus"]:
        lines.append(f"| `{item['name']}` | {item['count']} |")
    lines.extend([
        "",
        "## Primitive Types",
        "",
        "| Type | Count |",
        "|---|---:|",
    ])
    for item in summary["byType"]:
        lines.append(f"| `{item['name']}` | {item['count']} |")
    lines.extend([
        "",
        "## Entries",
        "",
        "| Status | Type | Name | Canonical Paths | References |",
        "|---|---|---|---|---|",
    ])
    for entry in report["entries"]:
        primitive = entry["primitive"]
        references = [
            f"`{item['pluginName']}`"
            for item in entry["pluginReferences"]
        ] + [
            f"`marketplace:{item['pluginName']}`"
            for item in entry["marketplaceReferences"]
        ]
        if not references:
            references = ["-"]
        lines.append(
            f"| `{entry['coverageStatus']}` | `{primitive['primitiveType']}` | "
            f"`{primitive['name']}` | {join_code(entry['canonicalPaths'])} | {', '.join(references)} |"
        )
    lines.append("")
    return "\n".join(lines)


def join_code(values: list[str]) -> str:
    return ", ".join(f"`{value}`" for value in values)


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
