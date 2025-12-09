import fnmatch
import json
import os
import shutil
import subprocess
import argparse

def get_java_boilerplate_excludes():
    """
    Returns a set of common files and directories to exclude in a Java project.
    """
    return {
        # Directories
        "target",
        ".mvn",
        ".idea",
        ".vscode",
        "build",
        "out",
        "/Users/pishty/ws/flink/docs/content.zh",

        # Files and globs
        ".gitignore",
        ".gitattributes",
        "pom.xml.tag",
        "pom.xml.releaseBackup",
        "pom.xml.versionsBackup",
        "pom.xml.next",
        "release.properties",
        "dependency-reduced-pom.xml",
        "buildNumber.properties",
        "*.class",
        "*.jar",
        "*.war",
        "*.ear",
        "*.log",
        "*.tmp",
        "*.swp",
        "*.zh",
        "*.scala"
    }


def matches_exclude(name, exclude_patterns):
    """Return True if the name matches any exclude glob pattern."""
    return any(fnmatch.fnmatch(name, pattern) for pattern in exclude_patterns)


def matches_exclude_path(file_path, exclude_patterns, repo_path):
    """Return True if the relative file path matches any of the exclude patterns."""
    try:
        relative = os.path.relpath(file_path, repo_path)
    except ValueError:
        relative = file_path
    normalized = relative.replace(os.sep, '/')
    components = normalized.split('/')
    if matches_exclude(os.path.basename(file_path), exclude_patterns):
        return True

    for pattern in exclude_patterns:
        if not pattern:
            continue
        normalized_pattern = pattern.replace(os.sep, '/')
        has_wildcard = any(ch in normalized_pattern for ch in "*?[]")
        if has_wildcard:
            if fnmatch.fnmatch(normalized, normalized_pattern):
                return True
            if any(fnmatch.fnmatch(comp, normalized_pattern) for comp in components):
                return True
        else:
            if normalized == normalized_pattern or normalized.startswith(f"{normalized_pattern}/"):
                return True
            if normalized_pattern in components:
                return True
    return False


def build_ripgrep_globs(exclude_items, repo_path):
    """Generate glob patterns for ripgrep that mirror the exclude list."""
    globs = []
    seen = set()
    repo_root = os.path.abspath(repo_path)
    for item in exclude_items:
        if not item:
            continue
        pattern = item
        if os.path.isabs(pattern):
            try:
                rel = os.path.relpath(pattern, repo_root)
            except ValueError:
                continue
            if rel.startswith(os.pardir):
                continue
            pattern = rel
        normalized = pattern.strip('/\\')
        if not normalized:
            continue
        normalized = normalized.replace(os.sep, '/')
        entries = {normalized, f"**/{normalized}"}
        if not any(ch in normalized for ch in "*?[]"):
            entries.update({
                f"{normalized}/**",
                f"**/{normalized}/**",
            })
        for variant in entries:
            if variant not in seen:
                globs.append(variant)
                seen.add(variant)
    return globs


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

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Search for a term in a repository, excluding boilerplate files and directories."
    )
    parser.add_argument("search_term", help="The term to search for.")
    parser.add_argument("repo_path", help="The path to the repository to search.")
    parser.add_argument(
        "--exclude",
        nargs='*',
        help="Additional files or directories to exclude.",
        default=[]
    )
    args = parser.parse_args()

    java_excludes = get_java_boilerplate_excludes()
    custom_excludes = set(args.exclude)
    all_excludes = java_excludes.union(custom_excludes)

    if not os.path.isdir(args.repo_path):
        print(f"Error: The specified repository path does not exist or is not a directory: {args.repo_path}")
    else:
        search_with_ripgrep(args.search_term, args.repo_path, all_excludes)