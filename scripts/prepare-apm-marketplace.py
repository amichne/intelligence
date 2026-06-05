#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any


SOURCE_ROOT = Path("source")
MARKETPLACE_PATH = SOURCE_ROOT / "adaptable.marketplace.json"
PLUGIN_SOURCE_ROOT = SOURCE_ROOT / "plugins"
DEFAULT_STAGE_ROOT = Path("build") / "apm-marketplace"
DEFAULT_VERSION = "0.1.0"
HOOK_COMMAND_PATH_RE = re.compile(r"\bhooks/[A-Za-z0-9_.-]+")
PRIMITIVE_COLLECTIONS = {
    "SKILL": "skills",
    "AGENT": "agents",
    "HOOK": "hooks",
    "INSTRUCTION": "instructions",
}


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Prepare the APM-native Intelligence marketplace workspace."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    manifest = subparsers.add_parser("manifest", help="Write or check the root APM manifest.")
    manifest.add_argument("--out", default="apm.yml", help="Manifest path to write or check.")
    manifest.add_argument("--check", action="store_true", help="Fail if the manifest is stale.")

    stage = subparsers.add_parser("stage", help="Create an untracked APM marketplace workspace.")
    stage.add_argument("--out", default=DEFAULT_STAGE_ROOT.as_posix(), help="Workspace directory.")
    stage.add_argument(
        "--check-root-manifest",
        action="store_true",
        help="Fail if apm.yml does not match the generated APM manifest.",
    )

    args = parser.parse_args()
    repo_root = Path.cwd().resolve()

    if args.command == "manifest":
        manifest_text = render_manifest(repo_root)
        manifest_path = (repo_root / args.out).resolve()
        if args.check:
            return check_text(manifest_path, manifest_text)
        manifest_path.write_text(manifest_text, encoding="utf-8")
        print(f"wrote {manifest_path.relative_to(repo_root)}")
        return 0

    if args.check_root_manifest:
        root_check = check_text(repo_root / "apm.yml", render_manifest(repo_root))
        if root_check != 0:
            return root_check

    stage_marketplace(repo_root, (repo_root / args.out).resolve())
    return 0


def render_manifest(repo_root: Path) -> str:
    marketplace = read_json(repo_root / MARKETPLACE_PATH)
    owner = marketplace.get("owner", {})
    owner_name = owner.get("name", "Amichne")

    payload: dict[str, Any] = {
        "name": marketplace["name"],
        "version": marketplace.get("version") or DEFAULT_VERSION,
        "description": marketplace.get("description", ""),
        "author": owner_name,
        "license": "Apache-2.0",
        "includes": "auto",
        "marketplace": {
            "owner": person_for(owner, owner_name),
            "outputs": {
                "claude": {"path": "marketplace.json"},
                "codex": {"path": "codex-marketplace.json"},
            },
            "claude": {"output": "marketplace.json"},
            "codex": {"output": "codex-marketplace.json"},
            "versioning": {"strategy": "per_package"},
            "packages": marketplace_packages(repo_root, marketplace, owner_name),
        },
    }
    return json.dumps(payload, indent=2, sort_keys=False) + "\n"


def marketplace_packages(
    repo_root: Path,
    marketplace: dict[str, Any],
    owner_name: str,
) -> list[dict[str, Any]]:
    packages: list[dict[str, Any]] = []
    for entry in marketplace.get("plugins", []):
        plugin_ref = entry["plugin"]
        source_path = Path(plugin_ref["source"]["path"])
        manifest = read_json(resolve_source_path(repo_root, source_path) / "plugin.json")
        name = manifest["name"]
        description = manifest.get("description") or entry.get("description") or f"{name} plugin."
        tags = entry.get("tags", [])
        packages.append(
            {
                "name": name,
                "source": f"./packages/{name}",
                "version": manifest.get("version", plugin_ref.get("version", DEFAULT_VERSION)),
                "description": description,
                "tags": tags,
                "author": owner_name,
                "license": "Apache-2.0",
                "repository": "https://github.com/amichne/intelligence",
                "category": category_for(tags),
            }
        )
    return packages


def stage_marketplace(repo_root: Path, out_root: Path) -> None:
    if out_root == repo_root:
        raise SystemExit("refusing to stage over repository root")
    if out_root.exists():
        shutil.rmtree(out_root)
    out_root.mkdir(parents=True)

    (out_root / "apm.yml").write_text(render_manifest(repo_root), encoding="utf-8")
    marketplace = read_json(repo_root / MARKETPLACE_PATH)
    owner_name = marketplace.get("owner", {}).get("name", "Amichne")

    for entry in marketplace.get("plugins", []):
        plugin_ref = entry["plugin"]
        source_path = Path(plugin_ref["source"]["path"])
        source_dir = resolve_source_path(repo_root, source_path)
        plugin_manifest = read_json(source_dir / "plugin.json")
        package_dir = out_root / "packages" / plugin_manifest["name"]
        package_dir.mkdir(parents=True)
        write_package_manifest(package_dir, plugin_manifest, plugin_ref, entry, owner_name)
        hydrate_plugin(repo_root, package_dir, plugin_manifest)

    (out_root / "README.md").write_text(
        "# Intelligence APM Marketplace\n\n"
        "This workspace is generated from the checked source graph. Do not edit it directly.\n",
        encoding="utf-8",
    )
    print(f"prepared APM marketplace workspace at {out_root}")


def write_package_manifest(
    package_dir: Path,
    plugin_manifest: dict[str, Any],
    plugin_ref: dict[str, Any],
    marketplace_entry: dict[str, Any],
    owner_name: str,
) -> None:
    name = plugin_manifest["name"]
    payload = {
        "name": name,
        "version": plugin_manifest.get("version", plugin_ref.get("version", DEFAULT_VERSION)),
        "description": (
            plugin_manifest.get("description")
            or marketplace_entry.get("description")
            or f"{name} plugin."
        ),
        "author": owner_name,
        "license": "Apache-2.0",
        "type": "hybrid",
        "target": ["claude", "codex"],
        "includes": "auto",
    }
    write_json(package_dir / "apm.yml", payload)


def hydrate_plugin(repo_root: Path, package_dir: Path, plugin_manifest: dict[str, Any]) -> None:
    queued: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()

    def enqueue(primitive: dict[str, Any]) -> None:
        primitive_type = primitive.get("type")
        path_value = primitive.get("path")
        if primitive_type not in PRIMITIVE_COLLECTIONS or not path_value:
            return
        key = (primitive_type, path_value)
        if key in seen:
            return
        seen.add(key)
        queued.append(primitive)

    for collection in PRIMITIVE_COLLECTIONS.values():
        for primitive in plugin_manifest.get(collection, []):
            enqueue(primitive)

    while queued:
        primitive = queued.pop(0)
        for dependency in primitive.get("dependsOn", []):
            enqueue(dependency)
        copy_primitive(repo_root, package_dir, primitive)


def copy_primitive(repo_root: Path, package_dir: Path, primitive: dict[str, Any]) -> None:
    source = primitive.get("source", {})
    if source.get("type") != "LOCAL_SOURCE" or source.get("path") != "./":
        raise SystemExit(f"unsupported non-local primitive reference: {primitive}")

    primitive_type = primitive["type"]
    source_path = resolve_source_path(repo_root, primitive["path"])
    if not source_path.exists():
        raise SystemExit(f"missing primitive path: {primitive['path']}")

    if primitive_type == "HOOK":
        copy_hook(repo_root, package_dir, source_path)
        return

    target_path = target_for_primitive(package_dir, primitive_type, primitive, source_path)
    copy_path(source_path, target_path)


def copy_hook(repo_root: Path, package_dir: Path, source_path: Path) -> None:
    metadata = read_json(source_path)
    adapter_source = resolve_source_path(repo_root, metadata.get("path", source_path.as_posix()))
    if not adapter_source.exists():
        raise SystemExit(f"missing hook adapter path: {metadata.get('path')}")

    metadata_target = package_dir / "hooks" / "metadata" / source_path.name
    adapter_target = package_dir / ".apm" / "hooks" / adapter_source.name
    copy_path(source_path, metadata_target)
    copy_path(adapter_source, adapter_target)

    for command_path in sorted(hook_command_paths(read_json(adapter_source))):
        script_source = resolve_source_path(repo_root, command_path)
        if not script_source.exists():
            continue
        copy_path(script_source, package_dir / command_path)
        copy_hook_sidecars(repo_root, package_dir, script_source)


def hook_command_paths(payload: Any) -> set[str]:
    paths: set[str] = set()
    if isinstance(payload, dict):
        for key, value in payload.items():
            if key == "command" and isinstance(value, str):
                paths.update(match.group(0) for match in HOOK_COMMAND_PATH_RE.finditer(value))
            else:
                paths.update(hook_command_paths(value))
    elif isinstance(payload, list):
        for item in payload:
            paths.update(hook_command_paths(item))
    return paths


def copy_hook_sidecars(repo_root: Path, package_dir: Path, hook_script: Path) -> None:
    stem = hook_script.stem
    for sidecar in sorted(hook_script.parent.glob(f"{stem}.*.json")):
        if sidecar.name.endswith(".hook.json"):
            continue
        copy_path(sidecar, package_dir / "hooks" / sidecar.name)


def target_for_primitive(
    package_dir: Path,
    primitive_type: str,
    primitive: dict[str, Any],
    source_path: Path,
) -> Path:
    if primitive_type == "SKILL":
        return package_dir / ".apm" / "skills" / primitive["name"]
    if primitive_type == "AGENT":
        return package_dir / ".apm" / "agents" / source_path.name
    if primitive_type == "INSTRUCTION":
        return package_dir / ".apm" / "instructions" / f"{primitive['name']}.instructions.md"
    raise SystemExit(f"unsupported primitive type: {primitive_type}")


def category_for(tags: list[str]) -> str:
    tag_set = set(tags)
    if tag_set & {"git", "github", "ci", "schemas", "audit", "governance"}:
        return "Engineering"
    if tag_set & {"kotlin", "testing"}:
        return "Coding"
    return "Productivity"


def person_for(owner: dict[str, Any], fallback_name: str) -> dict[str, str]:
    person = {"name": owner.get("name", fallback_name)}
    for key in ("email", "url"):
        if owner.get(key):
            person[key] = owner[key]
    return person


def resolve_source_path(repo_root: Path, path_value: str | Path) -> Path:
    path = Path(path_value)
    if path.is_absolute():
        raise SystemExit(f"source paths must be repository-relative: {path_value}")
    if path.parts and path.parts[0] == SOURCE_ROOT.as_posix():
        return repo_root / path
    return repo_root / SOURCE_ROOT / path


def copy_path(source: Path, target: Path) -> None:
    if target.exists() or target.is_symlink():
        if target.is_dir() and not target.is_symlink():
            shutil.rmtree(target)
        else:
            target.unlink()
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.is_dir():
        shutil.copytree(source, target, symlinks=False, ignore=copy_ignore)
    else:
        shutil.copy2(source, target, follow_symlinks=True)


def copy_ignore(_directory: str, names: list[str]) -> set[str]:
    ignored = {".git", ".gradle", ".idea", "__pycache__", "build", "dist", "node_modules", "target"}
    return {name for name in names if name in ignored or name.endswith(".pyc")}


def check_text(path: Path, expected: str) -> int:
    if not path.exists():
        print(f"missing: {path}", file=sys.stderr)
        return 1
    actual = path.read_text(encoding="utf-8")
    if actual == expected:
        print(f"OK {path.name} is current")
        return 0
    print(f"out of date: {path}", file=sys.stderr)
    print(f"run: python3 scripts/prepare-apm-marketplace.py manifest --out {path}", file=sys.stderr)
    return 1


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
