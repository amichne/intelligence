#!/usr/bin/env python3
"""
gen-agents-local.py — Generate AGENTS.local.md for a directory.

Usage:
  python3 gen-agents-local.py <directory> [--depth N] [--hash-only] [--dry-run]

Options:
  --depth N      Walk N levels deep (default: 1 = current dir only)
  --hash-only    Print the source-hash for the given directory and exit
  --dry-run      Print what would be written without writing anything
  --force        Regenerate even if the hash matches

The source-hash is a SHA1 of sorted "name:mtime:size" entries for all
non-excluded items in the directory. It changes whenever files are added,
removed, or modified.
"""

import argparse
import hashlib
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

# Directories that are never summarised or recursed into
SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".idea", "__pycache__",
             ".DS_Store", "dist", "out", ".venv", "venv", ".tox"}

# Files to read when inferring directory purpose (in priority order)
PRIORITY_FILES = ["README.md", "SKILL.md", "AGENT.md", "OVERVIEW.md", "index.md"]

# Files that are never listed in Key Files
SKIP_FILES = {"AGENTS.local.md", "OUTDATED.local.md", ".DS_Store"}

OUTPUT_FILENAME = "AGENTS.local.md"
MAX_FILES_TO_READ = 5


def source_hash(directory: Path) -> str:
    entries = []
    try:
        for item in sorted(directory.iterdir()):
            if item.name in SKIP_FILES or item.name.startswith("."):
                continue
            try:
                st = item.stat()
                entries.append(f"{item.name}:{int(st.st_mtime)}:{st.st_size}")
            except OSError:
                entries.append(item.name)
    except PermissionError:
        pass
    raw = "\n".join(entries).encode()
    return hashlib.sha1(raw).hexdigest()[:12]


def existing_hash(agents_local: Path) -> str | None:
    if not agents_local.exists():
        return None
    with agents_local.open() as f:
        for line in f:
            m = re.search(r"hash:\s*([a-f0-9]+)", line)
            if m:
                return m.group(1)
    return None


def read_first_meaningful_lines(path: Path, max_lines: int = 8) -> str:
    try:
        with path.open(errors="replace") as f:
            lines = []
            for line in f:
                stripped = line.strip()
                # Skip frontmatter delimiters and blank lines at start
                if stripped in ("---", "") and not lines:
                    continue
                if stripped.startswith("<!--"):
                    continue
                lines.append(stripped)
                if len(lines) >= max_lines:
                    break
            return " ".join(lines)
    except (OSError, UnicodeDecodeError):
        return ""


def infer_purpose(directory: Path) -> str:
    for name in PRIORITY_FILES:
        candidate = directory / name
        if candidate.exists():
            text = read_first_meaningful_lines(candidate, max_lines=6)
            if text:
                # Trim to ~200 chars, end on word boundary
                if len(text) > 200:
                    text = text[:200].rsplit(" ", 1)[0] + "..."
                return text
    # Fall back to directory name heuristics
    return f"Contains {_count_items(directory)} items."


def _count_items(directory: Path) -> int:
    try:
        return sum(1 for _ in directory.iterdir())
    except PermissionError:
        return 0


def key_files(directory: Path) -> list[tuple[str, str]]:
    items = []
    try:
        for item in sorted(directory.iterdir()):
            if item.name in SKIP_FILES or item.name.startswith("."):
                continue
            if item.is_dir():
                continue
            items.append(item)
    except PermissionError:
        return []

    result = []
    # Prioritise known doc/config files first
    priority = [f for f in items if f.name in PRIORITY_FILES]
    rest = [f for f in items if f.name not in PRIORITY_FILES]

    for f in priority + rest:
        if len(result) >= 12:
            break
        desc = _file_desc(f)
        result.append((f.name, desc))
    return result


def _file_desc(path: Path) -> str:
    suffix = path.suffix.lower()
    name = path.stem.lower()

    # Try to pull a one-liner from the file
    text = read_first_meaningful_lines(path, max_lines=3)
    if text and len(text) > 10:
        short = text[:120].rsplit(" ", 1)[0]
        return short

    # Fallback heuristics
    if suffix in (".md",):
        return "documentation"
    if suffix in (".py",):
        return "Python script"
    if suffix in (".sh", ".bash"):
        return "shell script"
    if suffix in (".yaml", ".yml"):
        return "configuration"
    if suffix in (".json",):
        return "JSON data"
    if suffix in (".kt", ".kts"):
        return "Kotlin source"
    return "file"


def subdirs(directory: Path) -> list[Path]:
    result = []
    try:
        for item in sorted(directory.iterdir()):
            if item.is_dir() and item.name not in SKIP_DIRS and not item.name.startswith("."):
                result.append(item)
    except PermissionError:
        pass
    return result


def subdir_desc(subdir: Path) -> str:
    # Use existing AGENTS.local.md purpose line if available
    agents_local = subdir / OUTPUT_FILENAME
    if agents_local.exists():
        with agents_local.open() as f:
            in_purpose = False
            for line in f:
                if line.strip() == "## Purpose":
                    in_purpose = True
                    continue
                if in_purpose and line.strip():
                    return line.strip()[:120]
    return infer_purpose(subdir)[:120]


def render(directory: Path, hash_val: str) -> str:
    now = datetime.now(tz=timezone.utc).strftime("%Y-%m-%d")
    rel = directory.resolve().name
    purpose = infer_purpose(directory)
    files = key_files(directory)
    dirs = subdirs(directory)

    lines = [
        f"<!-- AGENTS.local.md — generated by codebase-navigator, do not commit -->",
        f"<!-- generated: {now} | hash: {hash_val} -->",
        f"",
        f"# {rel}",
        f"",
        f"## Purpose",
        purpose,
        f"",
    ]

    if files:
        lines += ["## Key Files"]
        for name, desc in files:
            lines.append(f"- `{name}` — {desc}")
        lines.append("")

    if dirs:
        lines += ["## Subdirectories"]
        for d in dirs:
            nav_path = f"{d.name}/{OUTPUT_FILENAME}"
            desc = subdir_desc(d)
            lines.append(f"- [`{d.name}/`]({nav_path}) — {desc}")
        lines.append("")

    # Navigation hints: parent link
    parent = directory.resolve().parent
    parent_nav = f"../{OUTPUT_FILENAME}"
    lines += [
        "## Navigation",
        f"- Parent: [{parent.name}/]({parent_nav})",
    ]

    return "\n".join(lines) + "\n"


def generate(directory: Path, dry_run: bool = False, force: bool = False) -> str:
    """Generate AGENTS.local.md for directory. Returns 'written', 'skipped', or 'failed'."""
    if not directory.is_dir():
        print(f"  SKIP  {directory} (not a directory)", file=sys.stderr)
        return "failed"
    if directory.name in SKIP_DIRS:
        return "skipped"

    h = source_hash(directory)
    out_path = directory / OUTPUT_FILENAME

    if not force and existing_hash(out_path) == h:
        return "skipped"

    content = render(directory, h)

    if dry_run:
        print(f"\n{'='*60}\n{out_path}\n{'='*60}\n{content}")
        return "written"

    out_path.write_text(content)
    return "written"


def walk(root: Path, depth: int, dry_run: bool, force: bool) -> dict[str, int]:
    stats = {"written": 0, "skipped": 0, "failed": 0}

    def _walk(d: Path, current_depth: int):
        result = generate(d, dry_run=dry_run, force=force)
        stats[result] += 1
        if current_depth < depth:
            for sub in subdirs(d):
                _walk(sub, current_depth + 1)

    _walk(root, 0)
    return stats


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory", type=Path, nargs="?", default=Path("."))
    parser.add_argument("--depth", type=int, default=1,
                        help="Levels to recurse (default: 1 = current dir only)")
    parser.add_argument("--hash-only", action="store_true",
                        help="Print source-hash and exit")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print output without writing")
    parser.add_argument("--force", action="store_true",
                        help="Regenerate even if hash matches")
    args = parser.parse_args()

    directory = args.directory.resolve()

    if args.hash_only:
        print(source_hash(directory))
        return

    if args.depth == 1:
        result = generate(directory, dry_run=args.dry_run, force=args.force)
        verb = {"written": "wrote", "skipped": "current", "failed": "failed"}[result]
        print(f"  {verb:8s}  {directory}/{OUTPUT_FILENAME}")
    else:
        stats = walk(directory, depth=args.depth - 1, dry_run=args.dry_run, force=args.force)
        print(f"Done: {stats['written']} written, {stats['skipped']} current, {stats['failed']} failed")


if __name__ == "__main__":
    main()
