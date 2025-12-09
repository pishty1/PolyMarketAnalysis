import json
import shutil
import subprocess
import os
from config import matches_exclude_path, build_ripgrep_globs


def search_with_ripgrep(search_term, repo_path, exclude_items):
    """Use ripgrep to search while honoring exclude globs and keep counts per file."""
    rg_path = shutil.which("rg")
    if not rg_path:
        raise FileNotFoundError("ripgrep not installed")

    globs = build_ripgrep_globs(exclude_items, repo_path)
    cmd = [
        rg_path,
        "--json",
        "--no-heading",
        "--line-number",
        "--with-filename",
        "--fixed-strings",
    ]
    for glob_pattern in globs:
        cmd.extend(["--glob", f"!{glob_pattern}"])
    cmd.extend(["--", search_term, repo_path])

    process = subprocess.run(cmd, capture_output=True, text=True)
    if process.returncode not in (0, 1):
        raise subprocess.CalledProcessError(
            process.returncode, cmd, output=process.stdout, stderr=process.stderr
        )

    matches_by_file = {}
    for raw in process.stdout.splitlines():
        if not raw.strip():
            continue
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if payload.get("type") != "match":
            continue
        data = payload["data"]
        relative_path = data["path"]["text"]
        file_path = os.path.normpath(os.path.join(repo_path, relative_path))
        if matches_exclude_path(file_path, exclude_items, repo_path):
            continue
        line_num = data["line_number"]
        line_text = data["lines"]["text"].rstrip("\n")
        matches_by_file.setdefault(file_path, []).append((line_num, line_text))

    if not matches_by_file:
        return

    for file_path, matches in sorted(matches_by_file.items(), key=lambda item: len(item[1]), reverse=False):
        print(f"{file_path} ({len(matches)} matches)")
    print(f"Total files found: {len(matches_by_file)}")