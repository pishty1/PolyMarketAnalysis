import fnmatch
import os

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