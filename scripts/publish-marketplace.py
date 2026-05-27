#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any


CODEX_PLUGIN_DIR = ".codex-plugin"
CODEX_PROVIDER_DIR = "codex"
GITHUB_COPILOT_PROVIDER_PATH = Path(".github") / "plugin"
MAIN_BRANCH_PROVIDER_PATHS = [
    Path(CODEX_PROVIDER_DIR),
    GITHUB_COPILOT_PROVIDER_PATH,
]
CHECKED_IN_PROJECTION_GENERATED_AT = "1970-01-01T00:00:00Z"
CHECKED_IN_PROJECTION_SOURCE_SHA = "0000000000000000000000000000000000000000"
HOOK_COMMAND_PATH_RE = re.compile(r"\bhooks/[A-Za-z0-9_.-]+")
PRIMITIVE_COLLECTIONS = {
    "SKILL": "skills",
    "AGENT": "agents",
    "HOOK": "hooks",
    "INSTRUCTION": "instructions",
}


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Materialize and publish the hydrated Intelligence marketplace."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    materialize = subparsers.add_parser("materialize")
    materialize.add_argument("--out", required=True, help="Output directory to replace.")

    sync_github = subparsers.add_parser("sync-github-plugin")
    sync_github.add_argument(
        "--check",
        action="store_true",
        help="Fail if .github/plugin does not match generated output.",
    )

    sync_main = subparsers.add_parser("sync-main-projections")
    sync_main.add_argument(
        "--check",
        action="store_true",
        help="Fail if codex/ and .github/plugin do not match generated output.",
    )

    publish = subparsers.add_parser("publish-branch")
    publish.add_argument("--branch", default="marketplace")
    publish.add_argument("--no-push", action="store_true")

    args = parser.parse_args()
    repo_root = Path.cwd().resolve()

    if args.command == "materialize":
        materialize_marketplace(repo_root, Path(args.out).resolve())
        return 0

    if args.command == "sync-github-plugin":
        sync_github_plugin(repo_root, check=args.check)
        return 0

    if args.command == "sync-main-projections":
        sync_main_projections(repo_root, check=args.check)
        return 0

    with tempfile.TemporaryDirectory(prefix="intelligence-marketplace-") as temp:
        temp_root = Path(temp)
        materialized = temp_root / "materialized"
        worktree = temp_root / "worktree"
        local_branch = f"intelligence-marketplace-publish-{os.getpid()}"
        materialize_marketplace(repo_root, materialized)
        run(["git", "worktree", "add", "--detach", str(worktree), "HEAD"], repo_root)
        try:
            run(["git", "checkout", "--orphan", local_branch], worktree)
            clear_worktree(worktree)
            copy_tree_contents(materialized, worktree)
            run(["git", "config", "user.name", "github-actions[bot]"], worktree)
            run(
                [
                    "git",
                    "config",
                    "user.email",
                    "41898282+github-actions[bot]@users.noreply.github.com",
                ],
                worktree,
            )
            run(["git", "add", "-A"], worktree)
            run(["git", "commit", "-m", "Publish Intelligence Marketplace"], worktree)
            if not args.no_push:
                run(["git", "push", "--force", "origin", f"HEAD:{args.branch}"], worktree)
        finally:
            subprocess.run(["git", "worktree", "remove", "--force", str(worktree)], cwd=repo_root)
            subprocess.run(["git", "worktree", "prune"], cwd=repo_root)
            subprocess.run(["git", "branch", "-D", local_branch], cwd=repo_root)
        if args.no_push:
            print(f"prepared marketplace branch {args.branch} without pushing")
        else:
            print(f"published marketplace branch {args.branch}")
        return 0


def materialize_marketplace(
    repo_root: Path,
    out_root: Path,
    *,
    generated_at: str | None = None,
    source_sha: str | None = None,
) -> None:
    if out_root == repo_root:
        raise SystemExit("refusing to materialize over repository root")
    if out_root.exists():
        shutil.rmtree(out_root)
    out_root.mkdir(parents=True)

    marketplace = read_json(repo_root / "marketplace.json")
    owner = marketplace.get("owner", {})
    owner_name = owner.get("name", "Local developer")
    codex_root = out_root / CODEX_PROVIDER_DIR
    github_copilot_root = out_root / GITHUB_COPILOT_PROVIDER_PATH
    codex_marketplace = {
        "name": marketplace["name"],
        "interface": {"displayName": title_case(marketplace["name"])},
        "plugins": [],
    }
    github_copilot_marketplace = {
        "$schema": "github-marketplace.schema.json",
        "name": marketplace["name"],
        "owner": person_for(owner, owner_name),
        "metadata": {
            "description": marketplace.get("description", ""),
            "pluginRoot": "./plugins",
        },
        "plugins": [],
    }
    lock = {
        "type": "HYDRATED_MARKETPLACE",
        "schemaVersion": 1,
        "generatedAt": generated_at or current_source_timestamp(repo_root),
        "sourceSha": source_sha or current_sha(repo_root),
        "plugins": [],
    }

    for entry in marketplace.get("plugins", []):
        plugin_ref = entry["plugin"]
        source_path = Path(plugin_ref["source"]["path"])
        plugin_manifest = read_json(repo_root / source_path / "plugin.json")
        plugin_name = plugin_manifest["name"]
        codex_plugin_out = codex_root / "plugins" / plugin_name
        github_copilot_plugin_out = github_copilot_root / "plugins" / plugin_name
        codex_plugin_out.mkdir(parents=True, exist_ok=True)
        github_copilot_plugin_out.mkdir(parents=True, exist_ok=True)

        description = (
            plugin_manifest.get("description")
            or entry.get("description")
            or f"{plugin_name} plugin."
        )
        tags = entry.get("tags", [])
        category = category_for(tags)
        codex_hydrated = hydrate_plugin(repo_root, codex_plugin_out, plugin_manifest)
        github_copilot_hydrated = hydrate_plugin(
            repo_root,
            github_copilot_plugin_out,
            plugin_manifest,
        )
        render_agents_md_adapter(codex_plugin_out, plugin_name, description, codex_hydrated)
        render_agents_md_adapter(
            github_copilot_plugin_out,
            plugin_name,
            description,
            github_copilot_hydrated,
        )

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
        github_copilot_marketplace["plugins"].append(
            github_copilot_plugin_entry(
                plugin_name=plugin_name,
                description=description,
                version=plugin_manifest.get("version", plugin_ref.get("version", "0.1.0")),
                owner=person_for(owner, owner_name),
                tags=tags,
                category=category,
                hydrated=github_copilot_hydrated,
            )
        )

        codex_manifest = {
            "name": plugin_name,
            "version": plugin_manifest.get("version", plugin_ref.get("version", "0.1.0")),
            "description": description,
            "author": {"name": owner_name},
            "license": "UNLICENSED",
            "keywords": sorted(set([plugin_name, *tags])),
            "interface": {
                "displayName": title_case(plugin_name),
                "shortDescription": short_description(description),
                "longDescription": description,
                "developerName": owner_name,
                "category": category,
                "capabilities": capabilities_for(codex_hydrated),
                "brandColor": brand_color_for(category),
                "defaultPrompt": default_prompts(plugin_name),
            },
        }
        if codex_hydrated["skills"]:
            codex_manifest["skills"] = "./skills/"

        write_json(codex_plugin_out / CODEX_PLUGIN_DIR / "plugin.json", codex_manifest)
        lock["plugins"].append(
            {
                "name": plugin_name,
                "version": codex_manifest["version"],
                "sourceManifest": f"{source_path.as_posix()}/plugin.json",
                "references": codex_hydrated["references"],
            }
        )

    write_json(codex_root / "marketplace.json", codex_marketplace)
    write_json(codex_root / "marketplace-lock.json", lock)
    write_json(github_copilot_root / "marketplace.json", github_copilot_marketplace)
    (out_root / "README.md").write_text(
        "# Intelligence Marketplace\n\n"
        "This branch is generated from the referential source graph on `main`.\n\n"
        "Provider-native marketplace projections are scoped under their expected entrypoints:\n\n"
        "- `codex/marketplace.json`\n"
        "- `.github/plugin/marketplace.json`\n\n"
        "Each provider entrypoint owns its own `plugins/` payloads so marketplace-relative "
        "paths stay provider-native.\n",
        encoding="utf-8",
    )
    print(f"materialized marketplace at {out_root}")


def sync_github_plugin(repo_root: Path, *, check: bool) -> None:
    sync_provider_paths(
        repo_root,
        paths=[GITHUB_COPILOT_PROVIDER_PATH],
        check=check,
        command="sync-github-plugin",
    )


def sync_main_projections(repo_root: Path, *, check: bool) -> None:
    sync_provider_paths(
        repo_root,
        paths=MAIN_BRANCH_PROVIDER_PATHS,
        check=check,
        command="sync-main-projections",
    )


def sync_provider_paths(
    repo_root: Path,
    *,
    paths: list[Path],
    check: bool,
    command: str,
) -> None:
    with tempfile.TemporaryDirectory(prefix="intelligence-provider-projections-") as temp:
        materialized = Path(temp) / "marketplace"
        materialize_marketplace(
            repo_root,
            materialized,
            generated_at=CHECKED_IN_PROJECTION_GENERATED_AT,
            source_sha=CHECKED_IN_PROJECTION_SOURCE_SHA,
        )

        if check:
            missing_or_changed = []
            for provider_path in paths:
                source = materialized / provider_path
                target = repo_root / provider_path
                if not target.exists() or sync_digest_path(source) != sync_digest_path(target):
                    missing_or_changed.append(provider_path.as_posix())
            if missing_or_changed:
                changed = ", ".join(missing_or_changed)
                raise SystemExit(
                    f"{changed} not in sync; run `python3 scripts/publish-marketplace.py {command}`"
                )
            print(f"OK {', '.join(path.as_posix() for path in paths)} in sync")
            return

        for provider_path in paths:
            copy_path(materialized / provider_path, repo_root / provider_path)
            print(f"synced provider marketplace at {provider_path.as_posix()}")


def person_for(owner: dict[str, Any], fallback_name: str) -> dict[str, str]:
    person = {"name": owner.get("name", fallback_name)}
    for key in ("email", "url"):
        if owner.get(key):
            person[key] = owner[key]
    return person


def github_copilot_plugin_entry(
    *,
    plugin_name: str,
    description: str,
    version: str,
    owner: dict[str, str],
    tags: list[str],
    category: str,
    hydrated: dict[str, Any],
) -> dict[str, Any]:
    plugin_entry: dict[str, Any] = {
        "name": plugin_name,
        "source": f"./plugins/{plugin_name}",
        "description": description,
        "version": version,
        "author": owner,
        "license": "UNLICENSED",
        "keywords": sorted(set([plugin_name, *tags])),
        "category": category,
        "tags": tags,
        "strict": True,
    }
    if hydrated["agents"]:
        plugin_entry["agents"] = "./agents"
    if hydrated["skills"]:
        plugin_entry["skills"] = "./skills"
    if hydrated["hooks"]:
        plugin_entry["hooks"] = "./hooks"
    return plugin_entry


def hydrate_plugin(repo_root: Path, plugin_out: Path, plugin_manifest: dict[str, Any]) -> dict[str, Any]:
    queued: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    hydrated = {"skills": set(), "agents": set(), "hooks": set(), "instructions": set(), "references": []}

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
        copy_primitive(repo_root, plugin_out, primitive, hydrated)

    for key in ("skills", "agents", "hooks", "instructions"):
        hydrated[key] = sorted(hydrated[key])
    hydrated["references"].sort(key=lambda item: (item["type"], item["sourcePath"], item["targetPath"]))
    return hydrated


def render_agents_md_adapter(
    plugin_out: Path,
    plugin_name: str,
    description: str,
    hydrated: dict[str, Any],
) -> None:
    if not hydrated["agents"] and not hydrated["instructions"]:
        return

    references_by_type: dict[str, list[dict[str, Any]]] = {
        primitive_type: [
            reference
            for reference in hydrated["references"]
            if reference["type"] == primitive_type
        ]
        for primitive_type in ("INSTRUCTION", "AGENT", "SKILL", "HOOK")
    }

    lines = [
        f"# {title_case(plugin_name)} Plugin Instructions",
        "",
        "## Scope",
        "",
        (
            f"This generated adapter applies to the `{plugin_name}` plugin payload. "
            "Do not edit it directly; update the provider-neutral primitives or "
            "plugin manifest, then regenerate the marketplace output."
        ),
        "",
        "## Runtime Boundary",
        "",
        (
            "The source graph keeps skills, agent profiles, instructions, concepts, "
            "and hooks as independent primitives. This `AGENTS.md` adapts bundled "
            "agent and instruction primitives into a plain instruction file for "
            "runtimes that do not expose those primitive kinds directly."
        ),
        "",
        "## Plugin Intent",
        "",
        description,
        "",
        "## Operating Rules",
        "",
        "- Treat this file as an adapter, not a new source of truth.",
        "- Use bundled skills for step-by-step workflows.",
        "- Apply bundled instructions as normative guidance when their scope matches the task.",
        "- Treat bundled agent profiles as review criteria or focused review passes.",
        "- Keep hook behavior in bundled hook files and runtime adapter configs.",
        "- When guidance conflicts with the target repository's nearest `AGENTS.md`, follow the target repository unless the user explicitly chooses this plugin's rule.",
        "",
    ]

    append_reference_section(lines, "Instruction Primitives", references_by_type["INSTRUCTION"])
    append_reference_section(lines, "Agent Profile Primitives", references_by_type["AGENT"])
    append_reference_section(lines, "Skill Primitives", references_by_type["SKILL"])
    append_reference_section(lines, "Hook Primitives", references_by_type["HOOK"])

    (plugin_out / "AGENTS.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def append_reference_section(lines: list[str], title: str, references: list[dict[str, Any]]) -> None:
    if not references:
        return
    lines.extend([f"## {title}", ""])
    for reference in references:
        lines.append(
            f"- `{reference['name']}`: `{reference['targetPath']}` "
            f"(source: `{reference['sourcePath']}`)"
        )
    lines.append("")


def copy_primitive(
    repo_root: Path,
    plugin_out: Path,
    primitive: dict[str, Any],
    hydrated: dict[str, Any],
) -> None:
    source = primitive.get("source", {})
    if source.get("type") != "LOCAL_SOURCE" or source.get("path") != "./":
        raise SystemExit(f"unsupported non-local primitive reference: {primitive}")

    primitive_type = primitive["type"]
    source_path = repo_root / primitive["path"]
    if not source_path.exists():
        raise SystemExit(f"missing primitive path: {primitive['path']}")

    if primitive_type == "HOOK":
        copy_hook(repo_root, plugin_out, primitive, source_path, hydrated)
        return

    target_path = target_for_primitive(plugin_out, primitive_type, primitive, source_path)
    copy_path(source_path, target_path)
    hydrated[PRIMITIVE_COLLECTIONS[primitive_type]].add(target_path.relative_to(plugin_out).as_posix())
    hydrated["references"].append(
        {
            "type": primitive_type,
            "name": primitive.get("name"),
            "sourcePath": primitive["path"],
            "targetPath": target_path.relative_to(plugin_out).as_posix(),
            "sha256": digest_path(target_path),
        }
    )

    if primitive_type == "AGENT" and target_path.suffix == ".md":
        render_agent_toml(target_path)


def copy_hook(
    repo_root: Path,
    plugin_out: Path,
    primitive: dict[str, Any],
    source_path: Path,
    hydrated: dict[str, Any],
) -> None:
    metadata = read_json(source_path)
    adapter_source = repo_root / metadata.get("path", primitive["path"])
    if not adapter_source.exists():
        raise SystemExit(f"missing hook adapter path: {metadata.get('path')}")

    metadata_target = plugin_out / "hooks" / "metadata" / source_path.name
    adapter_target = plugin_out / "hooks" / adapter_source.name
    copy_path(source_path, metadata_target)
    copy_path(adapter_source, adapter_target)

    hydrated["hooks"].add(adapter_target.relative_to(plugin_out).as_posix())
    hydrated["references"].append(
        {
            "type": "HOOK",
            "name": primitive.get("name"),
            "sourcePath": primitive["path"],
            "targetPath": adapter_target.relative_to(plugin_out).as_posix(),
            "sha256": digest_path(adapter_target),
        }
    )

    adapter = read_json(adapter_source)
    for command_path in sorted(hook_command_paths(adapter)):
        source = repo_root / command_path
        if not source.exists():
            continue
        target = plugin_out / command_path
        copy_path(source, target)
        copy_hook_sidecars(repo_root, plugin_out, source)


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


def copy_hook_sidecars(repo_root: Path, plugin_out: Path, hook_script: Path) -> None:
    stem = hook_script.stem
    for sidecar in sorted(hook_script.parent.glob(f"{stem}.*.json")):
        if sidecar.name.endswith(".hook.json"):
            continue
        copy_path(sidecar, plugin_out / "hooks" / sidecar.name)


def target_for_primitive(
    plugin_out: Path,
    primitive_type: str,
    primitive: dict[str, Any],
    source_path: Path,
) -> Path:
    if primitive_type == "SKILL":
        return plugin_out / "skills" / primitive["name"]
    if primitive_type == "AGENT":
        return plugin_out / "agents" / source_path.name
    if primitive_type == "INSTRUCTION":
        suffix = source_path.suffix or ".md"
        return plugin_out / "instructions" / f"{primitive['name']}{suffix}"
    raise SystemExit(f"unsupported primitive type: {primitive_type}")


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


def copy_tree_contents(source: Path, target: Path) -> None:
    for child in source.iterdir():
        copy_path(child, target / child.name)


def clear_worktree(worktree: Path) -> None:
    for child in worktree.iterdir():
        if child.name == ".git":
            continue
        if child.is_dir() and not child.is_symlink():
            shutil.rmtree(child)
        else:
            child.unlink()


def render_agent_toml(markdown: Path) -> None:
    lines = markdown.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != "---":
        return
    try:
        end = lines[1:].index("---") + 1
    except ValueError:
        return
    frontmatter = {}
    for line in lines[1:end]:
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        frontmatter[key.strip()] = value.strip().strip("'\"")
    name = frontmatter.get("name")
    description = frontmatter.get("description")
    instructions = "\n".join(lines[end + 1 :]).strip() + "\n"
    if not name or not description or not instructions.strip():
        return
    markdown.with_suffix(".toml").write_text(
        "\n".join(
            [
                f"name = {json.dumps(name)}",
                f"description = {json.dumps(description)}",
                f"developer_instructions = {json.dumps(instructions)}",
                "",
            ]
        ),
        encoding="utf-8",
    )


def run(command: list[str], cwd: Path) -> None:
    print("+ " + " ".join(command))
    subprocess.run(command, cwd=cwd, check=True)


def current_sha(repo_root: Path) -> str | None:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=repo_root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        return None
    return result.stdout.strip()


def current_source_timestamp(repo_root: Path) -> str:
    result = subprocess.run(
        ["git", "show", "-s", "--format=%cI", "HEAD"],
        cwd=repo_root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        return "1970-01-01T00:00:00+00:00"
    return result.stdout.strip()


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def digest_path(path: Path) -> str:
    import hashlib

    hasher = hashlib.sha256()
    if path.is_dir():
        for child in sorted(item for item in path.rglob("*") if item.is_file()):
            relative = child.relative_to(path).as_posix()
            hasher.update(relative.encode())
            hasher.update(b"\0")
            hasher.update(child.read_bytes())
            hasher.update(b"\0")
    else:
        hasher.update(path.read_bytes())
    return f"sha256:{hasher.hexdigest()}"


def sync_digest_path(path: Path) -> str:
    import hashlib
    import stat

    hasher = hashlib.sha256()
    if path.is_dir():
        for child in sorted(item for item in path.rglob("*") if item.is_file()):
            relative = child.relative_to(path).as_posix()
            mode = stat.S_IMODE(child.stat().st_mode)
            hasher.update(relative.encode())
            hasher.update(b"\0")
            hasher.update(f"{mode:o}".encode())
            hasher.update(b"\0")
            hasher.update(child.read_bytes())
            hasher.update(b"\0")
    else:
        mode = stat.S_IMODE(path.stat().st_mode)
        hasher.update(f"{mode:o}".encode())
        hasher.update(b"\0")
        hasher.update(path.read_bytes())
    return f"sha256:{hasher.hexdigest()}"


def category_for(tags: list[str]) -> str:
    tag_set = set(tags)
    if tag_set & {"git", "github", "ci", "schemas", "audit", "governance"}:
        return "Engineering"
    if tag_set & {"kotlin", "testing"}:
        return "Coding"
    return "Productivity"


def capabilities_for(hydrated: dict[str, Any]) -> list[str]:
    capabilities = ["Interactive"]
    if hydrated["skills"] or hydrated["hooks"]:
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
    acronyms = {
        "api": "API",
        "ci": "CI",
        "cli": "CLI",
        "github": "GitHub",
        "json": "JSON",
        "pr": "PR",
        "prs": "PRs",
        "tdd": "TDD",
        "yaml": "YAML",
    }
    return " ".join(
        acronyms.get(part.lower(), part.capitalize())
        for part in value.replace("_", "-").split("-")
        if part
    )


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as error:
        raise SystemExit(error.returncode)
