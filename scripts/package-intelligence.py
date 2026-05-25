#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import tarfile
import tempfile
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]

EXCLUDED_PATH_PARTS = {
    ".agent-turn",
    ".git",
    ".gradle",
    ".idea",
    ".mypy_cache",
    ".pytest_cache",
    ".ruff_cache",
    ".venv",
    ".venv-docs",
    "__pycache__",
    "build",
    "dist",
    "node_modules",
    "out",
    "site",
    "target",
}

EXCLUDED_FILE_NAMES = {
    ".DS_Store",
}

EXCLUDED_FILE_SUFFIXES = (
    ".class",
    ".log",
    ".pyc",
    ".pyo",
    ".rar",
    ".tar.gz",
    ".tgz",
    ".zip",
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Build source distribution archives for the Intelligence CLI.",
    )
    parser.add_argument("--out", default="dist", help="Output directory for archives.")
    parser.add_argument("--version", default=None, help="Version label for archive names.")
    parser.add_argument("--source-ref", default=None, help="Source ref recorded in PACKAGE-MANIFEST.txt.")
    args = parser.parse_args(argv)

    version = safe_label(args.version or discover_version())
    source_ref = args.source_ref or discover_source_ref()
    out_dir = Path(args.out).expanduser()
    if not out_dir.is_absolute():
        out_dir = REPO_ROOT / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    package_name = f"intelligence-{version}"
    tar_path = out_dir / f"{package_name}.tar.gz"
    zip_path = out_dir / f"{package_name}.zip"
    checksum_path = out_dir / "SHA256SUMS"

    for path in (tar_path, zip_path, checksum_path):
        if path.exists():
            path.unlink()

    with tempfile.TemporaryDirectory(prefix="intelligence-package-") as raw_tmp:
        package_root = Path(raw_tmp) / package_name
        copy_source_tree(package_root, out_dir)
        write_package_metadata(package_root, version, source_ref)
        create_tarball(package_root, tar_path)
        create_zip(package_root, zip_path)

    write_checksums(checksum_path, [tar_path, zip_path])
    for path in (tar_path, zip_path, checksum_path):
        print(path)
    return 0


def discover_version() -> str:
    result = run_git(["describe", "--tags", "--always", "--dirty"])
    if result:
        return result
    return "0.0.0"


def discover_source_ref() -> str:
    result = run_git(["rev-parse", "HEAD"])
    if result:
        return result
    return "unknown"


def run_git(args: list[str]) -> str | None:
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=REPO_ROOT,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
        )
    except OSError:
        return None
    if result.returncode != 0:
        return None
    return result.stdout.strip() or None


def safe_label(value: str) -> str:
    label = re.sub(r"[^A-Za-z0-9._-]+", "-", value.strip())
    label = label.strip("._-")
    if not label:
        raise SystemExit("version label must contain at least one filename-safe character")
    return label


def copy_source_tree(package_root: Path, out_dir: Path) -> None:
    for source in sorted(REPO_ROOT.rglob("*")):
        relative = source.relative_to(REPO_ROOT)
        if should_exclude(source, relative, out_dir):
            continue
        destination = package_root / relative
        if source.is_symlink():
            destination.parent.mkdir(parents=True, exist_ok=True)
            os.symlink(os.readlink(source), destination)
        elif source.is_dir():
            destination.mkdir(parents=True, exist_ok=True)
        elif source.is_file():
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)


def should_exclude(source: Path, relative: Path, out_dir: Path) -> bool:
    if is_relative_to(source.absolute(), out_dir.absolute()):
        return True
    if any(part in EXCLUDED_PATH_PARTS for part in relative.parts):
        return True
    if source.name in EXCLUDED_FILE_NAMES:
        return True
    return source.name.endswith(EXCLUDED_FILE_SUFFIXES)


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
    except ValueError:
        return False
    return True


def write_package_metadata(package_root: Path, version: str, source_ref: str) -> None:
    (package_root / "VERSION").write_text(f"{version}\n", encoding="utf-8")
    (package_root / "PACKAGE-MANIFEST.txt").write_text(
        "\n".join(
            [
                "name: intelligence",
                f"version: {version}",
                f"source-ref: {source_ref}",
                "entrypoint: bin/intelligence",
                "dependency-install: npm ci",
                "",
            ]
        ),
        encoding="utf-8",
    )


def create_tarball(package_root: Path, tar_path: Path) -> None:
    with tarfile.open(tar_path, "w:gz") as archive:
        archive.add(
            package_root,
            arcname=package_root.name,
            recursive=False,
            filter=normalized_tarinfo,
        )
        for path in sorted(package_root.rglob("*")):
            archive.add(
                path,
                arcname=str(Path(package_root.name) / path.relative_to(package_root)),
                recursive=False,
                filter=normalized_tarinfo,
            )


def normalized_tarinfo(tarinfo: tarfile.TarInfo) -> tarfile.TarInfo:
    tarinfo.uid = 0
    tarinfo.gid = 0
    tarinfo.uname = ""
    tarinfo.gname = ""
    return tarinfo


def create_zip(package_root: Path, zip_path: Path) -> None:
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        write_zip_entry(archive, package_root, package_root.parent, force_dir=True)
        for path in sorted(package_root.rglob("*")):
            write_zip_entry(archive, path, package_root.parent)


def write_zip_entry(archive: zipfile.ZipFile, path: Path, root: Path, force_dir: bool = False) -> None:
    relative = Path(path.name) if path == root else path.relative_to(root)
    arcname = relative.as_posix()
    stat = path.lstat()
    if path.is_dir() or force_dir:
        info = zipfile.ZipInfo(arcname.rstrip("/") + "/")
        info.external_attr = (stat.st_mode & 0xFFFF) << 16
        archive.writestr(info, "")
        return
    info = zipfile.ZipInfo(arcname)
    info.external_attr = (stat.st_mode & 0xFFFF) << 16
    if path.is_symlink():
        info.external_attr = (0o120777 & 0xFFFF) << 16
        archive.writestr(info, os.readlink(path))
        return
    with path.open("rb") as handle:
        archive.writestr(info, handle.read())


def write_checksums(checksum_path: Path, paths: list[Path]) -> None:
    rows = []
    for path in paths:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        rows.append(f"{digest}  {path.name}")
    checksum_path.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
