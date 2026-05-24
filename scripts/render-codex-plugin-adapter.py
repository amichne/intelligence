#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any


CODEX_PLUGIN_DIR = ".codex-plugin"
CODEX_PLUGIN_FILE = "plugin.json"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Render Codex plugin adapter files from provider-neutral plugin manifests."
    )
    parser.add_argument(
        "--marketplace",
        default="marketplace.json",
        help="Canonical provider-neutral marketplace manifest.",
    )
    parser.add_argument(
        "--out-marketplace",
        default=".agents/plugins/marketplace.json",
        help="Codex marketplace adapter to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if generated adapter files or symlinks differ from disk.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    marketplace = read_json(repo_root / args.marketplace)
    expected = build_adapter(repo_root, marketplace)
    failures = 0

    failures += write_or_check_json(
        repo_root / args.out_marketplace,
        expected["marketplace"],
        args.check,
    )
    for plugin in expected["plugins"]:
        plugin_dir = repo_root / plugin["path"]
        failures += write_or_check_json(
            plugin_dir / CODEX_PLUGIN_DIR / CODEX_PLUGIN_FILE,
            plugin["manifest"],
            args.check,
        )
        for link in plugin["links"]:
            failures += ensure_or_check_symlink(
                plugin_dir / link["adapterPath"],
                repo_root / link["canonicalPath"],
                args.check,
            )

    if failures:
        return 1
    print(f"codex plugin adapter {'check OK' if args.check else 'rendered'}")
    return 0


def build_adapter(repo_root: Path, marketplace: dict[str, Any]) -> dict[str, Any]:
    owner = marketplace.get("owner", {}).get("name", "Local developer")
    codex_marketplace = {
        "name": marketplace["name"],
        "interface": {
            "displayName": title_case(marketplace["name"]),
        },
        "plugins": [],
    }
    plugins = []
    for entry in marketplace.get("plugins", []):
        plugin_ref = entry["plugin"]
        plugin_path = Path(plugin_ref["source"]["path"])
        plugin_dir = repo_root / plugin_path
        plugin_manifest = read_json(plugin_dir / "plugin.json")
        plugin_name = plugin_manifest["name"]
        description = plugin_manifest.get("description") or entry.get("description") or f"{plugin_name} plugin."
        category = category_for(entry.get("tags", []))
        codex_marketplace["plugins"].append(
            {
                "name": plugin_name,
                "source": {
                    "source": "local",
                    "path": f"./plugins/{plugin_name}",
                },
                "policy": {
                    "installation": "AVAILABLE",
                    "authentication": "ON_INSTALL",
                },
                "category": category,
            }
        )
        codex_manifest = {
            "name": plugin_name,
            "version": plugin_manifest.get("version", plugin_ref.get("version", "0.1.0")),
            "description": description,
            "author": {
                "name": owner,
            },
            "license": "UNLICENSED",
            "keywords": sorted(set([plugin_name, *entry.get("tags", [])])),
            "interface": {
                "displayName": title_case(plugin_name),
                "shortDescription": short_description(description),
                "longDescription": description,
                "developerName": owner,
                "category": category,
                "capabilities": capabilities_for(plugin_manifest),
                "brandColor": brand_color_for(category),
                "defaultPrompt": default_prompts(plugin_name),
            },
        }
        links = primitive_links(plugin_manifest)
        if any(link["kind"] == "skills" for link in links):
            codex_manifest["skills"] = "./skills/"
        plugins.append(
            {
                "path": plugin_path.as_posix(),
                "manifest": codex_manifest,
                "links": links,
            }
        )
    return {
        "marketplace": codex_marketplace,
        "plugins": plugins,
    }


def primitive_links(plugin_manifest: dict[str, Any]) -> list[dict[str, str]]:
    links = []
    for manifest_key, adapter_dir in [
        ("skills", "skills"),
        ("agents", "agents"),
        ("hooks", "hooks"),
        ("instructions", "instructions"),
    ]:
        for primitive in plugin_manifest.get(manifest_key, []):
            source = primitive.get("source", {})
            if source.get("type") != "LOCAL_SOURCE" or source.get("path") != "./":
                continue
            canonical_path = primitive["path"]
            adapter_name = adapter_link_name(adapter_dir, primitive, canonical_path)
            links.append(
                {
                    "kind": adapter_dir,
                    "adapterPath": f"{adapter_dir}/{adapter_name}",
                    "canonicalPath": canonical_path,
                }
            )
    return sorted(links, key=lambda item: (item["kind"], item["adapterPath"]))


def adapter_link_name(adapter_dir: str, primitive: dict[str, Any], canonical_path: str) -> str:
    canonical = Path(canonical_path)
    if adapter_dir == "skills":
        return primitive["name"]
    if adapter_dir == "agents":
        return canonical.name
    if adapter_dir == "hooks":
        return canonical.name
    if adapter_dir == "instructions":
        suffix = canonical.suffix or ".md"
        return f"{primitive['name']}{suffix}"
    return canonical.name


def write_or_check_json(path: Path, payload: dict[str, Any], check: bool) -> int:
    expected = json.dumps(payload, indent=2, sort_keys=True) + "\n"
    if check:
        if not path.is_file():
            print(f"missing generated file: {path}", file=sys.stderr)
            return 1
        if path.read_text(encoding="utf-8") != expected:
            print(f"generated file is stale: {path}", file=sys.stderr)
            return 1
        return 0
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(expected, encoding="utf-8")
    print(f"wrote {path}")
    return 0


def ensure_or_check_symlink(link_path: Path, target_path: Path, check: bool) -> int:
    if check:
        if not link_path.is_symlink():
            print(f"missing generated symlink: {link_path}", file=sys.stderr)
            return 1
        if link_path.resolve(strict=False) != target_path.resolve(strict=False):
            print(f"generated symlink is stale: {link_path}", file=sys.stderr)
            return 1
        return 0
    link_path.parent.mkdir(parents=True, exist_ok=True)
    if link_path.exists() or link_path.is_symlink():
        if link_path.is_symlink() and link_path.resolve(strict=False) == target_path.resolve(strict=False):
            return 0
        print(f"refusing to replace existing adapter path: {link_path}", file=sys.stderr)
        return 1
    relative_target = os.path.relpath(target_path, link_path.parent)
    link_path.symlink_to(relative_target)
    print(f"linked {link_path} -> {relative_target}")
    return 0


def category_for(tags: list[str]) -> str:
    tag_set = set(tags)
    if tag_set & {"git", "github", "ci"}:
        return "Engineering"
    if tag_set & {"docs", "mkdocs", "zensical", "planning"}:
        return "Productivity"
    if tag_set & {"schemas", "audit", "governance"}:
        return "Engineering"
    if tag_set & {"kotlin", "testing"}:
        return "Coding"
    return "Productivity"


def capabilities_for(plugin_manifest: dict[str, Any]) -> list[str]:
    capabilities = ["Interactive"]
    if plugin_manifest.get("skills") or plugin_manifest.get("hooks"):
        capabilities.append("Write")
    return capabilities


def brand_color_for(category: str) -> str:
    return {
        "Coding": "#2563EB",
        "Engineering": "#0F766E",
        "Productivity": "#7C3AED",
    }.get(category, "#475569")


def default_prompts(plugin_name: str) -> list[str]:
    display = title_case(plugin_name)
    return [
        f"Use {display} for this task.",
        f"Review this repo with {display}.",
        f"Show the {display} workflow.",
    ]


def short_description(description: str) -> str:
    return description if len(description) <= 72 else description[:69].rstrip() + "..."


def title_case(value: str) -> str:
    return " ".join(part.capitalize() for part in value.replace("_", "-").split("-") if part)


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


if __name__ == "__main__":
    raise SystemExit(main())
