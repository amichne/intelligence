#!/usr/bin/env python3
"""
Resolve the most relevant Jira issue key for the current repository.

Strategy order:
1. Current git branch
2. Explicit issue key in prompt text
3. Jira project search using prompt text
4. Most recently updated issue assigned to currentUser()
5. Optional ready-state JQL from JIRA_READY_JQL
6. Recent issue history in the current project
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

ISSUE_KEY_RE = re.compile(r"\b([A-Za-z][A-Za-z0-9]+-\d+)\b")


@dataclass
class IssueCandidate:
    key: str
    strategy: str
    summary: str | None = None
    status: str | None = None
    updated: str | None = None
    source_command: list[str] | None = None


class JiraCommandError(RuntimeError):
    def __init__(self, command: list[str], returncode: int, stderr: str):
        super().__init__(stderr.strip() or f"command failed with exit code {returncode}")
        self.command = command
        self.returncode = returncode
        self.stderr = stderr.strip()


def extract_issue_key(text: str | None) -> str | None:
    if not text:
        return None
    match = ISSUE_KEY_RE.search(text)
    return match.group(1).upper() if match else None


def git_branch(cwd: Path) -> str | None:
    try:
        completed = subprocess.run(
            ["git", "-C", str(cwd), "branch", "--show-current"],
            check=True,
            capture_output=True,
            text=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None

    branch = completed.stdout.strip()
    return branch or None


def run_command(command: list[str]) -> subprocess.CompletedProcess[str]:
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError as exc:
        raise RuntimeError(f"missing executable: {command[0]}") from exc

    if completed.returncode != 0:
        raise JiraCommandError(command, completed.returncode, completed.stderr)

    return completed


def build_jira_base(jira_bin: str, project: str | None) -> list[str]:
    command = [jira_bin]
    if project:
        command.extend(["--project", project])
    command.extend(["issue", "list"])
    return command


def parse_issue_rows(
    output: str,
    strategy: str,
    source_command: list[str],
) -> list[IssueCandidate]:
    candidates: list[IssueCandidate] = []
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        parts = raw_line.split("\t")
        key = extract_issue_key(parts[0] if parts else None)
        if not key:
            continue
        summary = parts[1].strip() if len(parts) > 1 else None
        status = parts[2].strip() if len(parts) > 2 else None
        updated = parts[3].strip() if len(parts) > 3 else None
        candidates.append(
            IssueCandidate(
                key=key,
                strategy=strategy,
                summary=summary or None,
                status=status or None,
                updated=updated or None,
                source_command=source_command,
            )
        )
    return candidates


def jira_list_candidates(
    jira_bin: str,
    project: str | None,
    strategy: str,
    positional_query: str | None = None,
    extra_args: Iterable[str] = (),
) -> list[IssueCandidate]:
    command = build_jira_base(jira_bin, project)
    command.extend(
        [
            "--plain",
            "--no-headers",
            "--columns",
            "key,summary,status,updated",
            "--delimiter",
            "\t",
            "--order-by",
            "updated",
            "--paginate",
            "0:20",
        ]
    )
    command.extend(extra_args)
    if positional_query:
        command.append(positional_query)
    completed = run_command(command)
    return parse_issue_rows(completed.stdout, strategy=strategy, source_command=command)


def resolve_ticket(
    cwd: Path,
    prompt: str | None,
    project: str | None,
    branch_override: str | None,
    jira_bin: str,
    ready_jql: str | None,
) -> tuple[IssueCandidate | None, list[dict[str, object]]]:
    tried: list[dict[str, object]] = []

    branch = branch_override if branch_override is not None else git_branch(cwd)
    branch_key = extract_issue_key(branch)
    tried.append({"strategy": "branch", "branch": branch})
    if branch_key:
        return IssueCandidate(key=branch_key, strategy="branch"), tried

    prompt_key = extract_issue_key(prompt)
    tried.append({"strategy": "prompt-key", "matched": prompt_key is not None})
    if prompt_key:
        return IssueCandidate(key=prompt_key, strategy="prompt-key"), tried

    stripped_prompt = (prompt or "").strip()
    if stripped_prompt:
        tried.append({"strategy": "prompt-search", "query": stripped_prompt})
        prompt_candidates = jira_list_candidates(
            jira_bin=jira_bin,
            project=project,
            strategy="prompt-search",
            positional_query=stripped_prompt,
        )
        if prompt_candidates:
            return prompt_candidates[0], tried

    assignee_query = "assignee = currentUser() ORDER BY updated DESC"
    tried.append({"strategy": "assigned-current-user", "jql": assignee_query})
    assigned_candidates = jira_list_candidates(
        jira_bin=jira_bin,
        project=project,
        strategy="assigned-current-user",
        extra_args=["--jql", assignee_query],
    )
    if assigned_candidates:
        return assigned_candidates[0], tried

    ready_jql_value = ready_jql or os.environ.get("JIRA_READY_JQL")
    if ready_jql_value:
        tried.append({"strategy": "ready-jql", "jql": ready_jql_value})
        ready_candidates = jira_list_candidates(
            jira_bin=jira_bin,
            project=project,
            strategy="ready-jql",
            extra_args=["--jql", ready_jql_value],
        )
        if ready_candidates:
            return ready_candidates[0], tried

    tried.append({"strategy": "history"})
    history_candidates = jira_list_candidates(
        jira_bin=jira_bin,
        project=project,
        strategy="history",
        extra_args=["--history"],
    )
    if history_candidates:
        return history_candidates[0], tried

    return None, tried


def print_json(payload: dict[str, object]) -> None:
    json.dump(payload, sys.stdout, indent=2)
    sys.stdout.write("\n")


def self_test() -> int:
    assert extract_issue_key("feature/abc-123-add-parser") == "ABC-123"
    assert extract_issue_key("Nothing here") is None

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        mock = tmp / "mock-jira"
        mock.write_text(
            """#!/usr/bin/env python3
import sys

args = sys.argv[1:]
joined = " ".join(args)
if "assignee = currentUser()" in joined:
    print("APP-8\\tAssigned issue\\tIn Progress\\t2026-03-24")
elif "--history" in args:
    print("APP-10\\tHistory issue\\tDone\\t2026-03-23")
elif any("payment retry" in arg for arg in args):
    print("APP-7\\tPayment retry handling\\tReady\\t2026-03-25")
elif any("status in" in arg for arg in args):
    print("APP-9\\tReady issue\\tReady\\t2026-03-24")
""",
            encoding="utf-8",
        )
        mock.chmod(0o755)

        candidate, _ = resolve_ticket(
            cwd=tmp,
            prompt="Investigate payment retry handling",
            project=None,
            branch_override="main",
            jira_bin=str(mock),
            ready_jql='status in ("Ready") ORDER BY updated DESC',
        )
        assert candidate is not None
        assert candidate.key == "APP-7"
        assert candidate.strategy == "prompt-search"

        candidate, _ = resolve_ticket(
            cwd=tmp,
            prompt=None,
            project=None,
            branch_override="feature/app-42-reconcile-events",
            jira_bin=str(mock),
            ready_jql=None,
        )
        assert candidate is not None
        assert candidate.key == "APP-42"
        assert candidate.strategy == "branch"

        candidate, _ = resolve_ticket(
            cwd=tmp,
            prompt=None,
            project=None,
            branch_override="main",
            jira_bin=str(mock),
            ready_jql='status in ("Ready") ORDER BY updated DESC',
        )
        assert candidate is not None
        assert candidate.key == "APP-8"
        assert candidate.strategy == "assigned-current-user"

    print("self-test: ok")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cwd", default=".", help="Repository path used to inspect the current branch")
    parser.add_argument("--prompt", help="Original user request text")
    parser.add_argument("--project", help="Override Jira project key/context")
    parser.add_argument("--branch", help="Override current branch for testing or non-git contexts")
    parser.add_argument("--jira-bin", default="jira", help="Path to jira executable")
    parser.add_argument("--ready-jql", help="Explicit ready-state JQL override")
    parser.add_argument("--self-test", action="store_true", help="Run built-in tests and exit")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    try:
        candidate, tried = resolve_ticket(
            cwd=Path(args.cwd).resolve(),
            prompt=args.prompt,
            project=args.project,
            branch_override=args.branch,
            jira_bin=args.jira_bin,
            ready_jql=args.ready_jql,
        )
    except JiraCommandError as exc:
        print_json(
            {
                "ok": False,
                "error": "jira command failed",
                "details": exc.stderr or f"exit code {exc.returncode}",
                "command": exc.command,
            }
        )
        return 1
    except RuntimeError as exc:
        print_json({"ok": False, "error": str(exc)})
        return 1

    if candidate is None:
        print_json(
            {
                "ok": False,
                "error": "no jira issue could be resolved",
                "tried": tried,
            }
        )
        return 2

    payload = {"ok": True, **asdict(candidate), "next_command": ["jira", "issue", "view", candidate.key, "--comments", "5", "--plain"]}
    print_json(payload)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
