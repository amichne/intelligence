#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sys
from pathlib import Path
from typing import Any


STATE_VERSION = 1
READ_TOOL_NAMES = {
    "cat",
    "find",
    "functions.exec_command",
    "grep",
    "head",
    "less",
    "mcp_idea2_read_file",
    "mcp_idea_read_file",
    "multi_tool_use.parallel",
    "nl",
    "read_file",
    "rg",
    "sed",
    "tail",
    "tool_read_file",
}
READ_COMMAND_WORDS = {
    "awk",
    "bat",
    "cat",
    "find",
    "grep",
    "head",
    "less",
    "more",
    "nl",
    "rg",
    "sed",
    "tail",
}
WRITE_COMMAND_PATTERN = re.compile(
    r"\b(apply_patch|chmod|cp|mv|perl\s+-0?pi|rm|tee|truncate)\b|>|>>"
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Track required skill reads for hook-gated work.")
    parser.add_argument("command", choices=["init", "check", "status"])
    parser.add_argument("--repo", default=".", help="Repository root for relative skill paths.")
    parser.add_argument(
        "--config",
        default="hooks/required-skill-read.requirements.json",
        help="Schema-validated required-skill configuration.",
    )
    parser.add_argument("--state-dir", default=".agent-turn", help="Turn-local state directory.")
    args = parser.parse_args()

    repo_root = Path(args.repo).resolve()
    config_path = resolve_repo_path(repo_root, args.config)
    state_path = resolve_repo_path(repo_root, args.state_dir) / "required-skill-read.json"

    config = load_config(config_path)
    required = required_skills(repo_root, config)

    if args.command == "init":
        write_state(state_path, repo_root, [])
        return 0

    if args.command == "status":
        state = load_state(state_path)
        print(json.dumps(status_payload(required, loaded_paths(state)), indent=2, sort_keys=True))
        return 0

    payload = hook_payload()
    state = load_state(state_path)
    loaded = loaded_paths(state)
    current_reads = skill_reads_from_payload(repo_root, payload, required)
    if current_reads:
        loaded.update(current_reads)
        write_state(state_path, repo_root, loaded)

    missing = [skill for skill in required if str(skill["absolutePath"]) not in loaded]
    if not missing:
        return 0

    if payload_reads_missing_skill(repo_root, payload, missing):
        return 0

    emit_missing_message(config.get("mode", "ADVISORY"), missing, repo_root)
    return 0


def resolve_repo_path(repo_root: Path, value: str) -> Path:
    path = Path(os.path.expanduser(value))
    if path.is_absolute():
        return path
    return (repo_root / path).resolve()


def load_config(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"mode": "ADVISORY", "skills": []}
    with path.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    if not isinstance(payload, dict):
        raise SystemExit(f"required-skill-read: config must be an object: {path}")
    return payload


def required_skills(repo_root: Path, config: dict[str, Any]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for entry in config.get("skills", []):
        if not isinstance(entry, dict) or not entry.get("requireRead", False):
            continue
        skill_path = entry.get("skillPath")
        if not isinstance(skill_path, str) or not skill_path:
            continue
        absolute_path = resolve_repo_path(repo_root, skill_path)
        if not absolute_path.exists():
            continue
        item = dict(entry)
        item["absolutePath"] = absolute_path
        item["relativePath"] = relative_display(repo_root, absolute_path)
        result.append(item)
    return result


def load_state(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        with path.open(encoding="utf-8") as handle:
            payload = json.load(handle)
    except json.JSONDecodeError:
        return {}
    return payload if isinstance(payload, dict) else {}


def write_state(path: Path, repo_root: Path, loaded: set[str] | list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "version": STATE_VERSION,
        "repoRoot": str(repo_root),
        "updatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "loadedSkillPaths": sorted(set(loaded)),
    }
    temporary = path.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def loaded_paths(state: dict[str, Any]) -> set[str]:
    values = state.get("loadedSkillPaths")
    if not isinstance(values, list):
        return set()
    return {value for value in values if isinstance(value, str)}


def hook_payload() -> dict[str, Any]:
    raw = os.environ.get("HOOK_INPUT", "").strip()
    if not raw:
        raw = sys.stdin.read().strip()
    if not raw:
        return {}
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return payload if isinstance(payload, dict) else {}


def status_payload(required: list[dict[str, Any]], loaded: set[str]) -> dict[str, Any]:
    return {
        "required": [skill["relativePath"] for skill in required],
        "loaded": sorted(loaded),
        "missing": [skill["relativePath"] for skill in required if str(skill["absolutePath"]) not in loaded],
    }


def skill_reads_from_payload(
    repo_root: Path,
    payload: dict[str, Any],
    required: list[dict[str, Any]],
) -> set[str]:
    reads: set[str] = set()
    for skill in required:
        if payload_references_path(repo_root, payload, skill["absolutePath"]):
            reads.add(str(skill["absolutePath"]))
    return reads


def payload_reads_missing_skill(repo_root: Path, payload: dict[str, Any], missing: list[dict[str, Any]]) -> bool:
    return any(payload_references_path(repo_root, payload, skill["absolutePath"]) for skill in missing)


def payload_references_path(repo_root: Path, payload: Any, target: Path) -> bool:
    if isinstance(payload, list):
        return any(payload_references_path(repo_root, item, target) for item in payload)
    if not isinstance(payload, dict):
        return False

    tool_name = str(
        payload.get("toolName")
        or payload.get("tool_name")
        or payload.get("name")
        or payload.get("recipient_name")
        or ""
    )
    args = decoded_mapping(
        payload.get("toolArgs")
        or payload.get("tool_args")
        or payload.get("arguments")
        or payload.get("parameters")
        or payload.get("input")
        or {}
    )

    if tool_name in READ_TOOL_NAMES and arguments_reference_path(repo_root, args, target):
        return True

    command = command_text(args)
    if command and command_reads_path(repo_root, command, target):
        return True

    for nested_key in ("tool_uses", "tools", "calls"):
        nested = args.get(nested_key) if isinstance(args, dict) else None
        if payload_references_path(repo_root, nested, target):
            return True

    return arguments_reference_path(repo_root, payload, target)


def decoded_mapping(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return {}
        return parsed if isinstance(parsed, dict) else {}
    return {}


def command_text(args: dict[str, Any]) -> str | None:
    for key in ("cmd", "command", "bash"):
        value = args.get(key)
        if isinstance(value, str) and value.strip():
            return value
    return None


def arguments_reference_path(repo_root: Path, args: Any, target: Path) -> bool:
    if isinstance(args, dict):
        return any(arguments_reference_path(repo_root, value, target) for value in args.values())
    if isinstance(args, list):
        return any(arguments_reference_path(repo_root, value, target) for value in args)
    if not isinstance(args, str):
        return False
    return string_references_path(repo_root, args, target)


def command_reads_path(repo_root: Path, command: str, target: Path) -> bool:
    if WRITE_COMMAND_PATTERN.search(command):
        return False
    if not any(re.search(rf"\b{re.escape(word)}\b", command) for word in READ_COMMAND_WORDS):
        return False
    return string_references_path(repo_root, command, target)


def string_references_path(repo_root: Path, value: str, target: Path) -> bool:
    candidates = {str(target), relative_display(repo_root, target)}
    return any(candidate and candidate in value for candidate in candidates)


def relative_display(repo_root: Path, path: Path) -> str:
    try:
        return path.resolve().relative_to(repo_root).as_posix()
    except ValueError:
        return str(path)


def emit_missing_message(mode: str, missing: list[dict[str, Any]], repo_root: Path) -> None:
    missing_paths = [skill["relativePath"] for skill in missing]
    message = "Read required skill files before using other tools: " + ", ".join(missing_paths)
    if mode == "DENY":
        print(json.dumps({
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": message,
            },
            "systemMessage": message,
        }))
        return
    print(json.dumps({"systemMessage": message}))


if __name__ == "__main__":
    raise SystemExit(main())
