#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
PROFILE_DIR = REPO_ROOT / "profiles"
TEMPLATE_DIR = REPO_ROOT / "templates" / "primitives"
MARKETPLACE_PATH = REPO_ROOT / "adaptable.marketplace.json"

PRIMITIVE_COLLECTIONS = {
    "skill": "skills",
    "agent": "agents",
    "hook": "hooks",
}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="intelligence",
        description="Orchestrate Intelligence workflow profiles, primitive scaffolds, and validation.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    profile = subparsers.add_parser("profile", help="Create or inspect workflow profiles.")
    profile_subparsers = profile.add_subparsers(dest="profile_command", required=True)
    profile_init = profile_subparsers.add_parser("init", help="Write a repo-local workflow profile and marketplace reference.")
    add_repo_arg(profile_init)
    profile_init.add_argument("--profile", default="kotlin-repo-default", help="Built-in profile name or profile JSON path.")
    profile_init.add_argument("--out", default=".agents/intelligence-profile.json", help="Profile path to write relative to --repo.")
    profile_init.add_argument("--plugin", action="append", default=[], help="Additional marketplace plugin name. Repeatable.")
    profile_init.add_argument("--hook", action="append", default=[], help="Additional hook selection as NAME or NAME@ADAPTER. Repeatable.")
    profile_init.add_argument("--dry-run", action="store_true", help="Print planned writes without changing files.")
    profile_init.add_argument("--no-marketplaces-doc", action="store_true", help="Do not create or update .agents/marketplaces.md.")
    profile_init.set_defaults(func=cmd_profile_init)

    install = subparsers.add_parser("install", help="Install or dry-run a workflow profile for a target repo.")
    add_repo_arg(install)
    install.add_argument("--profile", default=".agents/intelligence-profile.json", help="Target repo profile path or built-in profile name.")
    install.add_argument("--apply", action="store_true", help="Perform approved writes. Omit for dry-run.")
    install.add_argument("--skip-validation", action="store_true", help="Skip source validation after --apply.")
    install.set_defaults(func=cmd_install)

    primitive = subparsers.add_parser("primitive", help="Scaffold and optionally reference primitives.")
    primitive_subparsers = primitive.add_subparsers(dest="primitive_command", required=True)
    primitive_new = primitive_subparsers.add_parser("new", help="Create a new primitive from repo templates.")
    primitive_new.add_argument("kind", choices=["skill", "agent", "hook", "plugin"])
    primitive_new.add_argument("name", help="Kebab-case primitive or plugin name.")
    primitive_new.add_argument("--description", default=None, help="Short description for metadata and docs.")
    primitive_new.add_argument("--plugin", action="append", default=[], help="Plugin manifest to update with this primitive. Repeatable.")
    primitive_new.add_argument("--marketplace", action="store_true", help="Add the new plugin or primitive to adaptable.marketplace.json.")
    primitive_new.add_argument("--dry-run", action="store_true", help="Print planned writes without changing files.")
    primitive_new.add_argument("--force", action="store_true", help="Overwrite scaffold target files if they already exist.")
    primitive_new.add_argument("--validate", action="store_true", help="Run manifest validation after writing.")
    primitive_new.set_defaults(func=cmd_primitive_new)

    validate = subparsers.add_parser("validate", help="Run the Intelligence manifest validation gates.")
    validate.add_argument("--manifests-only", action="store_true", help="Run only node scripts/validate-manifests.mjs.")
    validate.set_defaults(func=cmd_validate)

    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except IntelligenceError as error:
        print(f"intelligence: {error}", file=sys.stderr)
        return 1


def add_repo_arg(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--repo", default=".", help="Target repository root.")


class IntelligenceError(Exception):
    pass


def cmd_profile_init(args: argparse.Namespace) -> int:
    target_repo = target_repo_path(args.repo)
    profile = load_profile(args.profile, target_repo)
    profile = merge_profile_overrides(profile, args.plugin, args.hook)
    validate_profile_references(profile)

    profile_out = resolve_target_path(target_repo, args.out)
    writes = [(profile_out, json.dumps(profile, indent=2, sort_keys=True) + "\n")]
    if not args.no_marketplaces_doc:
        marketplaces_path = target_repo / ".agents" / "marketplaces.md"
        writes.append((marketplaces_path, updated_marketplaces_markdown(marketplaces_path, profile, "pending")))

    if args.dry_run:
        for path, content in writes:
            print(f"would write {path}")
            print(indent_preview(content))
        return 0

    for path, content in writes:
        write_text(path, content)
        print(f"wrote {path}")
    return 0


def cmd_install(args: argparse.Namespace) -> int:
    target_repo = target_repo_path(args.repo)
    profile = load_profile(args.profile, target_repo)
    validate_profile_references(profile)

    apply = bool(args.apply)
    status = "configured" if apply else "pending"
    marketplaces_path = target_repo / ".agents" / "marketplaces.md"
    markdown = updated_marketplaces_markdown(marketplaces_path, profile, status)

    if apply:
        write_text(marketplaces_path, markdown)
        print(f"updated {marketplaces_path}")
    else:
        print(f"would update {marketplaces_path}")

    failures = 0
    if apply and not args.skip_validation:
        failures += run_validation(manifests_only=False)
    return 1 if failures else 0


def cmd_primitive_new(args: argparse.Namespace) -> int:
    name = slug(args.name)
    if name != args.name:
        raise IntelligenceError(f"name must already be kebab-case; use {name!r}")
    description = args.description or default_description(args.kind, name)
    planned = scaffold_plan(args.kind, name, description)
    marketplace_update = args.marketplace
    plugin_updates = list(args.plugin)

    if args.kind == "plugin" and plugin_updates:
        raise IntelligenceError("--plugin does not apply when scaffolding a plugin")

    if args.dry_run:
        for path, content, _executable in planned:
            action = "would overwrite" if path.exists() else "would create"
            print(f"{action} {path}")
            print(indent_preview(content))
        for plugin_name in plugin_updates:
            print(f"would add {args.kind} {name} to plugins/{plugin_name}/plugin.json")
        if marketplace_update:
            print(f"would add {args.kind} {name} to adaptable.marketplace.json")
        return 0

    for path, content, executable in planned:
        if path.exists() and not args.force:
            raise IntelligenceError(f"refusing to overwrite existing file without --force: {path}")
        write_text(path, content)
        if executable:
            path.chmod(path.stat().st_mode | 0o755)
        print(f"wrote {path}")

    for plugin_name in plugin_updates:
        add_primitive_to_plugin(plugin_name, args.kind, name)
        print(f"updated plugins/{plugin_name}/plugin.json")
    if marketplace_update:
        add_to_marketplace(args.kind, name, description)
        print("updated adaptable.marketplace.json")

    if args.validate:
        return run_validation(manifests_only=False)
    return 0


def cmd_validate(args: argparse.Namespace) -> int:
    return run_validation(manifests_only=args.manifests_only)


def load_profile(value: str, target_repo: Path) -> dict[str, Any]:
    candidates: list[Path] = []
    raw = Path(value).expanduser()
    if raw.is_absolute():
        candidates.append(raw)
    else:
        candidates.append(target_repo / raw)
        candidates.append(PROFILE_DIR / value)
        if raw.suffix != ".json":
            candidates.append(PROFILE_DIR / f"{value}.json")

    for candidate in candidates:
        if candidate.is_file():
            data = read_json(candidate)
            if data.get("type") != "WORKFLOW_PROFILE":
                raise IntelligenceError(f"profile is not a WORKFLOW_PROFILE: {candidate}")
            return data
    searched = ", ".join(str(path) for path in candidates)
    raise IntelligenceError(f"profile not found: {value}; searched {searched}")


def merge_profile_overrides(
    profile: dict[str, Any],
    plugins: list[str],
    hooks: list[str],
) -> dict[str, Any]:
    merged = json.loads(json.dumps(profile))
    merged["plugins"] = dedupe([*merged.get("plugins", []), *[slug(item) for item in plugins]])
    hook_values = list(merged.get("hooks", []))
    hook_keys = {(item["name"], item["adapter"]) for item in hook_values}
    for raw in hooks:
        hook = parse_hook_selection(raw)
        key = (hook["name"], hook["adapter"])
        if key not in hook_keys:
            hook_values.append(hook)
            hook_keys.add(key)
    merged["hooks"] = hook_values
    return merged


def parse_hook_selection(raw: str) -> dict[str, str]:
    if "@" in raw:
        name, adapter = raw.split("@", 1)
    else:
        name, adapter = raw, "codex"
    return {
        "type": "WORKFLOW_HOOK",
        "name": slug(name),
        "adapter": adapter.strip().lower(),
    }


def validate_profile_references(profile: dict[str, Any]) -> None:
    marketplace = read_json(MARKETPLACE_PATH)
    plugin_names = {entry["name"] for entry in marketplace.get("plugins", [])}
    hook_names = {entry["name"] for entry in marketplace.get("hooks", [])}

    missing_plugins = sorted(set(profile.get("plugins", [])) - plugin_names)
    if missing_plugins:
        raise IntelligenceError(f"profile references unknown marketplace plugins: {', '.join(missing_plugins)}")

    missing_hooks = sorted({hook["name"] for hook in profile.get("hooks", [])} - hook_names)
    if missing_hooks:
        raise IntelligenceError(f"profile references unknown hooks: {', '.join(missing_hooks)}")


def updated_marketplaces_markdown(path: Path, profile: dict[str, Any], status: str) -> str:
    section = render_marketplaces_section(profile, status)
    if not path.exists():
        return section

    current = path.read_text(encoding="utf-8")
    start = "<!-- intelligence:marketplaces:start -->"
    end = "<!-- intelligence:marketplaces:end -->"
    if start in current and end in current:
        before, remainder = current.split(start, 1)
        _old, after = remainder.split(end, 1)
        return before.rstrip() + "\n\n" + section + "\n" + after.lstrip()
    return current.rstrip() + "\n\n" + section


def render_marketplaces_section(profile: dict[str, Any], status: str) -> str:
    marketplace = read_json(MARKETPLACE_PATH)
    plugin_descriptions = {entry["name"]: entry.get("description", "") for entry in marketplace.get("plugins", [])}
    lines = [
        "<!-- intelligence:marketplaces:start -->",
        "# Agent Marketplaces",
        "",
        "This repository consumes agent tooling from configured marketplaces. Installed plugin payloads are not source-of-truth files.",
        "",
        f"Profile: `{profile['name']}`",
        "",
        "## Marketplace Sources",
        "",
        "| Provider | Marketplace | Source | Entrypoint | Scope |",
        "|---|---|---|---|---|",
    ]
    for source in profile.get("marketplaces", []):
        lines.append(
            f"| {source['provider']} | {source['name']} | {source['source']} | {source['entrypoint']} | {source['scope']} |"
        )
    lines.extend(
        [
            "",
            "## Expected Plugins",
            "",
            "| Plugin | Marketplace | Purpose | Status |",
            "|---|---|---|---|",
        ]
    )
    marketplace_name = profile.get("marketplaces", [{}])[0].get("name", "intelligence")
    for plugin in profile.get("plugins", []):
        purpose = plugin_descriptions.get(plugin, "Profile-selected plugin.")
        lines.append(f"| {plugin} | {marketplace_name} | {purpose} | {status} |")
    lines.extend(
        [
            "",
            "## Hooks",
            "",
            "| Hook | Adapter | Status |",
            "|---|---|---|",
        ]
    )
    for hook in profile.get("hooks", []):
        lines.append(f"| {hook['name']} | {hook['adapter']} | {status} |")
    lines.extend(
        [
            "",
            "## Refresh",
            "",
            "- Run `bin/intelligence profile init --repo . --profile "
            + profile["name"]
            + "` to refresh this checked-in reference.",
            "- Run `bin/intelligence install --repo . --profile .agents/intelligence-profile.json --runtime codex` for a dry run.",
            "- Add `--apply` only after reviewing the dry run.",
            "",
            "## Validation",
            "",
        ]
    )
    for command in profile.get("validation", {}).get("commands", []):
        lines.append(f"- `{command}`")
    lines.append("<!-- intelligence:marketplaces:end -->")
    lines.append("")
    return "\n".join(lines)


def scaffold_plan(kind: str, name: str, description: str) -> list[tuple[Path, str, bool]]:
    title = title_case(name)
    replacements = {
        "{{name}}": name,
        "{{description}}": description,
        "{{title}}": title,
    }
    if kind == "skill":
        return [(REPO_ROOT / "skills" / name / "SKILL.md", render_template("skill/SKILL.md.tmpl", replacements), False)]
    if kind == "agent":
        return [(REPO_ROOT / "agents" / f"{name}.agent.md", render_template("agent/agent.md.tmpl", replacements), False)]
    if kind == "hook":
        return [
            (REPO_ROOT / "hooks" / f"{name}.hook.json", render_template("hook/hook.json.tmpl", replacements), False),
            (REPO_ROOT / "hooks" / f"{name}.py", render_template("hook/hook.py.tmpl", replacements), True),
            (REPO_ROOT / "hooks" / "codex" / f"{name}.hooks.json", render_template("hook/codex.hooks.json.tmpl", replacements), False),
        ]
    if kind == "plugin":
        return [(REPO_ROOT / "plugins" / name / "plugin.json", render_template("plugin/plugin.json.tmpl", replacements), False)]
    raise IntelligenceError(f"unsupported primitive kind: {kind}")


def add_primitive_to_plugin(plugin_name: str, kind: str, name: str) -> None:
    collection = PRIMITIVE_COLLECTIONS.get(kind)
    if not collection:
        raise IntelligenceError(f"cannot add {kind} to plugin references")
    plugin_path = REPO_ROOT / "plugins" / plugin_name / "plugin.json"
    if not plugin_path.is_file():
        raise IntelligenceError(f"plugin manifest not found: {plugin_path}")
    manifest = read_json(plugin_path)
    ref = primitive_reference(kind, name)
    current = manifest.setdefault(collection, [])
    if not any(item.get("name") == name for item in current):
        current.append(ref)
    write_json(plugin_path, manifest)


def add_to_marketplace(kind: str, name: str, description: str) -> None:
    marketplace = read_json(MARKETPLACE_PATH)
    if kind == "plugin":
        entries = marketplace.setdefault("plugins", [])
        if not any(item.get("name") == name for item in entries):
            entries.append(
                {
                    "type": "PLUGIN_ENTRY",
                    "name": name,
                    "plugin": {
                        "type": "PLUGIN_REFERENCE",
                        "name": name,
                        "source": {
                            "type": "LOCAL_SOURCE",
                            "path": f"./plugins/{name}",
                        },
                        "version": "0.1.0",
                    },
                    "description": description,
                    "tags": ["workflow"],
                }
            )
    else:
        collection = PRIMITIVE_COLLECTIONS[kind]
        entries = marketplace.setdefault(collection, [])
        if not any(item.get("name") == name for item in entries):
            entries.append(primitive_reference(kind, name))
    write_json(MARKETPLACE_PATH, marketplace)


def primitive_reference(kind: str, name: str) -> dict[str, Any]:
    type_name = kind.upper()
    paths = {
        "skill": f"skills/{name}",
        "agent": f"agents/{name}.agent.md",
        "hook": f"hooks/{name}.hook.json",
    }
    return {
        "type": type_name,
        "source": {
            "type": "LOCAL_SOURCE",
            "path": "./",
        },
        "path": paths[kind],
        "name": name,
    }


def run_validation(manifests_only: bool) -> int:
    commands = [["node", "scripts/validate-manifests.mjs"]]
    failures = 0
    for command in commands:
        print("$ " + " ".join(command))
        result = subprocess.run(command, cwd=REPO_ROOT, check=False)
        if result.returncode != 0:
            failures += 1
    return 1 if failures else 0


def render_template(relative: str, replacements: dict[str, str]) -> str:
    path = TEMPLATE_DIR / relative
    text = path.read_text(encoding="utf-8")
    for marker, value in replacements.items():
        text = text.replace(marker, value)
    return text


def read_json(path: Path) -> dict[str, Any]:
    try:
        with path.open(encoding="utf-8") as handle:
            data = json.load(handle)
    except FileNotFoundError as error:
        raise IntelligenceError(f"missing JSON file: {path}") from error
    if not isinstance(data, dict):
        raise IntelligenceError(f"JSON file must contain an object: {path}")
    return data


def write_json(path: Path, data: dict[str, Any]) -> None:
    write_text(path, json.dumps(data, indent=2) + "\n")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def resolve_target_path(repo_root: Path, value: str) -> Path:
    path = Path(value).expanduser()
    if path.is_absolute():
        return path
    return (repo_root / path).resolve()


def target_repo_path(value: str) -> Path:
    path = Path(value).expanduser().resolve()
    if not path.is_dir():
        raise IntelligenceError(f"target repo does not exist or is not a directory: {path}")
    return path


def resolve_repo_path(value: str) -> Path:
    path = Path(os.path.expanduser(value))
    if path.is_absolute():
        return path
    return (REPO_ROOT / path).resolve()


def expand_target_path(value: str) -> str:
    if value.startswith("codex://"):
        return value
    return str(Path(os.path.expanduser(value)))


def indent_preview(content: str) -> str:
    lines = content.rstrip().splitlines()
    if len(lines) > 24:
        lines = [*lines[:24], "..."]
    return "\n".join(f"  {line}" for line in lines)


def dedupe(values: list[str]) -> list[str]:
    result = []
    seen = set()
    for value in values:
        if value not in seen:
            result.append(value)
            seen.add(value)
    return result


def slug(value: str) -> str:
    candidate = re.sub(r"[^a-z0-9]+", "-", value.strip().lower()).strip("-")
    if not candidate:
        raise IntelligenceError("name cannot be empty")
    return candidate


def title_case(value: str) -> str:
    return " ".join(part.capitalize() for part in value.split("-") if part)


def default_description(kind: str, name: str) -> str:
    return f"{title_case(name)} {kind} primitive."


if __name__ == "__main__":
    raise SystemExit(main())
