#!/usr/bin/env bash
# setup-local-nav.sh — Install AGENTS.local.md gitexclude patterns, post-commit hook,
#                      and optionally the Copilot adapter files.
#
# Usage:
#   bash setup-local-nav.sh [options]
#
# Options:
#   --repo-root <path>   Repo root to configure (default: git rev-parse --show-toplevel)
#   --hook-mode auto     Hook regenerates AGENTS.local.md immediately on commit
#   --hook-mode collect  Hook appends to OUTDATED.local.md; agent updates on demand (default)
#   --copilot            Install Copilot chat mode + prompt files into .github/
#   --copilot-only       Run only the Copilot install step (skip gitexclude + hook)

set -euo pipefail

HOOK_MODE="collect"
REPO_ROOT=""
INSTALL_COPILOT=false
COPILOT_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo-root)    REPO_ROOT="$2";    shift 2 ;;
    --hook-mode)    HOOK_MODE="$2";    shift 2 ;;
    --copilot)      INSTALL_COPILOT=true; shift ;;
    --copilot-only) INSTALL_COPILOT=true; COPILOT_ONLY=true; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$REPO_ROOT" ]]; then
  REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" \
    || { echo "ERROR: not inside a git repo and --repo-root not provided" >&2; exit 1; }
fi

AGENT_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_ROOT="$(dirname "$AGENT_SCRIPT_DIR")"   # agents/codebase-navigator/
COPILOT_SRC="$AGENT_ROOT/copilot"
GEN_SCRIPT="$AGENT_SCRIPT_DIR/gen-agents-local.py"
GIT_DIR="$REPO_ROOT/.git"
EXCLUDE_FILE="$GIT_DIR/info/exclude"
HOOKS_DIR="$GIT_DIR/hooks"
POST_COMMIT="$HOOKS_DIR/post-commit"

# ── 1. Gitexclude ─────────────────────────────────────────────────────────────

if [[ "$COPILOT_ONLY" != true ]]; then
  mkdir -p "$GIT_DIR/info"
  touch "$EXCLUDE_FILE"

  add_exclude() {
    local pattern="$1"
    if ! grep -qxF "$pattern" "$EXCLUDE_FILE"; then
      echo "$pattern" >> "$EXCLUDE_FILE"
      echo "  added exclude: $pattern"
    else
      echo "  already excluded: $pattern"
    fi
  }

  echo "── gitexclude ($EXCLUDE_FILE)"
  add_exclude "AGENTS.local.md"
  add_exclude "**/AGENTS.local.md"
  add_exclude "OUTDATED.local.md"

  # ── 2. Post-commit hook ────────────────────────────────────────────────────

  echo ""
  echo "── post-commit hook ($POST_COMMIT) [mode=$HOOK_MODE]"

  if [[ "$HOOK_MODE" == "auto" ]]; then
    HOOK_FRAGMENT=$(cat <<FRAGMENT
# codebase-navigator: regenerate AGENTS.local.md for changed directories
_nav_changed_dirs() {
  git diff-tree --no-commit-id -r --name-only HEAD \
    | xargs -I{} dirname {} \
    | sort -u \
    | grep -v '^\.' \
    | grep -v '^\$'
}
if command -v python3 &>/dev/null && [ -f "$GEN_SCRIPT" ]; then
  while IFS= read -r dir; do
    [ -d "\$dir" ] && python3 "$GEN_SCRIPT" "\$dir" --force 2>/dev/null || true
  done < <(_nav_changed_dirs)
fi
FRAGMENT
)
  else
    HOOK_FRAGMENT=$(cat <<FRAGMENT
# codebase-navigator: mark changed directories as outdated
_nav_mark_outdated() {
  local outdated="$REPO_ROOT/OUTDATED.local.md"
  git diff-tree --no-commit-id -r --name-only HEAD \
    | xargs -I{} dirname {} \
    | sort -u \
    | grep -v '^\.' \
    | grep -v '^\$' \
    | while IFS= read -r dir; do
        [ -d "\$dir" ] && echo "\$dir" >> "\$outdated" || true
      done
}
_nav_mark_outdated
FRAGMENT
)
  fi

  MARKER="# codebase-navigator:"

  if [[ -f "$POST_COMMIT" ]]; then
    if grep -qF "$MARKER" "$POST_COMMIT"; then
      echo "  hook already installed"
    else
      { echo ""; echo "$HOOK_FRAGMENT"; } >> "$POST_COMMIT"
      echo "  appended to existing post-commit hook"
    fi
  else
    { echo "#!/usr/bin/env bash"; echo "set -euo pipefail"; echo ""; echo "$HOOK_FRAGMENT"; } > "$POST_COMMIT"
    chmod +x "$POST_COMMIT"
    echo "  created new post-commit hook"
  fi
fi

# ── 3. Copilot adapter ────────────────────────────────────────────────────────

if [[ "$INSTALL_COPILOT" == true ]]; then
  echo ""
  echo "── Copilot adapter ($REPO_ROOT/.github/)"

  GITHUB_DIR="$REPO_ROOT/.github"
  CHATMODES_DIR="$GITHUB_DIR/chatmodes"
  PROMPTS_DIR="$GITHUB_DIR/prompts"
  INSTRUCTIONS_FILE="$GITHUB_DIR/copilot-instructions.md"

  mkdir -p "$CHATMODES_DIR" "$PROMPTS_DIR"

  # Chat mode
  cp "$COPILOT_SRC/chatmode.md" "$CHATMODES_DIR/codebase-navigator.chatmode.md"
  echo "  wrote: .github/chatmodes/codebase-navigator.chatmode.md"

  # Prompt files
  for prompt_file in "$COPILOT_SRC/prompts/"*.prompt.md; do
    [[ -f "$prompt_file" ]] || continue
    name="$(basename "$prompt_file")"
    cp "$prompt_file" "$PROMPTS_DIR/$name"
    echo "  wrote: .github/prompts/$name"
  done

  # Inject instructions snippet into copilot-instructions.md
  SNIPPET_MARKER="<!-- codebase-navigator snippet"
  if [[ -f "$INSTRUCTIONS_FILE" ]] && grep -qF "$SNIPPET_MARKER" "$INSTRUCTIONS_FILE"; then
    echo "  skipped: codebase-navigator snippet already in copilot-instructions.md"
  else
    if [[ -f "$INSTRUCTIONS_FILE" ]]; then
      echo "" >> "$INSTRUCTIONS_FILE"
      cat "$COPILOT_SRC/instructions-snippet.md" >> "$INSTRUCTIONS_FILE"
      echo "  appended snippet to: .github/copilot-instructions.md"
    else
      cp "$COPILOT_SRC/instructions-snippet.md" "$INSTRUCTIONS_FILE"
      echo "  created: .github/copilot-instructions.md"
    fi
  fi

  echo ""
  echo "  Copilot usage:"
  echo "    Bootstrap:        open Copilot Chat → switch to 'codebase-navigator' mode → type 'bootstrap'"
  echo "    Process outdated: #process-outdated (from prompt files)"
  echo "    Update a dir:     #update-dir (from prompt files)"
  echo ""
  echo "  Commit .github/chatmodes/ and .github/prompts/ to share with your team."
  echo "  Do NOT commit AGENTS.local.md or OUTDATED.local.md (git-excluded)."
fi

# ── 4. Summary ────────────────────────────────────────────────────────────────

echo ""
echo "── setup complete"
echo "   repo:    $REPO_ROOT"
if [[ "$COPILOT_ONLY" != true ]]; then
  echo "   hook:    $HOOK_MODE mode"
  echo "   exclude: AGENTS.local.md, **/AGENTS.local.md, OUTDATED.local.md"
fi
if [[ "$INSTALL_COPILOT" == true ]]; then
  echo "   copilot: chat mode + prompts installed"
fi
echo ""
if [[ "$COPILOT_ONLY" != true ]]; then
  echo "Next: bootstrap navigation files"
  if command -v python3 &>/dev/null && [[ -f "$GEN_SCRIPT" ]]; then
    echo "  python3 $GEN_SCRIPT $REPO_ROOT --depth 2"
  else
    echo "  Open Copilot Chat → codebase-navigator mode → 'bootstrap'"
  fi
fi
