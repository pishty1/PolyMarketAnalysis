import os
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
        "/Users/pishty/ws/flink/docs/content.zh"

        # Files
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
    }

def search_in_repository(search_term, repo_path, exclude_items):
    """
    Recursively searches for a term in a repository, excluding specified files and directories.

    Args:
        search_term (str): The term to search for.
        repo_path (str): The path to the root of the repository.
        exclude_items (set): A set of file and directory names to exclude.
    """
    for root, dirs, files in os.walk(repo_path, topdown=True):
        # Exclude directories by modifying the dirs list in-place
        dirs[:] = [d for d in dirs if d not in exclude_items]

        for file in files:
            if file in exclude_items:
                continue

            file_path = os.path.join(root, file)
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    for line_num, line in enumerate(f, 1):
                        if search_term in line:
                            print(f"Found '{search_term}' in {file_path} on line {line_num}:")
                            print(f"  {line.strip()}")
                            print("-" * 20)
            except Exception as e:
                print(f"Could not read file {file_path}: {e}")

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
        search_in_repository(args.search_term, args.repo_path, all_excludes)