---
name: "jira-resolve-ticket"
description: "Resolve a Jira issue key before work by checking the branch, prompt, project search, assigned issues, ready-state JQL, and recent history, then fetch issue details with the Jira CLI."
---

# Jira Resolve Ticket

## Overview

Resolve a Jira issue key before doing work, then fetch the issue details with `jira issue view`.
Prefer the bundled resolver script over ad hoc shell pipelines so the selection order stays
consistent.

## Prerequisites

- Require `jira` on `PATH`.
- Require Jira authentication and project configuration before live queries. If a Jira command
  fails with an auth/config error, stop and tell the user exactly what is missing.
- Run from the target git repository, or pass `--cwd` explicitly to the resolver script.

## Resolution Order

Run the resolver first:

```bash
python3 skills/jira-resolve-ticket/scripts/resolve_ticket.py \
  --cwd "$PWD" \
  --prompt "<user request>"
```

The resolver uses this order:

1. Extract an issue key from the current branch name.
2. Extract an explicit issue key from the user prompt.
3. Search the current Jira project using the user prompt text.
4. Query the current Jira project for issues assigned to `currentUser()`, ordered by most recently updated.
5. Query an optional ready-state JQL from `JIRA_READY_JQL`, ordered by most recently updated.
6. Fall back to recent Jira history in the current project.

The script prints JSON. On success, read `key` and `strategy`. On failure, read `error`,
`details`, and `tried`.

## Fetch The Issue

After resolving the key, fetch the issue:

```bash
jira issue view ISSUE-123 --comments 5 --plain
```

Use `--raw` instead of `--plain` when structured Jira API data is more useful than formatted text.

## Configuration

- Pass `--project PROJECT_KEY` to force a Jira project instead of relying on the CLI default.
- Set `JIRA_READY_JQL` when the team has one or more explicit ready states. Example:

```bash
export JIRA_READY_JQL='status in ("Ready", "Selected for Development") ORDER BY updated DESC'
```

- Keep the ready-state query project-local. Do not broaden it to every Jira project unless the
  user explicitly asks for that.

## Examples

Use this skill for prompts like:

- "Find the Jira ticket for this branch and show me the acceptance criteria."
- "Which ticket does this request refer to: add retry handling for webhook delivery failures?"
- "I do not know the ticket; pull the most relevant recent Jira issue for me."

## Failure Handling

- If branch and prompt both contain issue keys and they disagree, prefer the branch key and call
  out the mismatch.
- If prompt search returns no results, continue to the fallback queries instead of stopping.
- If Jira auth/config is missing, stop after the first live Jira failure and tell the user to fix
  Jira CLI access before retrying.
- If the resolver returns `ok: false`, do not guess a ticket key manually.
