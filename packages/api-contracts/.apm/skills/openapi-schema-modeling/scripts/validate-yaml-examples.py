#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:
    sys.stderr.write(
        "FAIL PyYAML is required to parse OpenAPI YAML examples. "
        "Install it with `python3 -m pip install PyYAML` in your local tooling environment.\n"
    )
    sys.exit(1)


REFERENCE_DIR = Path(__file__).resolve().parent.parent / "references"


def main() -> int:
    files = sorted(REFERENCE_DIR.glob("*.openapi.yaml"))
    if not files:
        sys.stderr.write(f"FAIL no OpenAPI YAML examples found under {REFERENCE_DIR}\n")
        return 1

    failures: list[str] = []
    for path in files:
        try:
            with path.open("r", encoding="utf-8") as handle:
                document = yaml.safe_load(handle)
        except yaml.YAMLError as error:
            failures.append(f"{path}: YAML parse failed: {error}")
            continue
        scan_contract(document, [path.name], failures)

    if failures:
        for failure in failures:
            sys.stderr.write(f"FAIL {failure}\n")
        return 1

    for path in files:
        print(f"OK {path.relative_to(Path.cwd())}")
    return 0


def scan_contract(value: Any, path: list[str | int], failures: list[str]) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            key_text = str(key)
            child_path = [*path, key_text]
            if key_text == "nullable":
                failures.append(f"{format_path(child_path)}: nullable is forbidden")
            if key_text == "default" and child is None:
                failures.append(f"{format_path(child_path)}: default null is forbidden")
            if key_text == "type" and isinstance(child, list) and "null" in child:
                failures.append(f"{format_path(child_path)}: type unions cannot include null")
            scan_contract(child, child_path, failures)
        return

    if isinstance(value, list):
        for index, child in enumerate(value):
            scan_contract(child, [*path, index], failures)
        return

    if value is None:
        failures.append(f"{format_path(path)}: literal null is forbidden")


def format_path(parts: list[str | int]) -> str:
    rendered = "$"
    for part in parts:
        if isinstance(part, int):
            rendered += f"[{part}]"
        else:
            rendered += f".{part}"
    return rendered


if __name__ == "__main__":
    raise SystemExit(main())
