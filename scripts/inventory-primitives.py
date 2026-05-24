#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import fnmatch
import hashlib
import json
import os
import re
import stat
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any


TEXT_SUFFIXES = {".md", ".markdown", ".txt", ".yaml", ".yml", ".json", ".toml"}
HOOK_SUFFIXES = {".json", ".sh", ".py", ".js", ".ts", ".mjs"}


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Inventory local AI tooling primitives without moving sources."
    )
    parser.add_argument(
        "--config",
        default="manifests/source-roots.json",
        help="Source-root manifest to read.",
    )
    parser.add_argument(
        "--out",
        default="manifests/discovered-primitives.json",
        help="Inventory JSON file to write.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail if the generated inventory differs from --out.",
    )
    args = parser.parse_args()

    repo_root = Path.cwd().resolve()
    config_path = (repo_root / args.config).resolve()
    config = read_json(config_path)
    inventory = build_inventory(repo_root, config)
    rendered = json.dumps(inventory, indent=2, sort_keys=True) + "\n"

    out_path = (repo_root / args.out).resolve()
    if args.check:
        if not out_path.exists():
            print(f"missing inventory: {out_path}", file=sys.stderr)
            return 1
        current_data = json.loads(out_path.read_text(encoding="utf-8"))
        current_data["generatedAt"] = inventory["generatedAt"]
        current = json.dumps(current_data, indent=2, sort_keys=True) + "\n"
        if current != rendered:
            print(f"inventory is stale: {out_path}", file=sys.stderr)
            return 1
        return 0

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(rendered, encoding="utf-8")
    print(f"wrote {out_path}")
    return 0


def build_inventory(repo_root: Path, config: dict[str, Any]) -> dict[str, Any]:
    excluded_parts = set(config.get("excludedPathParts", []))
    exclude_globs = list(config.get("excludeGlobs", []))
    entries: list[dict[str, Any]] = []
    missing_roots: list[dict[str, str]] = []
    broken_symlinks: list[dict[str, str]] = []

    for root in config.get("scanRoots", []):
        raw_path = root["path"]
        observed_root = resolve_config_path(repo_root, raw_path)
        if not observed_root.exists():
            missing_roots.append(
                {
                    "name": root["name"],
                    "path": raw_path,
                    "resolvedPath": str(observed_root),
                }
            )
            continue

        broken_symlinks.extend(find_broken_symlinks(observed_root, root, excluded_parts, exclude_globs))
        for path in iter_files(observed_root, excluded_parts, exclude_globs):
            entry = classify_path(path, observed_root, root)
            if entry is not None:
                entries.append(entry)

    entries.sort(key=lambda item: (item["type"], item["name"], item["sourceRoot"], item["path"]))
    return {
        "schemaVersion": 1,
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "generator": "scripts/inventory-primitives.py",
        "sourceManifest": "manifests/source-roots.json",
        "counts": counts(entries),
        "missingRoots": missing_roots,
        "brokenSymlinks": sorted(broken_symlinks, key=lambda item: (item["sourceRoot"], item["path"])),
        "nameCollisions": collision_groups(entries, ("type", "name"), repo_root),
        "digestDuplicates": digest_groups(entries, repo_root),
        "entries": entries,
    }


def resolve_config_path(repo_root: Path, value: str) -> Path:
    expanded = Path(os.path.expanduser(value))
    if expanded.is_absolute():
        return expanded
    return (repo_root / expanded).resolve()


def iter_files(root: Path, excluded_parts: set[str], exclude_globs: list[str]) -> list[Path]:
    files: list[Path] = []
    stack = [root]
    visited_dirs: set[tuple[int, int]] = set()

    while stack:
        current = stack.pop()
        try:
            resolved = current.resolve()
            info = resolved.stat()
        except OSError:
            continue
        if not stat.S_ISDIR(info.st_mode):
            files.append(current)
            continue
        key = (info.st_dev, info.st_ino)
        if key in visited_dirs:
            continue
        visited_dirs.add(key)

        try:
            children = sorted(current.iterdir(), key=lambda item: item.name)
        except OSError:
            continue
        for child in reversed(children):
            if excluded(child, root, excluded_parts, exclude_globs):
                continue
            try:
                if child.is_dir():
                    stack.append(child)
                elif child.is_file():
                    files.append(child)
            except OSError:
                continue
    return files


def find_broken_symlinks(
    root: Path,
    source_root: dict[str, Any],
    excluded_parts: set[str],
    exclude_globs: list[str],
) -> list[dict[str, str]]:
    broken: list[dict[str, str]] = []
    stack = [root]
    visited_dirs: set[tuple[int, int]] = set()

    while stack:
        current = stack.pop()
        if current.is_symlink() and not current.exists():
            broken.append(broken_symlink_entry(current, root, source_root))
            continue
        try:
            resolved = current.resolve()
            info = resolved.stat()
        except OSError:
            continue
        if not stat.S_ISDIR(info.st_mode):
            continue
        key = (info.st_dev, info.st_ino)
        if key in visited_dirs:
            continue
        visited_dirs.add(key)

        try:
            children = sorted(current.iterdir(), key=lambda item: item.name)
        except OSError:
            continue
        for child in reversed(children):
            if excluded(child, root, excluded_parts, exclude_globs):
                continue
            if child.is_symlink() and not child.exists():
                broken.append(broken_symlink_entry(child, root, source_root))
                continue
            try:
                if child.is_dir():
                    stack.append(child)
            except OSError:
                continue
    return broken


def broken_symlink_entry(path: Path, root: Path, source_root: dict[str, Any]) -> dict[str, str]:
    return {
        "sourceRoot": source_root["name"],
        "sourceRootPath": source_root["path"],
        "path": relative_to_root(path, root),
        "observedPath": str(path),
        "target": os.readlink(path),
    }


def excluded(path: Path, root: Path, excluded_parts: set[str], exclude_globs: list[str]) -> bool:
    try:
        relative = path.relative_to(root).as_posix()
    except ValueError:
        relative = path.name
    if any(part in excluded_parts for part in Path(relative).parts):
        return True
    return any(fnmatch.fnmatch(relative, pattern) for pattern in exclude_globs)


def classify_path(path: Path, observed_root: Path, source_root: dict[str, Any]) -> dict[str, Any] | None:
    relative = relative_to_root(path, observed_root)
    parts = Path(relative).parts
    name = path.name
    suffix = path.suffix.lower()
    hint = root_kind_hint(source_root, observed_root)

    if name == "SKILL.md":
        primitive_root = path.parent
        primitive_type = "SKILL"
        primitive_path = relative_to_root(primitive_root, observed_root)
        primitive_name = metadata_name(path) or slug(primitive_root.name)
        digest_path = primitive_root
    elif name == "plugin.json":
        primitive_root = path.parent
        primitive_type = "PLUGIN"
        primitive_path = relative_to_root(primitive_root, observed_root)
        primitive_name = json_name(path) or slug(primitive_root.name)
        digest_path = primitive_root
    elif is_hook_file(path, parts, suffix, hint):
        primitive_type = "HOOK"
        primitive_path = relative
        primitive_name = json_name(path) or hook_name(path)
        digest_path = path
    elif is_agent_file(path, parts, suffix, hint):
        primitive_type = "AGENT"
        primitive_path = relative
        primitive_name = metadata_name(path) or slug(path.stem.removesuffix(".agent"))
        digest_path = path
    elif is_instruction_file(path, parts, suffix, hint):
        primitive_type = "INSTRUCTION"
        primitive_path = relative
        primitive_name = metadata_name(path) or instruction_name(path)
        digest_path = path
    else:
        return None

    resolved_path = path.resolve()
    entry: dict[str, Any] = {
        "type": primitive_type,
        "name": primitive_name,
        "path": primitive_path,
        "entryFile": relative,
        "sourceRoot": source_root["name"],
        "sourceRootRole": source_root.get("role", "unspecified"),
        "sourceRootPath": source_root["path"],
        "observedPath": str(path),
        "resolvedPath": str(resolved_path),
        "sha256": digest(digest_path),
        "symlink": path.is_symlink() or any(parent.is_symlink() for parent in path.parents if parent != path.anchor),
    }
    if path.is_symlink():
        entry["symlinkTarget"] = os.readlink(path)
    title = markdown_title(path)
    if title:
        entry["title"] = title
    description = metadata_description(path)
    if description:
        entry["description"] = description
    if primitive_type == "SKILL":
        entry["expectedResources"] = expected_resources(path.parent)
    return entry


def relative_to_root(path: Path, root: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.name


def root_kind_hint(source_root: dict[str, Any], observed_root: Path) -> str | None:
    values = {
        slug(source_root.get("name", "")),
        slug(source_root.get("path", "")),
        slug(observed_root.name),
    }
    if any(value.endswith("agents") or "-agents" in value for value in values):
        return "AGENT"
    if any(value.endswith("hooks") or "-hooks" in value for value in values):
        return "HOOK"
    if any(value.endswith("instructions") or "-instructions" in value for value in values):
        return "INSTRUCTION"
    return None


def is_hook_file(path: Path, parts: tuple[str, ...], suffix: str, hint: str | None) -> bool:
    if path.name.endswith(".requirements.json"):
        return False
    if path.name.endswith((".hook.json", ".hooks.json")):
        return True
    if path.name == "hooks.json":
        return True
    return (hint == "HOOK" or "hooks" in parts) and suffix in HOOK_SUFFIXES and path.name != "README.md"


def is_agent_file(path: Path, parts: tuple[str, ...], suffix: str, hint: str | None) -> bool:
    return (hint == "AGENT" or "agents" in parts) and path.name != "AGENTS.md" and suffix in TEXT_SUFFIXES


def is_instruction_file(path: Path, parts: tuple[str, ...], suffix: str, hint: str | None) -> bool:
    if path.name in {"AGENTS.md", "CLAUDE.md", "copilot-instructions.md"}:
        return True
    return (hint == "INSTRUCTION" or bool({"instructions", "concepts", "standards"}.intersection(parts))) and suffix in {
        ".md",
        ".markdown",
        ".txt",
    }


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    if path.is_dir():
        for child in sorted(iter_digest_files(path), key=lambda item: item.relative_to(path).as_posix()):
            relative = child.relative_to(path).as_posix()
            hasher.update(relative.encode("utf-8"))
            hasher.update(b"\0")
            hasher.update(file_bytes(child))
            hasher.update(b"\0")
    else:
        hasher.update(file_bytes(path))
    return f"sha256:{hasher.hexdigest()}"


def iter_digest_files(root: Path) -> list[Path]:
    excluded_parts = {".git", ".gradle", ".idea", "__pycache__", "build", "dist", "node_modules", "target"}
    return [
        path
        for path in iter_files(root, excluded_parts, [])
        if path.is_file() and not path.name.endswith((".pyc", ".class"))
    ]


def file_bytes(path: Path) -> bytes:
    try:
        return path.read_bytes()
    except OSError:
        return b""


def expected_resources(skill_root: Path) -> list[str]:
    resources = []
    for path in iter_digest_files(skill_root):
        try:
            resources.append(path.relative_to(skill_root).as_posix())
        except ValueError:
            continue
    return sorted(resources)


def json_name(path: Path) -> str | None:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError, UnicodeDecodeError):
        return None
    value = data.get("name") if isinstance(data, dict) else None
    return slug(value) if isinstance(value, str) and value.strip() else None


def metadata_name(path: Path) -> str | None:
    return metadata(path).get("name")


def metadata_description(path: Path) -> str | None:
    return metadata(path).get("description")


def metadata(path: Path) -> dict[str, str]:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return {}
    if not text.startswith("---\n"):
        return {}
    end = text.find("\n---", 4)
    if end == -1:
        return {}
    result: dict[str, str] = {}
    for line in text[4:end].splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        value = value.strip().strip('"').strip("'")
        if key.strip() in {"name", "description"} and value:
            result[key.strip()] = slug(value) if key.strip() == "name" else value
    return result


def markdown_title(path: Path) -> str | None:
    if path.suffix.lower() not in {".md", ".markdown"}:
        return None
    try:
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith("# "):
                return line[2:].strip()
    except (OSError, UnicodeDecodeError):
        return None
    return None


def hook_name(path: Path) -> str:
    stem = path.name
    for suffix in (".hooks.json", ".hook.json"):
        if stem.endswith(suffix):
            stem = stem[: -len(suffix)]
            break
    else:
        stem = path.stem
    if stem == "hooks" and path.parent.name != "hooks":
        stem = f"{path.parent.name}-hooks"
    return slug(stem)


def instruction_name(path: Path) -> str:
    if path.name == "AGENTS.md":
        return slug(f"{path.parent.name}-agents-instructions")
    if path.name == "CLAUDE.md":
        return slug(f"{path.parent.name}-claude-instructions")
    if path.name == "core.md":
        return slug(path.parent.name)
    return slug(path.stem)


def slug(value: str) -> str:
    lowered = value.strip().lower()
    candidate = re.sub(r"[^a-z0-9]+", "-", lowered).strip("-")
    return candidate[:64].strip("-") or "unnamed"


def counts(entries: list[dict[str, Any]]) -> dict[str, int]:
    result: dict[str, int] = {}
    for entry in entries:
        result[entry["type"]] = result.get(entry["type"], 0) + 1
    return dict(sorted(result.items()))


def collision_groups(
    entries: list[dict[str, Any]],
    keys: tuple[str, ...],
    repo_root: Path,
) -> list[dict[str, Any]]:
    groups: dict[tuple[str, ...], list[dict[str, Any]]] = defaultdict(list)
    for entry in duplicate_review_candidates(entries, repo_root):
        groups[tuple(entry[key] for key in keys)].append(entry)
    result = []
    for key, values in groups.items():
        if len(values) <= 1:
            continue
        result.append(
            {
                "key": dict(zip(keys, key)),
                "entries": [entry_ref(entry) for entry in values],
            }
        )
    return sorted(result, key=lambda item: tuple(item["key"].values()))


def digest_groups(entries: list[dict[str, Any]], repo_root: Path) -> list[dict[str, Any]]:
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for entry in duplicate_review_candidates(entries, repo_root):
        groups[entry["sha256"]].append(entry)
    return [
        {"sha256": sha, "entries": [entry_ref(entry) for entry in values]}
        for sha, values in sorted(groups.items())
        if len(values) > 1
    ]


def duplicate_review_candidates(entries: list[dict[str, Any]], repo_root: Path) -> list[dict[str, Any]]:
    return [
        entry
        for entry in entries
        if not active_repo_runtime_alias(entry, repo_root)
    ]


def active_repo_runtime_alias(entry: dict[str, Any], repo_root: Path) -> bool:
    if not entry.get("symlink"):
        return False
    try:
        observed = Path(entry["observedPath"])
        resolved = Path(entry["resolvedPath"]).resolve(strict=False)
    except OSError:
        return False
    return not observed.is_relative_to(repo_root) and resolved.is_relative_to(repo_root)


def entry_ref(entry: dict[str, Any]) -> dict[str, str]:
    return {
        "type": entry["type"],
        "name": entry["name"],
        "sourceRoot": entry["sourceRoot"],
        "path": entry["path"],
        "resolvedPath": entry["resolvedPath"],
    }


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


if __name__ == "__main__":
    raise SystemExit(main())
