#!/usr/bin/env bash
set -euo pipefail

COMMAND="${1:-}"
if [[ -z "${COMMAND}" ]]; then
    echo "usage: agents-md-turn-refresh.sh <start|record|stop|status>" >&2
    exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${AGENTS_MD_TURN_REFRESH_REPO:-}" ]]; then
    REPO_ROOT="${AGENTS_MD_TURN_REFRESH_REPO}"
else
    REPO_ROOT="$(git -C "${SCRIPT_DIR}" rev-parse --show-toplevel)"
fi

HOOK_INPUT="$(cat || true)"
export HOOK_INPUT

python3 - "${COMMAND}" "${REPO_ROOT}" <<'PY'
import datetime as dt
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path, PurePosixPath

COMMAND = sys.argv[1]
REPO_ROOT = Path(sys.argv[2]).resolve()
STATE_DIR = REPO_ROOT / ".agent-turn"
STATE_FILE = STATE_DIR / "agents-md-turn-refresh.json"
MARKER_FILE = STATE_DIR / "agents-md-turn-refresh.started"
STATE_VERSION = 1


def run_git(args: list[str]) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        ["git", "-C", str(REPO_ROOT), *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def rel(path: Path) -> str | None:
    try:
        return path.resolve().relative_to(REPO_ROOT).as_posix()
    except ValueError:
        return None


def ignored(relative: str) -> bool:
    path = PurePosixPath(relative)
    parts = path.parts
    return (
        not relative
        or relative == "."
        or ".git" in parts
        or ".agent-turn" in parts
    )


def mtime_ns(relative: str) -> int | None:
    path = REPO_ROOT / relative
    try:
        return path.stat().st_mtime_ns
    except FileNotFoundError:
        return None


def git_status_paths() -> set[str]:
    result = run_git(["status", "--porcelain=v1", "-z", "--untracked-files=all"])
    if result.returncode != 0:
        return set()

    fields = result.stdout.decode("utf-8", errors="replace").split("\0")
    paths: set[str] = set()
    index = 0
    while index < len(fields):
        entry = fields[index]
        index += 1
        if not entry:
            continue
        if len(entry) < 4:
            continue
        status = entry[:2]
        path = entry[3:]
        if path and not ignored(path):
            paths.add(path)
        if ("R" in status or "C" in status) and index < len(fields):
            original = fields[index]
            index += 1
            if original and not ignored(original):
                paths.add(original)
    return paths


def agents_mtimes() -> dict[str, int]:
    result: dict[str, int] = {}
    for path in REPO_ROOT.rglob("AGENTS.md"):
        relative = rel(path)
        if relative and not ignored(relative):
            value = mtime_ns(relative)
            if value is not None:
                result[relative] = value
    return result


def load_state() -> dict:
    if not STATE_FILE.exists():
        return {}
    try:
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def write_state(state: dict) -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    tmp = STATE_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(STATE_FILE)


def start() -> int:
    now_ns = time.time_ns()
    changed = sorted(git_status_paths())
    state = {
        "version": STATE_VERSION,
        "repoRoot": str(REPO_ROOT),
        "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "startedAtEpochNs": now_ns,
        "baselineChangedFiles": changed,
        "baselinePathMtimes": {path: mtime_ns(path) for path in changed},
        "baselineAgentsMtimes": agents_mtimes(),
        "recordedPaths": [],
    }
    write_state(state)
    MARKER_FILE.write_text(str(now_ns) + "\n", encoding="utf-8")
    return 0


def hook_payload() -> dict:
    raw = os.environ.get("HOOK_INPUT", "").strip()
    if not raw:
        return {}
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return payload if isinstance(payload, dict) else {}


def decoded_mapping(value) -> dict:
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return {}
        return parsed if isinstance(parsed, dict) else {}
    return {}


def successful_result(payload: dict) -> bool:
    result = (
        payload.get("toolResult")
        or payload.get("tool_result")
        or payload.get("result")
        or {}
    )
    if isinstance(result, str):
        return result.lower() in {"success", "ok", "completed"}
    if not isinstance(result, dict):
        return True
    result_type = result.get("resultType") or result.get("status") or result.get("type")
    if result_type is None:
        return True
    return str(result_type).lower() in {"success", "ok", "completed"}


def patch_paths(text: str) -> list[str]:
    paths: list[str] = []
    prefixes = (
        "*** Add File: ",
        "*** Update File: ",
        "*** Delete File: ",
        "*** Move to: ",
    )
    for line in text.splitlines():
        for prefix in prefixes:
            if line.startswith(prefix):
                paths.append(line[len(prefix) :].strip())
                break
    return paths


def path_candidates(value) -> list[str]:
    if isinstance(value, str):
        if "\n" in value:
            return patch_paths(value)
        return [value]
    if isinstance(value, list):
        candidates: list[str] = []
        for item in value:
            candidates.extend(path_candidates(item))
        return candidates
    return []


def normalize(candidate: str, cwd: Path) -> str | None:
    if not candidate or "\0" in candidate:
        return None
    if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*://", candidate):
        return None
    path = Path(candidate)
    if not path.is_absolute():
        path = cwd / path
    relative = rel(path)
    if relative and not ignored(relative):
        return relative
    return None


def extract_paths(payload: dict) -> set[str]:
    cwd_value = payload.get("cwd") or payload.get("workingDirectory") or str(REPO_ROOT)
    cwd = Path(cwd_value)
    if not cwd.is_absolute():
        cwd = REPO_ROOT / cwd
    cwd = cwd.resolve()

    args = (
        payload.get("toolArgs")
        or payload.get("tool_args")
        or payload.get("arguments")
        or payload.get("input")
        or {}
    )
    args_map = decoded_mapping(args)
    candidates: list[str] = []
    path_keys = {
        "path",
        "paths",
        "file",
        "files",
        "filePath",
        "filePaths",
        "file_path",
        "file_paths",
        "filename",
        "target",
        "targetFile",
        "target_file",
    }
    for key, value in args_map.items():
        if key in path_keys:
            candidates.extend(path_candidates(value))
        elif isinstance(value, str) and value.startswith("*** Begin Patch"):
            candidates.extend(patch_paths(value))

    if isinstance(args, str) and args.startswith("*** Begin Patch"):
        candidates.extend(patch_paths(args))

    paths = {normalized for item in candidates if (normalized := normalize(item, cwd))}
    return paths


def record() -> int:
    payload = hook_payload()
    if payload and not successful_result(payload):
        return 0
    paths = extract_paths(payload)
    if not paths:
        return 0

    state = load_state()
    if not state:
        start()
        state = load_state()
    recorded = set(state.get("recordedPaths", []))
    recorded.update(paths)
    state["recordedPaths"] = sorted(recorded)
    write_state(state)
    return 0


def current_turn_paths(state: dict) -> set[str]:
    baseline = set(state.get("baselineChangedFiles", []))
    baseline_mtimes = state.get("baselinePathMtimes", {})
    current = git_status_paths()
    paths: set[str] = set(state.get("recordedPaths", []))
    for path in current:
        if path not in baseline:
            paths.add(path)
            continue
        if baseline_mtimes.get(path) != mtime_ns(path):
            paths.add(path)
    return {path for path in paths if not ignored(path)}


def nearest_agent(relative: str) -> tuple[str, bool]:
    path = PurePosixPath(relative)
    if path.name == "AGENTS.md":
        return path.as_posix(), (REPO_ROOT / path.as_posix()).exists()
    parent = path.parent
    search_dirs = [parent, *parent.parents]
    for directory in search_dirs:
        candidate = (
            "AGENTS.md"
            if str(directory) == "."
            else f"{directory.as_posix()}/AGENTS.md"
        )
        if (REPO_ROOT / candidate).is_file():
            return candidate, True
    return "AGENTS.md", (REPO_ROOT / "AGENTS.md").exists()


def updated_agents(state: dict, changed_paths: set[str]) -> set[str]:
    updated = {
        path
        for path in changed_paths
        if PurePosixPath(path).name == "AGENTS.md"
    }
    baseline = state.get("baselineAgentsMtimes", {})
    for path, current_mtime in agents_mtimes().items():
        if baseline.get(path) != current_mtime:
            updated.add(path)
    return updated


def stop() -> int:
    state = load_state()
    if not state:
        return 0

    changed = sorted(current_turn_paths(state))
    if not changed:
        return 0

    affected: dict[str, dict[str, object]] = {}
    for path in changed:
        agent_path, exists = nearest_agent(path)
        item = affected.setdefault(agent_path, {"exists": exists, "paths": []})
        item["exists"] = bool(item["exists"]) or exists
        item["paths"].append(path)

    touched_agents = updated_agents(state, set(changed))
    pending = {
        path: item
        for path, item in affected.items()
        if path not in touched_agents
    }
    if not pending:
        return 0

    print("AGENTS.md refresh needed for the current turn.", file=sys.stderr)
    print("", file=sys.stderr)
    for agent_path, item in sorted(pending.items()):
        action = "update" if item["exists"] else "create"
        print(f"- {action} `{agent_path}`", file=sys.stderr)
        for changed_path in sorted(item["paths"])[:12]:
            print(f"  - covers `{changed_path}`", file=sys.stderr)
        if len(item["paths"]) > 12:
            remaining = len(item["paths"]) - 12
            print(f"  - plus {remaining} more path(s)", file=sys.stderr)
    print("", file=sys.stderr)
    print(
        "Review the nearest parent instructions, add only local deltas, and rerun "
        "`bash hooks/agents-md-turn-refresh.sh stop`.",
        file=sys.stderr,
    )

    mode = os.environ.get("AGENTS_MD_TURN_REFRESH_MODE", "enforce").lower()
    return 0 if mode in {"warn", "advisory", "off"} else 1


def status() -> int:
    state = load_state()
    print(json.dumps({
        "stateFile": str(STATE_FILE),
        "stateExists": bool(state),
        "currentTurnPaths": sorted(current_turn_paths(state)) if state else [],
    }, indent=2, sort_keys=True))
    return 0


commands = {
    "start": start,
    "record": record,
    "stop": stop,
    "status": status,
}

handler = commands.get(COMMAND)
if handler is None:
    print(f"unknown command: {COMMAND}", file=sys.stderr)
    raise SystemExit(2)
raise SystemExit(handler())
PY
